package com.theeclecticwitch.powertothepeople.debt

import com.theeclecticwitch.powertothepeople.data.CachedSource
import com.theeclecticwitch.powertothepeople.data.Http
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/*
 * The national debt and the deficit, straight from the U.S. Treasury's Fiscal Data service.
 * Free, no key, updated every business day (debt) and every month (deficit).
 */

private const val FISCAL = "https://api.fiscaldata.treasury.gov/services/api/fiscal_service"

object TreasurySources {
    const val DEBT_PAGE = "https://fiscaldata.treasury.gov/datasets/debt-to-the-penny/debt-to-the-penny"
    const val MTS_PAGE = "https://fiscaldata.treasury.gov/datasets/monthly-treasury-statement/"
    const val HISTORY_PAGE = "https://fiscaldata.treasury.gov/datasets/historical-debt-outstanding/"
}

@Serializable
private class FiscalResponse<T>(val data: List<T>)

@Serializable
private class DebtRow(
    @SerialName("record_date") val date: String,
    @SerialName("tot_pub_debt_out_amt") val total: String,
    @SerialName("debt_held_public_amt") val heldByPublic: String? = null,
    @SerialName("intragov_hold_amt") val intragovernmental: String? = null,
)

@Serializable
private class MtsRow(
    @SerialName("record_date") val date: String,
    @SerialName("classification_id") val id: String,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("classification_desc") val description: String,
    @SerialName("current_month_gross_rcpt_amt") val receipts: String? = null,
    @SerialName("current_month_gross_outly_amt") val outlays: String? = null,
    @SerialName("current_month_dfct_sur_amt") val deficit: String? = null,
    @SerialName("record_type_cd") val type: String,
    @SerialName("print_order_nbr") val order: String,
)

@Serializable
private class BreakdownRow(
    @SerialName("classification_id") val id: String,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("classification_desc") val description: String,
    @SerialName("current_month_rcpt_outly_amt") val amount: String? = null,
    @SerialName("record_type_cd") val type: String,
)

@Serializable
private class HistoryRow(
    @SerialName("record_date") val date: String,
    @SerialName("debt_outstanding_amt") val amount: String,
)

data class DebtSnapshot(
    val asOf: LocalDate,
    val total: Double,
    val heldByPublic: Double?,
    val intragovernmental: Double?,
    /** Average change per second over the past year. Negative if the debt fell. */
    val perSecond: Double,
    val basisStart: LocalDate,
    /** The moment the official figure is taken to apply from: the close of [asOf], Eastern time. */
    val anchor: Instant,
    val fetchedAt: Instant,
    val isStale: Boolean,
) {
    /** The estimate between official updates. Never runs backwards from before the anchor. */
    fun estimateAt(now: Instant): Double {
        val seconds = (now - anchor).inWholeMilliseconds.coerceAtLeast(0) / 1000.0
        return total + perSecond * seconds
    }
}

/** One month of a fiscal year. [year] is the calendar year: October to December fall in the year before. */
data class MonthResult(
    val label: String,
    val year: Int,
    val month: Int,
    val receipts: Double,
    val outlays: Double,
    val deficit: Double,
)

/** One line of a month's breakdown, in dollars. Can be negative (refunds, offsetting receipts). */
data class BudgetLine(val name: String, val amount: Double)

/** Where one month's money came from and where it went: the Treasury's Table 9. */
data class MonthBreakdown(val sources: List<BudgetLine>, val costs: List<BudgetLine>)

data class DeficitSnapshot(
    val fiscalYear: Int,
    /** The last day the monthly statement covers. */
    val through: LocalDate,
    /** Positive is a deficit; negative is a surplus - the Treasury's own sign. */
    val yearToDate: Double,
    val receiptsYearToDate: Double,
    val outlaysYearToDate: Double,
    val months: List<MonthResult>,
    /** The fiscal year before, month by month, for "the same month last year". */
    val priorMonths: List<MonthResult>,
    val priorYear: Int?,
    val priorYearTotal: Double?,
    val fetchedAt: Instant,
    val isStale: Boolean,
)

data class DebtYear(val fiscalYear: Int, val amount: Double)

object Treasury {
    private val eastern = TimeZone.of("America/New_York")

    private val debtSource = CachedSource("cache_debt_to_penny_year.json", 3.hours) {
        Http.getText(
            "$FISCAL/v2/accounting/od/debt_to_penny" +
                "?sort=-record_date&page%5Bsize%5D=270" +
                "&fields=record_date,tot_pub_debt_out_amt,debt_held_public_amt,intragov_hold_amt",
        )
    }

    private val mtsSource = CachedSource("cache_mts_table_1.json", 12.hours) {
        Http.getText("$FISCAL/v1/accounting/mts/mts_table_1?sort=-record_date&page%5Bsize%5D=40")
    }

    private val historySource = CachedSource("cache_debt_history.json", 24.hours) {
        Http.getText(
            "$FISCAL/v2/accounting/od/debt_outstanding?sort=-record_date&page%5Bsize%5D=26" +
                "&fields=record_date,debt_outstanding_amt",
        )
    }

    suspend fun debt(forceRefresh: Boolean = false): DebtSnapshot {
        val fetched = debtSource.get(forceRefresh)
        val rows = Http.json.decodeFromString<FiscalResponse<DebtRow>>(fetched.text).data
            .map { Triple(LocalDate.parse(it.date), it.total.toDouble(), it) }
            .sortedByDescending { it.first }
        val (latestDate, latestTotal, latestRow) = rows.first()
        // The rate is the average over a year. A month is too short: tax deadlines make the debt
        // dip for weeks at a time (it fell through September 2026), and a counter running
        // backwards through a seasonal dip would mislead more than it informs.
        val target = latestDate.minus(DatePeriod(days = 365))
        val basis = rows.firstOrNull { it.first <= target } ?: rows.last()
        val latestClose = LocalDateTime(latestDate, LocalTime(23, 59, 59)).toInstant(eastern)
        val basisClose = LocalDateTime(basis.first, LocalTime(23, 59, 59)).toInstant(eastern)
        val seconds = (latestClose - basisClose).inWholeSeconds.coerceAtLeast(1)
        return DebtSnapshot(
            asOf = latestDate,
            total = latestTotal,
            heldByPublic = latestRow.heldByPublic?.toDoubleOrNull(),
            intragovernmental = latestRow.intragovernmental?.toDoubleOrNull(),
            perSecond = (latestTotal - basis.second) / seconds,
            basisStart = basis.first,
            anchor = latestClose,
            fetchedAt = fetched.fetchedAt,
            isStale = fetched.isStale,
        )
    }

    suspend fun deficit(forceRefresh: Boolean = false): DeficitSnapshot {
        val fetched = mtsSource.get(forceRefresh)
        val all = Http.json.decodeFromString<FiscalResponse<MtsRow>>(fetched.text).data
        // One statement's rows: the current fiscal year and the one before it, side by side.
        val latest = all.maxOf { it.date }
        val rows = all.filter { it.date == latest }.sortedBy { it.order.toIntOrNull() ?: 0 }
        val years = rows.filter { it.type == "SL" && it.description.startsWith("FY ") }
            .mapNotNull { r -> r.description.removePrefix("FY ").trim().toIntOrNull()?.let { it to r.id } }
            .sortedBy { it.first }
        val (fy, fyId) = years.last()
        val prior = years.getOrNull(years.size - 2)
        fun ytdOf(parent: String) = rows.firstOrNull { it.parentId == parent && it.description == "Year-to-Date" }
        val ytd = ytdOf(fyId) ?: error("The monthly statement has no year-to-date line for FY $fy")
        fun monthsOf(year: Int, parent: String) = rows.filter { it.parentId == parent && it.type == "MTH" }.mapNotNull {
            val month = monthNumber(it.description) ?: return@mapNotNull null
            MonthResult(
                label = it.description,
                year = if (month >= 10) year - 1 else year,
                month = month,
                receipts = it.receipts?.toDoubleOrNull() ?: 0.0,
                outlays = it.outlays?.toDoubleOrNull() ?: 0.0,
                deficit = it.deficit?.toDoubleOrNull() ?: 0.0,
            )
        }
        val months = monthsOf(fy, fyId)
        return DeficitSnapshot(
            fiscalYear = fy,
            through = LocalDate.parse(latest),
            yearToDate = ytd.deficit?.toDoubleOrNull() ?: 0.0,
            receiptsYearToDate = ytd.receipts?.toDoubleOrNull() ?: 0.0,
            outlaysYearToDate = ytd.outlays?.toDoubleOrNull() ?: 0.0,
            months = months,
            priorMonths = prior?.let { monthsOf(it.first, it.second) }.orEmpty(),
            priorYear = prior?.first,
            priorYearTotal = prior?.let { ytdOf(it.second)?.deficit?.toDoubleOrNull() },
            fetchedAt = fetched.fetchedAt,
            isStale = fetched.isStale,
        )
    }

    /**
     * One month's receipts by source and outlays by function. Fetched only when a reader taps that
     * month, and kept a day; a closed month's figures don't change.
     */
    suspend fun breakdown(year: Int, month: Int, forceRefresh: Boolean = false): MonthBreakdown {
        val mm = month.toString().padStart(2, '0')
        val fetched = CachedSource("cache_mts_table_9_${year}_$mm.json", 24.hours) {
            Http.getText(
                "$FISCAL/v1/accounting/mts/mts_table_9?filter=record_calendar_year:eq:$year,record_calendar_month:eq:$mm" +
                    "&page%5Bsize%5D=80&fields=classification_id,parent_id,classification_desc," +
                    "current_month_rcpt_outly_amt,record_type_cd",
            )
        }.get(forceRefresh)
        val rows = Http.json.decodeFromString<FiscalResponse<BreakdownRow>>(fetched.text).data
        return breakdownOf(rows)
    }

    /** The debt at the end of each fiscal year (September 30), most recent 26 years. */
    suspend fun history(forceRefresh: Boolean = false): List<DebtYear> {
        val fetched = historySource.get(forceRefresh)
        return Http.json.decodeFromString<FiscalResponse<HistoryRow>>(fetched.text).data
            .map { DebtYear(LocalDate.parse(it.date).year, it.amount.toDouble()) }
            .sortedBy { it.fiscalYear }
    }

    fun now(): Instant = Clock.System.now()
}

private val monthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private fun monthNumber(name: String): Int? = monthNames.indexOf(name.trim()).takeIf { it >= 0 }?.plus(1)

/**
 * Table 9 in plain words. Receipts sit under "Receipts" (the three payroll-tax lines under their
 * own heading, added together here); outlays sit under "Net Outlays". Totals are left out.
 */
private fun breakdownOf(rows: List<BreakdownRow>): MonthBreakdown {
    // Top-level rows carry the word "null" as their parent, not an empty value.
    fun topLevel(r: BreakdownRow) = r.parentId == null || r.parentId == "null"
    val receiptsId = rows.firstOrNull { topLevel(it) && it.description == "Receipts" }?.id ?: return MonthBreakdown(emptyList(), emptyList())
    val outlaysId = rows.firstOrNull { topLevel(it) && it.description == "Net Outlays" }?.id ?: return MonthBreakdown(emptyList(), emptyList())
    fun amount(r: BreakdownRow) = r.amount?.toDoubleOrNull() ?: 0.0
    val sources = rows.filter { it.parentId == receiptsId && it.type == "RSG" }.mapNotNull { r ->
        val children = rows.filter { it.parentId == r.id }
        when {
            children.isNotEmpty() -> BudgetLine(plainName(r.description), children.sumOf(::amount))
            r.amount == null -> null
            else -> BudgetLine(plainName(r.description), amount(r))
        }
    }
    val costs = rows.filter { it.parentId == outlaysId && it.type == "F" }
        .map { BudgetLine(plainName(it.description), amount(it)) }
    return MonthBreakdown(sources.sortedByDescending { it.amount }, costs.sortedByDescending { it.amount })
}

/** The Treasury's names, with a hint where the official one doesn't say what it is. */
private fun plainName(official: String): String = when (official.trim().removeSuffix(":")) {
    "Individual Income Taxes" -> "Individual income taxes"
    "Corporation Income Taxes" -> "Corporate income taxes"
    "Social Insurance and Retirement Receipts" -> "Payroll taxes (Social Security, Medicare, unemployment)"
    "Excise Taxes" -> "Excise taxes (fuel, alcohol, tobacco and more)"
    "Estate and Gift Taxes" -> "Estate and gift taxes"
    "Customs Duties" -> "Customs duties (tariffs)"
    "Miscellaneous Receipts" -> "Other receipts (including Federal Reserve earnings)"
    "Social Security" -> "Social Security"
    "Medicare" -> "Medicare"
    "Net Interest" -> "Interest on the debt"
    "Income Security" -> "Income security (food aid, housing, unemployment, federal pensions)"
    "Health" -> "Health (Medicaid and other health programs)"
    "Undistributed Offsetting Receipts" -> "Offsetting receipts (payments back to the government)"
    // The rest, in sentence case: "Veterans Benefits and Services" -> "Veterans benefits and services".
    else -> official.trim().split(' ').mapIndexed { i, w -> if (i == 0) w else w.lowercase() }.joinToString(" ")
}

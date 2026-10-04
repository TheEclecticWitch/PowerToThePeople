package com.theeclecticwitch.powertothepeople.debt

import com.theeclecticwitch.powertothepeople.ui.fullWidth
import com.theeclecticwitch.powertothepeople.ui.PageColumn
import com.theeclecticwitch.powertothepeople.ui.CardPage
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theeclecticwitch.powertothepeople.ui.AppTopBar
import com.theeclecticwitch.powertothepeople.ui.ErrorBox
import com.theeclecticwitch.powertothepeople.ui.Format
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.LoadingBox
import com.theeclecticwitch.powertothepeople.ui.ReadingColumn
import com.theeclecticwitch.powertothepeople.ui.SourceLine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** Loads a Treasury figure, with a retry the reader can press. */
@Composable
private fun <T> rememberTreasury(vararg keys: Any?, load: suspend (Boolean) -> T): Triple<T?, String?, () -> Unit> {
    var value by remember(*keys) { mutableStateOf<T?>(null) }
    var error by remember(*keys) { mutableStateOf<String?>(null) }
    var attempt by remember(*keys) { mutableIntStateOf(0) }
    LaunchedEffect(attempt, *keys) {
        error = null
        try {
            value = load(attempt > 0)
        } catch (e: Exception) {
            error = "Couldn't reach the U.S. Treasury. Check your connection."
        }
    }
    return Triple(value, error, { attempt++ })
}

/** The debt, ticking. The digits are an estimate between the Treasury's daily figures, and say so. */
@Composable
fun LiveDebtFigure(snapshot: DebtSnapshot, large: Boolean) {
    var shown by remember(snapshot) { mutableDoubleStateOf(snapshot.estimateAt(Treasury.now())) }
    LaunchedEffect(snapshot) {
        while (true) {
            shown = snapshot.estimateAt(Treasury.now())
            delay(100)
        }
    }
    Text(
        Format.dollars(shown),
        style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        fontSize = if (large) 30.sp else 24.sp,
        maxLines = 1,
    )
}

/** The home screen's card: the counter, the year's deficit so far, and a tap for more. */
@Composable
fun DebtCard(onOpen: () -> Unit) {
    val (debt, debtError, retryDebt) = rememberTreasury { Treasury.debt(it) }
    val (deficit, _, _) = rememberTreasury { Treasury.deficit(it) }
    InfoCard(title = "U.S. National Debt", onClick = onOpen) {
        when {
            debt != null -> {
                LiveDebtFigure(debt, large = false)
                Text(
                    "Estimated live. Official total ${Format.dollarsShort(debt.total)} on ${Format.date(debt.asOf)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            debtError != null -> ErrorBox(debtError, retryDebt)
            else -> LoadingBox("Asking the Treasury…")
        }
        if (deficit != null) {
            val word = if (deficit.yearToDate >= 0) "Deficit" else "Surplus"
            Text(
                "$word so far in fiscal year ${deficit.fiscalYear}: ${Format.dollarsShort(abs(deficit.yearToDate))}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text("Tap for the full picture ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun DebtScreen(onBack: () -> Unit) {
    val (debt, debtError, retryDebt) = rememberTreasury { Treasury.debt(it) }
    val (deficit, deficitError, retryDeficit) = rememberTreasury { Treasury.deficit(it) }
    val (history, historyError, retryHistory) = rememberTreasury { Treasury.history(it) }
    Scaffold(topBar = { AppTopBar("Debt & Deficit", onBack) }) { padding ->
        PageColumn(Modifier.padding(padding)) {
            CardPage(spacing = 12.dp) {
                InfoCard(title = "Total national debt") {
                    when {
                        debt != null -> DebtDetail(debt)
                        debtError != null -> ErrorBox(debtError, retryDebt)
                        else -> LoadingBox()
                    }
                }
                InfoCard(title = "This year's budget") {
                    when {
                        deficit != null -> DeficitDetail(deficit)
                        deficitError != null -> ErrorBox(deficitError, retryDeficit)
                        else -> LoadingBox()
                    }
                }
                InfoCard(title = "Debt at the end of each fiscal year") {
                    when {
                        history != null -> HistoryDetail(history)
                        historyError != null -> ErrorBox(historyError, retryHistory)
                        else -> LoadingBox()
                    }
                }
                InfoCard(title = "What these words mean") {
                    Definition("Deficit", "When the government spends more than it collects in a year. It borrows the difference, which adds to the debt.")
                    Definition("Surplus", "When the government collects more than it spends in a year.")
                    Definition("Debt", "The total the government owes: every past year's borrowing that has not been repaid.")
                    Definition("Fiscal year", "The government's budget year. It runs from October 1 to September 30, and is named for the year it ends in.")
                }
            }
        }
    }
}

@Composable
private fun DebtDetail(d: DebtSnapshot) {
    LiveDebtFigure(d, large = true)
    val perDay = d.perSecond * 86_400
    val direction = if (perDay >= 0) "grown" else "shrunk"
    Text(
        "The counter is an estimate: it starts from the Treasury's latest official total and adds the average " +
            "daily change since ${Format.date(d.basisStart)}. Over that time the debt has $direction by about " +
            "${Format.dollarsShort(abs(perDay))} a day.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Stat("Official total, ${Format.date(d.asOf)}", Format.dollars(d.total))
    d.heldByPublic?.let { Stat("Held by the public", Format.dollarsShort(it)) }
    d.intragovernmental?.let { Stat("Owed to other government accounts (like Social Security)", Format.dollarsShort(it)) }
    SourceLine("U.S. Treasury, Debt to the Penny", TreasurySources.DEBT_PAGE, staleNote(d.isStale, d.fetchedAt.toString()))
}

@Composable
private fun DeficitDetail(d: DeficitSnapshot) {
    val word = if (d.yearToDate >= 0) "Deficit" else "Surplus"
    Text(
        "$word for fiscal year ${d.fiscalYear} so far (October 1 to ${Format.date(d.through)})",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        Format.dollarsShort(abs(d.yearToDate)),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Stat("Collected", Format.dollarsShort(d.receiptsYearToDate))
    Stat("Spent", Format.dollarsShort(d.outlaysYearToDate))
    if (d.priorYear != null && d.priorYearTotal != null) {
        val w = if (d.priorYearTotal >= 0) "deficit" else "surplus"
        Stat("Fiscal year ${d.priorYear}, full year $w", Format.dollarsShort(abs(d.priorYearTotal)))
    }
    Spacer(Modifier.height(4.dp))
    Text("Month by month", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    var selected by remember(d) { mutableStateOf<Int?>(null) }
    Text(
        if (selected == null) "Tap a month to see its details." else "Tap the month again to close.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    MonthBars(d.months, selected) { selected = if (it == selected) null else it }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Legend(MaterialTheme.colorScheme.primary, "Deficit (spent more)")
        Legend(MaterialTheme.colorScheme.secondary, "Surplus (collected more)")
    }
    selected?.let { d.months.getOrNull(it) }?.let { m ->
        MonthDetail(m, d.priorMonths.firstOrNull { it.month == m.month })
    }
    SourceLine("U.S. Treasury, Monthly Treasury Statement", TreasurySources.MTS_PAGE, staleNote(d.isStale, null))
}

@Composable
private fun HistoryDetail(years: List<DebtYear>) {
    if (years.isEmpty()) return
    val first = years.first()
    val last = years.last()
    Text(
        "From ${Format.dollarsShort(first.amount)} in ${first.fiscalYear} to ${Format.dollarsShort(last.amount)} in ${last.fiscalYear}.",
        style = MaterialTheme.typography.bodyMedium,
    )
    YearBars(years)
    SourceLine("U.S. Treasury, Historical Debt Outstanding", TreasurySources.HISTORY_PAGE)
}

private fun staleNote(isStale: Boolean, @Suppress("UNUSED_PARAMETER") at: String?): String? =
    if (isStale) "offline - showing the last copy saved" else null

@Composable
private fun Stat(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

@Composable
private fun Definition(word: String, meaning: String) {
    Column {
        Text(word, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(meaning, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp)) { Canvas(Modifier.fillMaxSize()) { drawRect(color) } }
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Deficits above the line, surpluses below it, one bar per month of the fiscal year. Tapping
 * anywhere in a month's column (bar or name) picks it; the other months fade.
 */
@Composable
private fun MonthBars(months: List<MonthResult>, selected: Int?, onTap: (Int) -> Unit) {
    if (months.isEmpty()) return
    val deficitColor = MaterialTheme.colorScheme.primary
    val surplusColor = MaterialTheme.colorScheme.secondary
    val axis = MaterialTheme.colorScheme.outline
    val biggest = months.maxOf { abs(it.deficit) }.coerceAtLeast(1.0)
    val tap by rememberUpdatedState(onTap)
    Column(
        Modifier.pointerInput(months) {
            detectTapGestures { at ->
                tap((at.x / (size.width.toFloat() / months.size)).toInt().coerceIn(months.indices))
            }
        },
    ) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val slot = size.width / months.size
            val barWidth = slot * 0.62f
            val hasSurplus = months.any { it.deficit < 0 }
            val zeroY = if (hasSurplus) size.height * 0.7f else size.height
            val upSpace = zeroY
            val downSpace = size.height - zeroY
            months.forEachIndexed { i, m ->
                val x = i * slot + (slot - barWidth) / 2
                val alpha = if (selected == null || selected == i) 1f else 0.3f
                if (m.deficit >= 0) {
                    val h = (m.deficit / biggest * upSpace).toFloat()
                    drawRect(deficitColor.copy(alpha = alpha), Offset(x, zeroY - h), Size(barWidth, h))
                } else {
                    val h = (abs(m.deficit) / biggest * max(downSpace, upSpace * 0.3f)).toFloat().coerceAtMost(downSpace)
                    drawRect(surplusColor.copy(alpha = alpha), Offset(x, zeroY), Size(barWidth, h))
                }
            }
            drawLine(axis, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.5f)
        }
        Row(Modifier.fillMaxWidth()) {
            months.forEachIndexed { i, it ->
                Text(
                    it.label.take(3),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (i == selected) FontWeight.Bold else null,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One month up close: its totals, the same month a year before, and where the money came from and went. */
@Composable
private fun MonthDetail(m: MonthResult, lastYear: MonthResult?) {
    val (b, error, retry) = rememberTreasury(m.year, m.month) { Treasury.breakdown(m.year, m.month, it) }
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HorizontalDivider()
        Text("${m.label} ${m.year}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            if (m.deficit >= 0) "A deficit of ${Format.dollarsShort(m.deficit)}: the government spent more than it collected."
            else "A surplus of ${Format.dollarsShort(-m.deficit)}: the government collected more than it spent.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Stat("Collected", Format.dollarsShort(m.receipts))
        Stat("Spent", Format.dollarsShort(m.outlays))
        if (lastYear != null) {
            val w = if (lastYear.deficit >= 0) "deficit" else "surplus"
            Stat("${lastYear.label} ${lastYear.year}, a year before", "${Format.dollarsShort(abs(lastYear.deficit))} $w")
        }
        when {
            b != null -> {
                BreakdownList("Where the money came from", b.sources, show = b.sources.size)
                BreakdownList("Where it went", b.costs, show = 6)
                if ((b.sources + b.costs).any { it.amount < 0 }) {
                    Text(
                        "Amounts are net: refunds and money paid back are already taken out, so a line can be below zero.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            error != null -> ErrorBox(error, retry)
            else -> LoadingBox("Asking the Treasury…")
        }
    }
}

/** The biggest lines, each with its share of the whole; the rest added together as "Everything else". */
@Composable
private fun BreakdownList(title: String, lines: List<BudgetLine>, show: Int) {
    val total = lines.sumOf { it.amount }
    if (lines.isEmpty() || total <= 0) return
    val rest = lines.drop(show)
    fun share(amount: Double) = "${(amount / total * 100).roundToInt()}%"
    Spacer(Modifier.height(4.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    lines.take(show).forEach { Stat(it.name, "${Format.dollarsShort(it.amount)} · ${share(it.amount)}") }
    if (rest.isNotEmpty()) {
        val other = rest.sumOf { it.amount }
        Stat("Everything else", "${Format.dollarsShort(other)} · ${share(other)}")
    }
}

@Composable
private fun YearBars(years: List<DebtYear>) {
    val color = MaterialTheme.colorScheme.primary
    val biggest = years.maxOf { it.amount }.coerceAtLeast(1.0)
    Column {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val slot = size.width / years.size
            val barWidth = slot * 0.7f
            years.forEachIndexed { i, y ->
                val h = (y.amount / biggest * size.height).toFloat()
                drawRect(color, Offset(i * slot + (slot - barWidth) / 2, size.height - h), Size(barWidth, h))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(years.first().fiscalYear.toString(), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text(years.last().fiscalYear.toString(), style = MaterialTheme.typography.labelSmall)
        }
    }
}

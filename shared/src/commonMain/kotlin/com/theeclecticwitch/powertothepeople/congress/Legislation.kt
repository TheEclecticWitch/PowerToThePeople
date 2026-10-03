package com.theeclecticwitch.powertothepeople.congress

import com.theeclecticwitch.powertothepeople.data.CachedSource
import com.theeclecticwitch.powertothepeople.data.Http
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlin.time.Duration.Companion.days
import kotlinx.serialization.Serializable

// --- Federal bills: searched on the device, over the bill list the app already keeps ---

private val billNumber = Regex("^(hr|s|hres|sres|hjres|sjres|hconres|sconres)(\\d{1,5})$")

/**
 * "HR 1", "H.R.1", "s.j.res. 12" -> "119/hr/1"; anything else is not a bill number. People type bill numbers
 * every way the news prints them, so spaces and dots are ignored.
 */
fun billKeyFromQuery(query: String, congress: Int = CongressData.CONGRESS): String? {
    val compact = query.lowercase().filter { it.isLetterOrDigit() }
    val m = billNumber.matchEntire(compact) ?: return null
    return "$congress/${m.groupValues[1]}/${m.groupValues[2].trimStart('0').ifEmpty { "0" }}"
}

enum class BillFilter(val label: String) { All("All"), Law("Became law"), House("House"), Senate("Senate") }

fun BillSummary.becameLaw(): Boolean =
    latestAction?.text?.let { it.contains("Became Public Law", true) || it.contains("Became Private Law", true) } == true

/**
 * Bills matching a number or the words of their title, newest activity first. Every word must appear, so
 * "child tax credit" finds bills about that and not every bill mentioning "child".
 */
fun searchBills(index: Map<String, BillSummary>, query: String, filter: BillFilter = BillFilter.All): List<Pair<String, BillSummary>> {
    val keep: (Pair<String, BillSummary>) -> Boolean = { (key, b) ->
        when (filter) {
            BillFilter.All -> true
            BillFilter.Law -> b.becameLaw()
            BillFilter.House -> key.split('/').getOrNull(1)?.startsWith("h") == true
            BillFilter.Senate -> key.split('/').getOrNull(1)?.startsWith("s") == true
        }
    }
    val q = query.trim()
    billKeyFromQuery(q)?.let { key -> return listOfNotNull(index[key]?.let { key to it }).filter(keep) }
    val words = q.lowercase().split(Regex("\\s+")).filter { it.length >= 2 }
    return index.entries.asSequence()
        .map { it.key to it.value }
        .filter(keep)
        .filter { (_, b) ->
            val title = b.title?.lowercase() ?: return@filter words.isEmpty()
            words.all { it in title }
        }
        .sortedWith(
            compareByDescending<Pair<String, BillSummary>> { (_, b) -> q.length > 2 && b.title?.contains(q, true) == true }
                .thenByDescending { (_, b) -> b.latestAction?.date ?: b.introduced ?: "" },
        )
        .toList()
}

// --- Executive orders, from the Federal Register via the gatherer ---

@Serializable
data class ExecutiveOrder(
    val number: String? = null,
    val title: String? = null,
    val signed: String? = null,
    val published: String? = null,
    val president: String? = null,
    val notes: String? = null,
    val url: String? = null,
    val pdf: String? = null,
)

@Serializable
private class OrderList(val orders: List<ExecutiveOrder> = emptyList())

object ExecutiveOrders {
    const val SOURCE_NAME = "Federal Register"
    const val SOURCE_URL = "https://www.federalregister.gov/presidential-documents/executive-orders"

    private val source = CachedSource("pd_orders.json", 1.days) { Http.getText("${CongressData.BASE}/orders/index.json") }
    private var orders: List<ExecutiveOrder>? = null

    suspend fun all(force: Boolean = false): List<ExecutiveOrder> {
        orders?.takeIf { !force }?.let { return it }
        return Http.json.decodeFromString<OrderList>(source.get(force).text).orders.also { orders = it }
    }

    /** "14434", "EO 14434" or "Executive Order 14434" finds that order; otherwise every word must be in the title. */
    fun search(all: List<ExecutiveOrder>, query: String, president: String?): List<ExecutiveOrder> {
        val q = query.trim()
        val inTerm = all.filter { president == null || it.president == president }
        val number = Regex("^(?:executive\\s*order|eo)?\\s*(?:no\\.?)?\\s*(\\d{4,5})$", RegexOption.IGNORE_CASE).matchEntire(q)
        if (number != null) return inTerm.filter { it.number == number.groupValues[1] }
        val words = q.lowercase().split(Regex("\\s+")).filter { it.length >= 2 }
        return inTerm.filter { o -> words.all { o.title?.contains(it, true) == true } }
    }
}

// --- State bills, searched live through this project's Cloudflare service ---

@Serializable
data class StateBill(
    val id: String,
    val number: String,
    val title: String? = null,
    val session: String? = null,
    val chamber: String? = null,
    val kind: String? = null,
    val subjects: List<String> = emptyList(),
    val introduced: String? = null,
    val latestAction: String? = null,
    val latestActionDate: String? = null,
    val url: String? = null,
)

@Serializable
data class StateBillResults(
    val state: String,
    val query: String = "",
    val page: Int = 1,
    val pages: Int = 1,
    val total: Int = 0,
    val bills: List<StateBill> = emptyList(),
)

@Serializable
private class ServiceError(val error: String = "")

/** A search the service turned down, with its own plain-English reason ("isn't set up yet", "limit for now"). */
class StateSearchUnavailable(message: String) : Exception(message)

object StateBills {
    const val SOURCE_NAME = "Open States"
    const val SOURCE_URL = "https://openstates.org/"

    suspend fun search(state: String, query: String, page: Int = 1): StateBillResults {
        val url = "${AppTally.BASE}/state-bills?state=${state.lowercase()}&q=${encode(query)}&page=$page"
        return try {
            Http.json.decodeFromString(Http.getText(url))
        } catch (e: ResponseException) {
            val reason = try {
                Http.json.decodeFromString<ServiceError>(e.response.bodyAsText()).error
            } catch (x: Exception) {
                ""
            }
            throw StateSearchUnavailable(reason.ifBlank { "State bill search isn't available right now." }.replaceFirstChar { it.uppercase() })
        }
    }

    private fun encode(text: String): String = buildString {
        text.encodeToByteArray().forEach { b ->
            val c = b.toInt().toChar()
            if (c.isLetterOrDigit() && b >= 0) append(c) else append('%').append(((b.toInt() and 0xff) + 0x100).toString(16).substring(1).uppercase())
        }
    }
}

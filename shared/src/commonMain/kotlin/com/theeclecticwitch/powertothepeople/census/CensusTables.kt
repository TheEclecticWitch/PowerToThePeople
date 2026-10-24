package com.theeclecticwitch.powertothepeople.census

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.location.LocationStore
import com.theeclecticwitch.powertothepeople.ui.AppTopBar
import com.theeclecticwitch.powertothepeople.ui.CardPage
import com.theeclecticwitch.powertothepeople.ui.CenteredMessage
import com.theeclecticwitch.powertothepeople.ui.Format
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.LoadingBox
import com.theeclecticwitch.powertothepeople.ui.PageColumn
import com.theeclecticwitch.powertothepeople.ui.SourceLine
import kotlinx.serialization.Serializable

/*
 * The other Census tables: House seats from the 2020 census, who votes (the November voting
 * survey), and what state and local governments take in and spend.
 */

@Serializable
data class SeatsRow(
    val state: String,
    val name: String,
    val population: Long? = null,
    val seats: Int,
    val change: Int = 0,
    val history: Map<String, Int> = emptyMap(),
)

@Serializable
data class Apportionment(val source: TableSource, val asOf: String? = null, val totalSeats: Int = 435, val states: List<SeatsRow> = emptyList())

@Serializable
data class Turnout(
    val state: String? = null,
    val citizens18Thousands: Double? = null,
    val registeredPct: Double? = null,
    val votedPct: Double? = null,
)

@Serializable
data class AgeTurnout(val ages: String, val registeredPct: Double? = null, val votedPct: Double? = null)

@Serializable
data class Election(
    val year: Int,
    val kind: String? = null,
    val url: String? = null,
    val us: Turnout? = null,
    val byAge: List<AgeTurnout> = emptyList(),
    val states: List<Turnout> = emptyList(),
)

@Serializable
data class VotingSurvey(val source: TableSource, val elections: List<Election> = emptyList(), val notes: List<String> = emptyList())

@Serializable
data class GovMoney(
    val state: String? = null,
    val revenue: Double? = null,
    val taxes: Double? = null,
    val fromFederal: Double? = null,
    val spending: Double? = null,
    val byFunction: Map<String, Double?> = emptyMap(),
    val debt: Double? = null,
)

@Serializable
data class GovFinances(
    val source: TableSource,
    val asOf: String? = null,
    val unit: String? = null,
    val notes: List<String> = emptyList(),
    val us: GovMoney? = null,
    val states: List<GovMoney> = emptyList(),
)

/** Every state's seats in the House, from the 2020 census, and how they've changed since 1910. */
@Composable
fun SeatsScreen(onBack: () -> Unit) {
    val location by LocationStore.location.collectAsState()
    val data by produceState<Apportionment?>(null) { value = CensusData.table("apportionment-2020.json") ?: Apportionment(TableSource("", "")) }
    var open by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { AppTopBar("Seats in the House", onBack) }) { padding ->
        PageColumn(Modifier.padding(padding)) {
            val d = data
            when {
                d == null -> LoadingBox()
                d.states.isEmpty() -> CenteredMessage("The seat counts couldn't be loaded. Check your connection and try again.")
                else -> CardPage(spacing = 12.dp) {
                    InfoCard(title = "How seats are shared out") {
                        Text(
                            "The House has ${d.totalSeats} voting seats. After each census, every ten years, they are shared " +
                                "among the states by population, and each state gets at least one. A state's electoral votes " +
                                "for President are its House seats plus its two senators.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        SourceLine(d.source.name, d.source.link)
                    }
                    val mine = d.states.firstOrNull { it.state == location?.stateAbbr }
                    mine?.let { SeatsCard(it, expanded = true) }
                    InfoCard(title = "Every state") {
                        Note("Tap a state to see its seats in every census since 1910.")
                        d.states.sortedWith(compareByDescending<SeatsRow> { it.seats }.thenBy { it.name }).forEachIndexed { i, s ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Column(Modifier.clickable { open = if (open == s.state) null else s.state }) {
                                Stat(s.name, "${s.seats} ${if (s.seats == 1) "seat" else "seats"}${changeText(s.change)}")
                                if (open == s.state) SeatsHistory(s)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun changeText(change: Int): String = when {
    change > 0 -> " (+$change)"
    change < 0 -> " ($change)"
    else -> ""
}

@Composable
private fun SeatsCard(s: SeatsRow, expanded: Boolean) {
    InfoCard(title = s.name) {
        Text("${s.seats} ${if (s.seats == 1) "seat" else "seats"} in the House, ${s.seats + 2} electoral votes", style = MaterialTheme.typography.titleMedium)
        Text(
            when {
                s.change > 0 -> "Gained ${s.change} after the 2020 census."
                s.change < 0 -> "Lost ${-s.change} after the 2020 census."
                else -> "No change after the 2020 census."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        s.population?.let { Stat("Population counted for seats in 2020", Format.commas(it)) }
        if (expanded) SeatsHistory(s)
    }
}

@Composable
private fun SeatsHistory(s: SeatsRow) {
    if (s.history.isEmpty()) return
    Column(Modifier.padding(start = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        s.history.entries.sortedByDescending { it.key }.forEach { (year, seats) -> Stat(year, "$seats") }
    }
}

/** For the voting screen: how many citizens in the reader's state registered and voted, from the Census survey. */
@Composable
fun WhoVotesCard(stateAbbr: String?, stateName: String?) {
    val data by produceState<VotingSurvey?>(null) { value = CensusData.table("voting.json") }
    val d = data ?: return
    if (d.elections.isEmpty()) return
    var showAges by remember { mutableStateOf(false) }
    InfoCard(title = "Who votes") {
        Text("Share of adult citizens who registered and who voted", style = MaterialTheme.typography.bodyMedium)
        d.elections.sortedByDescending { it.year }.forEach { e ->
            Text("${e.year}${e.kind?.let { " ($it)" } ?: ""}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            val mine = e.states.firstOrNull { it.state == stateAbbr }
            if (mine != null && stateName != null) {
                Stat(stateName, "${percent(mine.registeredPct)} registered · ${percent(mine.votedPct)} voted")
            }
            e.us?.let { Stat("United States", "${percent(it.registeredPct)} registered · ${percent(it.votedPct)} voted") }
        }
        val latest = d.elections.maxByOrNull { it.year }
        if (latest != null && latest.byAge.isNotEmpty()) {
            TextButton(onClick = { showAges = !showAges }) { Text(if (showAges) "Hide ages" else "By age, ${latest.year}") }
            if (showAges) {
                latest.byAge.forEach { Stat(it.ages, "${percent(it.registeredPct)} · ${percent(it.votedPct)} voted") }
            }
        }
        Note("From the Census Bureau's survey after each November election. People report whether they voted, so these can differ from official vote counts.")
        SourceLine(d.source.name, latest?.url ?: d.source.url)
    }
}

/** For the debt screen: what the reader's state and its local governments take in, spend and owe. */
@Composable
fun StateMoneyCard(stateAbbr: String, stateName: String) {
    val data by produceState<GovFinances?>(null) { value = CensusData.table("finances.json") }
    val d = data ?: return
    val m = d.states.firstOrNull { it.state == stateAbbr } ?: return
    // Published in thousands of dollars.
    fun money(v: Double?): String = v?.let { Format.dollarsShort(it * 1000) } ?: "not available"
    val year = d.asOf?.removePrefix("FY")
    InfoCard(title = "$stateName's state and local governments") {
        Text(
            "The state government and every county, city, town, school district and special district in it, together" +
                (year?.let { ", in the fiscal year that ended in $it" } ?: "") + ".",
            style = MaterialTheme.typography.bodyMedium,
        )
        Stat("Money taken in", money(m.revenue))
        Stat("From taxes", money(m.taxes))
        Stat("From the federal government", money(m.fromFederal))
        Stat("Money spent", money(m.spending))
        Stat("Debt owed at year's end", money(m.debt))
        if (m.byFunction.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Where some of the money went", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            val f = m.byFunction
            Stat("Education", money(f["education"]))
            f["elementarySecondary"]?.let { Stat("    Schools, kindergarten to 12th grade", money(it)) }
            f["higherEducation"]?.let { Stat("    Colleges and universities", money(it)) }
            f.entries.filter { it.key !in educationKeys && it.value != null }.sortedByDescending { it.value }.forEach { (key, v) ->
                Stat(functionName(key), money(v))
            }
        }
        Note(
            "Money spent includes government-run utilities and pension and unemployment payments. The spending by area " +
                "is the governments' own direct spending. Fiscal ${year ?: ""} is the newest year the Census Bureau publishes " +
                "as a state-by-state table.",
        )
        SourceLine(d.source.name, d.source.link)
    }
}

private val educationKeys = setOf("education", "elementarySecondary", "higherEducation")

private fun functionName(key: String): String = when (key) {
    "publicWelfare" -> "Public welfare (including Medicaid)"
    "hospitals" -> "Hospitals"
    "health" -> "Public health"
    "highways" -> "Highways and roads"
    "police" -> "Police"
    "corrections" -> "Prisons and jails"
    "interestOnGeneralDebt" -> "Interest on debt"
    else -> key.replace(Regex("([a-z])([A-Z])"), "$1 $2").replaceFirstChar { it.uppercase() }
}

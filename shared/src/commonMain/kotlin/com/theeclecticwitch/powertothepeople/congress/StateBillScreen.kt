package com.theeclecticwitch.powertothepeople.congress

import com.theeclecticwitch.powertothepeople.ui.fullWidth
import com.theeclecticwitch.powertothepeople.ui.PageColumn
import com.theeclecticwitch.powertothepeople.ui.CardPage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.data.Http
import com.theeclecticwitch.powertothepeople.location.LocationStore
import com.theeclecticwitch.powertothepeople.officials.Official
import com.theeclecticwitch.powertothepeople.officials.StateNames
import com.theeclecticwitch.powertothepeople.officials.rememberStateDelegation
import com.theeclecticwitch.powertothepeople.ui.AppTopBar
import com.theeclecticwitch.powertothepeople.ui.ErrorBox
import com.theeclecticwitch.powertothepeople.ui.Format
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.LoadingBox
import com.theeclecticwitch.powertothepeople.ui.ReadingColumn
import com.theeclecticwitch.powertothepeople.ui.SourceLine
import com.theeclecticwitch.powertothepeople.ui.Tag
import io.ktor.client.plugins.ResponseException
import kotlinx.serialization.Serializable

@Serializable
data class StateAction(val date: String? = null, val text: String? = null, val chamber: String? = null)

@Serializable
data class StateSponsor(val name: String, val primary: Boolean = false, val personId: String? = null, val party: String? = null)

@Serializable
data class StateVoter(val name: String, val personId: String? = null, val option: String)

@Serializable
data class StateVote(
    val date: String? = null,
    val motion: String? = null,
    val result: String? = null,
    val chamber: String? = null,
    val counts: Map<String, Int> = emptyMap(),
    val voters: List<StateVoter> = emptyList(),
)

@Serializable
data class StateBillDetail(
    val id: String,
    val state: String? = null,
    val number: String,
    val title: String? = null,
    val session: String? = null,
    val chamber: String? = null,
    val subjects: List<String> = emptyList(),
    val actions: List<StateAction> = emptyList(),
    val sponsors: List<StateSponsor> = emptyList(),
    val votes: List<StateVote> = emptyList(),
    val url: String? = null,
    val sources: List<String> = emptyList(),
)

object StateBillDetails {
    /** One bill with its record, through this project's Cloudflare service (which holds the Open States key). */
    suspend fun load(id: String): StateBillDetail = try {
        Http.json.decodeFromString(Http.getText("${AppTally.BASE}/state-bill?id=$id"))
    } catch (e: ResponseException) {
        throw StateSearchUnavailable("This state bill isn't available right now.")
    }

    /** What each state calls its two chambers; most say Senate and House. */
    fun chamberName(state: String?, chamber: String?): String = when (chamber) {
        "upper" -> "Senate"
        "lower" -> when (state) {
            "md", "va", "wv" -> "House of Delegates"
            "ca", "nv", "ny", "wi" -> "Assembly"
            "nj" -> "General Assembly"
            else -> "House"
        }
        "legislature" -> "Legislature"
        else -> "Legislature"
    }

    /** The reader's own legislators' votes on a roll call, matched by their Open States person ids. */
    fun mine(vote: StateVote, legislators: List<Official>): List<Pair<Official, String>> =
        legislators.mapNotNull { o ->
            val person = o.id.substringAfter("ocd-person/", "").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            vote.voters.firstOrNull { it.personId?.endsWith(person) == true }?.let { o to it.option }
        }
}

private fun optionLabel(option: String): String = when (option) {
    "yes" -> "Yes"
    "no" -> "No"
    "not voting" -> "Not voting"
    else -> option.replaceFirstChar { it.uppercase() }
}

@Composable
fun StateBillScreen(id: String, onBack: () -> Unit) {
    val (load, retry) = rememberLoad(id, "Couldn't load this bill. Check your connection.") { StateBillDetails.load(id) }
    val location by LocationStore.location.collectAsState()
    val (delegation, _, _) = rememberStateDelegation(location)
    val title = (load as? Load.Done)?.value?.let { "${it.state?.uppercase().orEmpty()} ${it.number}".trim() } ?: "State bill"
    Scaffold(topBar = { AppTopBar(title, onBack) }) { padding ->
        PageColumn(Modifier.padding(padding)) {
            when (val l = load) {
                Load.Loading -> LoadingBox("Loading the bill…")
                is Load.Failed -> ErrorBox(l.message, retry)
                is Load.Done -> {
                    val b = l.value
                    // Only the reader's own legislators, and only for a bill in their own state.
                    val mineToo = if (location?.stateAbbr.equals(b.state, true)) {
                        delegation?.let { it.senators + it.representatives }.orEmpty()
                    } else emptyList()
                    CardPage(spacing = 12.dp) {
                        Text(
                            listOfNotNull(
                                b.state?.let { StateNames.of(it.uppercase()) },
                                b.session?.let { "$it session" },
                                b.chamber?.let { "from the ${StateBillDetails.chamberName(b.state, it)}" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fullWidth(),
                        )
                        Text(b.title ?: b.number, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fullWidth())
                        if (b.subjects.isNotEmpty()) {
                            Row(Modifier.fullWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                b.subjects.take(6).forEach { Tag(it) }
                            }
                        }
                        b.actions.lastOrNull()?.let { a ->
                            InfoCard(title = "Latest action") {
                                a.date?.let { Text(Format.date(it), style = MaterialTheme.typography.titleSmall) }
                                Text(a.text.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        if (b.votes.isEmpty()) {
                            InfoCard(title = "Recorded votes") {
                                Text("No recorded votes on this bill yet.", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        b.votes.sortedByDescending { it.date }.forEach { v -> StateVoteCard(b.state, v, mineToo) }
                        if (b.sponsors.isNotEmpty()) {
                            InfoCard(title = "Sponsors") {
                                Text(
                                    b.sponsors.sortedByDescending { it.primary }.joinToString(", ") { s ->
                                        s.name + (s.party?.let { " (${it.first()})" } ?: "")
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        ActionsCard(b)
                        b.sources.firstOrNull()?.let { SourceLine("${b.state?.let { StateNames.of(it.uppercase()) } ?: "The state"} legislature", it) }
                        b.url?.let { SourceLine(StateBills.SOURCE_NAME, it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StateVoteCard(state: String?, v: StateVote, mine: List<Official>) {
    var showAll by remember { mutableStateOf(false) }
    InfoCard(title = "${StateBillDetails.chamberName(state, v.chamber)} vote") {
        Text(
            listOfNotNull(v.date?.let { Format.date(it) }, v.result?.let { if (it == "pass") "Passed" else if (it == "fail") "Failed" else it }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        v.motion?.let { Text(it.split(Regex("\\s+")).joinToString(" "), style = MaterialTheme.typography.titleSmall) }
        Text(
            v.counts.filterValues { it > 0 }.entries
                .sortedBy { (k, _) -> listOf("yes", "no").indexOf(k).let { i -> if (i < 0) 9 else i } }
                .joinToString(" · ") { (k, n) -> "${optionLabel(k)} $n" },
            style = MaterialTheme.typography.bodyMedium,
        )
        val yours = StateBillDetails.mine(v, mine)
        if (yours.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Your legislators", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            yours.forEach { (o, option) ->
                Row {
                    Text(o.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(optionLabel(option), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (v.voters.isNotEmpty()) {
            TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Hide how each member voted" else "How each member voted") }
            if (showAll) {
                v.voters.groupBy { it.option }.forEach { (option, people) ->
                    Text("${optionLabel(option)} (${people.size})", style = MaterialTheme.typography.labelLarge)
                    Text(people.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ActionsCard(b: StateBillDetail) {
    var all by remember { mutableStateOf(false) }
    if (b.actions.isEmpty()) return
    InfoCard(title = "Every step") {
        val shown = b.actions.asReversed().let { if (all) it else it.take(6) }
        shown.forEach { a ->
            Column(Modifier.fillMaxWidth()) {
                Text(
                    listOfNotNull(a.date?.let { Format.date(it) }, a.chamber?.let { StateBillDetails.chamberName(b.state, it) }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(a.text.orEmpty(), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (b.actions.size > 6) {
            Text(
                if (all) "Show fewer" else "Show all ${b.actions.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.clickable { all = !all }.padding(vertical = 4.dp),
            )
        }
    }
}

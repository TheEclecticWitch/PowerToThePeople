package com.theeclecticwitch.powertothepeople.voting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.congress.AppTally
import com.theeclecticwitch.powertothepeople.data.Http
import com.theeclecticwitch.powertothepeople.location.LocationStore
import com.theeclecticwitch.powertothepeople.location.today
import com.theeclecticwitch.powertothepeople.officials.StateNames
import com.theeclecticwitch.powertothepeople.ui.AppTopBar
import com.theeclecticwitch.powertothepeople.ui.Format
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.LoadingBox
import com.theeclecticwitch.powertothepeople.ui.ReadingColumn
import com.theeclecticwitch.powertothepeople.ui.SourceLine
import com.theeclecticwitch.powertothepeople.ui.openSafely
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Federal elections fall on the Tuesday after the first Monday in November of even-numbered years (2 U.S.C. 7):
 * never November 1, always November 2 to 8. Returns the next one on or after [from].
 */
fun nextFederalElection(from: LocalDate): LocalDate {
    var year = if (from.year % 2 == 0) from.year else from.year + 1
    while (true) {
        var day = LocalDate(year, Month.NOVEMBER, 1)
        while (day.dayOfWeek != DayOfWeek.MONDAY) day = day.plus(DatePeriod(days = 1))
        val election = day.plus(DatePeriod(days = 1))
        if (election >= from) return election
        year += 2
    }
}

/** vote.gov's page for registering in a state: vote.gov/register/new-hampshire. */
fun voteGovRegister(state: String): String {
    val name = StateNames.of(state)
    if (name.isBlank() || name == state) return "https://vote.gov/"
    return "https://vote.gov/register/" + name.lowercase().replace(' ', '-').replace(".", "")
}

@Serializable
data class Place(val name: String? = null, val address: String = "", val hours: String? = null, val notes: String? = null, val start: String? = null, val end: String? = null)

@Serializable
data class Candidate(
    val name: String,
    val party: String? = null,
    val url: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val photo: String? = null,
    val channels: List<Channel> = emptyList(),
)

@Serializable
data class Channel(val type: String, val id: String)

@Serializable
data class Measure(val title: String, val subtitle: String? = null, val text: String? = null, val url: String? = null, val choices: List<String> = emptyList())

@Serializable
data class Contest(
    val office: String? = null,
    val district: String? = null,
    val level: String? = null,
    val type: String? = null,
    val candidates: List<Candidate> = emptyList(),
    val measure: Measure? = null,
)

@Serializable
data class ElectionRef(val id: String, val name: String, val day: String? = null)

@Serializable
data class ElectionLinks(
    val info: String? = null,
    val register: String? = null,
    val checkRegistration: String? = null,
    val absentee: String? = null,
    val findPollingPlace: String? = null,
    val ballot: String? = null,
    val rules: String? = null,
)

@Serializable
data class VoterInfo(
    val election: ElectionRef? = null,
    val mailOnly: Boolean = false,
    val polling: List<Place> = emptyList(),
    val early: List<Place> = emptyList(),
    val dropOff: List<Place> = emptyList(),
    val contests: List<Contest> = emptyList(),
    val links: ElectionLinks = ElectionLinks(),
    val office: String? = null,
)

@Serializable
private class ServiceError(val error: String = "")

class VoterInfoUnavailable(message: String) : Exception(message)

object Voting {
    /**
     * Polling places, ballot and official links for an address, from state and local election offices by way of
     * Google's Civic Information service. Sent only when the reader asks; this app's service doesn't keep it.
     */
    suspend fun lookUp(address: String): VoterInfo {
        val body = buildJsonObject { put("address", JsonPrimitive(address)) }
        return try {
            Http.json.decodeFromString(
                Http.client.post("${AppTally.BASE}/voter-info") {
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }.bodyAsText(),
            )
        } catch (e: ResponseException) {
            val reason = try { Http.json.decodeFromString<ServiceError>(e.response.bodyAsText()).error } catch (x: Exception) { "" }
            throw VoterInfoUnavailable(reason.ifBlank { "Election information isn't available right now." }.replaceFirstChar { it.uppercase() })
        }
    }
}

/** On Overview: the next federal Election Day and how far off it is. */
@Composable
fun ElectionCard(onOpen: () -> Unit) {
    val now = today()
    val day = nextFederalElection(now)
    val days = day.toEpochDays() - now.toEpochDays()
    InfoCard(title = "Your vote", onClick = onOpen) {
        Text(
            when (days) {
                0L -> "Election Day is today, ${Format.date(day)}."
                1L -> "Election Day is tomorrow, ${Format.date(day)}."
                else -> "Election Day: ${Format.date(day)}, $days days away"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Check that you're registered, find where to vote, and see what's on your ballot.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("Get ready to vote ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun LinkLine(label: String, url: String?) {
    val uri = LocalUriHandler.current
    url ?: return
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.fillMaxWidth().clickable { openSafely(uri, url) }.padding(vertical = 4.dp),
    )
}

@Composable
fun VotingScreen(onBack: () -> Unit, onSetLocation: () -> Unit, onElections: () -> Unit = {}) {
    val location by LocationStore.location.collectAsState()
    val now = today()
    val day = nextFederalElection(now)
    var info by remember { mutableStateOf<VoterInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { AppTopBar("Your Vote", onBack) }) { padding ->
        ReadingColumn(Modifier.padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoCard(title = "Election Day") {
                    Text(Format.date(day), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Federal elections are held on the Tuesday after the first Monday in November of even-numbered " +
                            "years. Every House seat and about a third of Senate seats are on the ballot, along with many " +
                            "state and local offices. Many states also let you vote early or by mail.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                InfoCard(title = "Who's running", onClick = onElections) {
                    Text("Every candidate in your races, shown equally, with room for your own notes.", style = MaterialTheme.typography.bodyMedium)
                    Text("See the candidates ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                }
                val loc = location
                InfoCard(title = "Are you registered?") {
                    Text(
                        "You must be registered to vote, and deadlines can be weeks before Election Day. Each state sets its own " +
                            "rules; vote.gov, the U.S. government's official site, has your state's.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinkLine(if (loc != null) "Register or update your registration in ${loc.stateName} (vote.gov)" else "Register to vote (vote.gov)",
                        loc?.let { voteGovRegister(it.stateAbbr) } ?: "https://vote.gov/")
                    info?.links?.checkRegistration?.let { LinkLine("Check your registration (your state's official site)", it) }
                }
                InfoCard(title = "Where to vote, and what's on your ballot") {
                    if (loc == null) {
                        Text("Set your location to look up your polling place and ballot.", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = onSetLocation) { Text("Set my location") }
                        return@InfoCard
                    }
                    if (info == null) {
                        Text(
                            "This sends your saved address to Google's Civic Information service, which gathers polling places and " +
                                "ballots from state and local election offices. It goes through this app's own service, which " +
                                "doesn't keep it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(enabled = !busy, onClick = {
                            busy = true; error = null
                            scope.launch {
                                try { info = Voting.lookUp(loc.matchedAddress) } catch (e: VoterInfoUnavailable) { error = e.message } catch (e: Exception) { error = "Couldn't look that up. Check your connection." }
                                busy = false
                            }
                        }) { Text("Look up my polling place and ballot") }
                    }
                    if (busy) LoadingBox("Looking it up…")
                    error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                    info?.let { VoterInfoDetails(it) }
                }
                Spacer(Modifier.height(8.dp))
                SourceLine("vote.gov, the official U.S. government voter registration site", "https://vote.gov/")
            }
        }
    }
}

@Composable
private fun PlaceList(title: String, places: List<Place>) {
    if (places.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleMedium)
    places.take(5).forEach { p ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            p.name?.let { Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
            Text(p.address, style = MaterialTheme.typography.bodyMedium)
            listOfNotNull(
                if (p.start != null && p.end != null) "${Format.date(p.start)} to ${Format.date(p.end)}" else null,
                p.hours,
                p.notes,
            ).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun VoterInfoDetails(v: VoterInfo) {
    v.election?.let {
        Text(listOfNotNull(it.name, it.day?.let { d -> Format.date(d) }).joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
    }
    if (v.mailOnly) Text("Your area votes by mail.", style = MaterialTheme.typography.bodyLarge)
    PlaceList("Your polling place on Election Day", v.polling)
    PlaceList("Early voting", v.early)
    PlaceList("Ballot drop boxes", v.dropOff)
    if (v.polling.isEmpty() && v.early.isEmpty() && !v.mailOnly) {
        Text("Your polling place hasn't been published yet; your state's own lookup is below.", style = MaterialTheme.typography.bodyMedium)
    }
    LinkLine("Find your polling place (your state's official site)", v.links.findPollingPlace)
    if (v.contests.isNotEmpty()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("On your ballot", style = MaterialTheme.typography.titleMedium)
        Text(
            "Candidates are listed as the election office reports them, in no particular order. Look up each one before you decide.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        v.contests.forEach { c ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (c.measure != null) {
                    Text(c.measure.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    c.measure.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    c.measure.text?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    LinkLine("Read the full measure", c.measure.url)
                } else {
                    Text(listOfNotNull(c.office, c.district).distinct().joinToString(" · "), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    c.candidates.forEach { p ->
                        Text("• ${p.name}" + (p.party?.let { " ($it)" } ?: ""), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text("From your state's election office", style = MaterialTheme.typography.titleMedium)
    LinkLine("Election information", v.links.info)
    LinkLine("Voting by mail or absentee", v.links.absentee)
    LinkLine("Your sample ballot", v.links.ballot)
    LinkLine("Voting rules", v.links.rules)
    SourceLine("Google Civic Information API, from ${v.office ?: "state and local election offices"}", v.links.info)
}

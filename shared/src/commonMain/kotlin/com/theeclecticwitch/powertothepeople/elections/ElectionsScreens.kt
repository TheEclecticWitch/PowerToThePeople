package com.theeclecticwitch.powertothepeople.elections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.theeclecticwitch.powertothepeople.location.LocationStore
import com.theeclecticwitch.powertothepeople.location.today
import com.theeclecticwitch.powertothepeople.ui.AppTopBar
import com.theeclecticwitch.powertothepeople.ui.Format
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.LoadingBox
import com.theeclecticwitch.powertothepeople.ui.ReadingColumn
import com.theeclecticwitch.powertothepeople.ui.SourceLine
import com.theeclecticwitch.powertothepeople.ui.openSafely
import com.theeclecticwitch.powertothepeople.voting.Measure
import com.theeclecticwitch.powertothepeople.voting.VoterInfo
import com.theeclecticwitch.powertothepeople.voting.VoterInfoUnavailable
import com.theeclecticwitch.powertothepeople.voting.Voting
import com.theeclecticwitch.powertothepeople.voting.nextFederalElection
import kotlinx.coroutines.launch

/** The ballot looked up during this visit, so coming back from a race doesn't ask Google again. */
private object BallotMemory {
    var info: VoterInfo? = null
}

@Composable
fun ElectionsScreen(onBack: () -> Unit, onRace: (String) -> Unit, onSetLocation: () -> Unit) {
    val location by LocationStore.location.collectAsState()
    val now = today()
    val day = nextFederalElection(now)
    val cycle = Elections.cycle(day.year)
    var congress by remember(location) { mutableStateOf<List<Race>?>(null) }
    var congressLoaded by remember(location) { mutableStateOf(false) }
    var ballot by remember { mutableStateOf(BallotMemory.info) }
    var ballotError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(location) {
        val loc = location ?: return@LaunchedEffect
        congress = try { Elections.congressRaces(loc, cycle) } catch (e: Exception) { null }
        congressLoaded = true
    }
    Scaffold(topBar = { AppTopBar("Elections", onBack) }) { padding ->
        ReadingColumn(Modifier.padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoCard(title = "Election Day") {
                    Text(Format.date(day), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Who is running where you live, and what you'd like to remember about each of them. Every candidate " +
                            "is shown the same way, and each race opens in a new random order, so no one is always first.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                val loc = location
                if (loc == null) {
                    InfoCard(title = "Your races") {
                        Text("Set your location to see the races you can vote in.", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = onSetLocation) { Text("Set my location") }
                    }
                    return@Column
                }

                InfoCard(title = "Your ballot") {
                    val b = ballot
                    if (b == null) {
                        Text(
                            "Every race on your ballot, from president or governor to local offices and ballot questions, as your " +
                                "state and local election offices publish it. This sends your saved address to Google's Civic " +
                                "Information service through this app's own service, which doesn't keep it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(enabled = !busy, onClick = {
                            busy = true; ballotError = null
                            scope.launch {
                                try {
                                    ballot = Voting.lookUp(loc.matchedAddress).also { BallotMemory.info = it }
                                } catch (e: VoterInfoUnavailable) {
                                    ballotError = e.message
                                } catch (e: Exception) {
                                    ballotError = "Couldn't look that up. Check your connection."
                                }
                                busy = false
                            }
                        }) { Text("Show my ballot") }
                        if (busy) LoadingBox("Looking it up…")
                        ballotError?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "States usually publish ballots a few weeks before Election Day. The candidates for Congress " +
                                    "below are available now.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        val races = remember(b) { Elections.ballotRaces(b, loc.stateAbbr) }
                        b.election?.let { Text(listOfNotNull(it.name, it.day?.let { d -> Format.date(d) }).joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary) }
                        if (races.isEmpty() && b.contests.none { it.measure != null }) {
                            Text("Your ballot's races haven't been published yet. Check back closer to Election Day.", style = MaterialTheme.typography.bodyMedium)
                        }
                        races.forEach { r -> RaceRow(r) { onRace(r.id) } }
                        val measures = b.contests.mapNotNull { it.measure }
                        if (measures.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text("Ballot questions", style = MaterialTheme.typography.titleSmall)
                            measures.forEach { MeasureRow(it) }
                        }
                    }
                }

                InfoCard(title = "Running for Congress") {
                    val list = congress
                    when {
                        !congressLoaded -> LoadingBox("Loading…")
                        list == null -> Text("The list of candidates appears after the next data update.", style = MaterialTheme.typography.bodyMedium)
                        list.isEmpty() -> Text("No candidates for your seats are listed yet.", style = MaterialTheme.typography.bodyMedium)
                        else -> list.forEach { r -> RaceRow(r) { onRace(r.id) } }
                    }
                    Text(
                        "Candidates registered with the Federal Election Commission for your seats in the House and, when one " +
                            "is up, the Senate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SourceLine("Federal Election Commission", "https://www.fec.gov/data/elections/")
            }
        }
    }
}

@Composable
private fun RaceRow(r: Race, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 6.dp)) {
        Text(r.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            listOfNotNull(r.subtitle, "${r.candidates.size} candidate${if (r.candidates.size == 1) "" else "s"}").joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("See the candidates ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun MeasureRow(m: Measure) {
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(m.title, style = MaterialTheme.typography.titleSmall)
        m.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        m.text?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        m.url?.let {
            Text(
                "Read the full measure",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { openSafely(uri, it) },
            )
        }
    }
}

/**
 * One race. Every candidate gets the same card: same size, same fields, same style. The order is shuffled each
 * time the race is opened, and pictures show only if every candidate has one.
 */
@Composable
fun RaceScreen(id: String, onBack: () -> Unit, onCandidate: (String) -> Unit) {
    var race by remember(id) { mutableStateOf<Race?>(null) }
    var missing by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) {
        race = try { Elections.race(id) } catch (e: Exception) { null }
        missing = race == null
    }
    val r = race
    // A fresh order each time this page is opened (Rod's choice), kept while it stays open.
    val order = remember(r) { r?.candidates?.shuffled().orEmpty() }
    val notes by CandidateNotes.flow.collectAsState()
    Scaffold(topBar = { AppTopBar(r?.title ?: "Race", onBack) }) { padding ->
        ReadingColumn(Modifier.padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (r == null) {
                    if (missing) Text("This race isn't available right now. Open Elections again to look it up.", style = MaterialTheme.typography.bodyLarge)
                    else LoadingBox("Loading…")
                    return@Column
                }
                r.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(r.sourceNote, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Listed in a random order, new each time you open this race.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val photos = order.isNotEmpty() && order.all { it.photo != null }
                val statuses = order.all { it.status != null }
                order.forEach { c ->
                    CandidateCard(c, showPhoto = photos, showStatus = statuses, hasNote = notes[c.key] != null) { onCandidate(c.key) }
                }
                SourceLine(r.sourceName, r.sourceUrl)
            }
        }
    }
}

@Composable
private fun CandidateCard(c: RaceCandidate, showPhoto: Boolean, showStatus: Boolean, hasNote: Boolean, onOpen: () -> Unit) {
    InfoCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 72.dp)) {
            if (showPhoto) {
                AsyncImage(
                    model = c.photo,
                    contentDescription = "Photo of ${c.name}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(c.name, style = MaterialTheme.typography.titleMedium)
                Text(c.party ?: "Party not listed", style = MaterialTheme.typography.bodyMedium)
                if (showStatus) statusLine(c.status)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(
                    if (hasNote) "Your notes ›" else "Details and your notes ›",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

/** One candidate: what the sources list, and the reader's own private notes. */
@Composable
fun CandidateScreen(raceId: String, key: String, onBack: () -> Unit) {
    var race by remember(raceId) { mutableStateOf<Race?>(null) }
    LaunchedEffect(raceId) { race = try { Elections.race(raceId) } catch (e: Exception) { null } }
    val c = race?.candidates?.firstOrNull { it.key == key }
    val notes by CandidateNotes.flow.collectAsState()
    var draft by remember(key) { mutableStateOf(notes[key].orEmpty()) }
    val uri = LocalUriHandler.current
    Scaffold(topBar = { AppTopBar(c?.name ?: "Candidate", onBack) }) { padding ->
        ReadingColumn(Modifier.padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (c != null) {
                    val r = race!!
                    Text(c.name, style = MaterialTheme.typography.headlineSmall)
                    Text(r.title, style = MaterialTheme.typography.bodyLarge)
                    Text(c.party ?: "Party not listed", style = MaterialTheme.typography.bodyMedium)
                    if (r.candidates.all { it.status != null }) statusLine(c.status)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    InfoCard(title = "Learn more") {
                        val links = listOfNotNull(
                            c.website?.let { "Campaign website" to it },
                            c.fecUrl?.let { "Campaign finance filings (FEC)" to it },
                            c.email?.let { "Email the campaign" to "mailto:$it" },
                            c.phone?.let { "Call the campaign: $it" to "tel:${it.filter { ch -> ch.isDigit() || ch == '+' }}" },
                        ) + c.channels.mapNotNull { (type, id) -> socialLink(type, id) }
                        if (links.isEmpty()) Text("No links listed for this candidate yet.", style = MaterialTheme.typography.bodyMedium)
                        links.forEach { (label, url) ->
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier.clickable { openSafely(uri, url) }.padding(vertical = 4.dp),
                            )
                        }
                    }
                } else {
                    Text("This candidate isn't available right now. Open the race again.", style = MaterialTheme.typography.bodyLarge)
                }
                InfoCard(title = "Your notes") {
                    Text(
                        "What you like, what you don't, what you want to remember. Kept only on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it; CandidateNotes.set(key, it) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                        placeholder = { Text("Write anything…") },
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun socialLink(type: String, id: String): Pair<String, String>? {
    if (id.startsWith("https://")) return type to id
    return when (type.lowercase()) {
        "facebook" -> "Facebook" to "https://www.facebook.com/$id"
        "twitter" -> "X (Twitter)" to "https://x.com/$id"
        "youtube" -> "YouTube" to "https://www.youtube.com/$id"
        "instagram" -> "Instagram" to "https://www.instagram.com/$id"
        else -> null
    }
}

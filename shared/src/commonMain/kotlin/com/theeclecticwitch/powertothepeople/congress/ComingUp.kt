package com.theeclecticwitch.powertothepeople.congress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.data.CachedSource
import com.theeclecticwitch.powertothepeople.data.Http
import com.theeclecticwitch.powertothepeople.ui.AppTopBar
import com.theeclecticwitch.powertothepeople.ui.ErrorBox
import com.theeclecticwitch.powertothepeople.ui.Format
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.LoadingBox
import com.theeclecticwitch.powertothepeople.ui.LoopingTabs
import com.theeclecticwitch.powertothepeople.ui.ReadingColumn
import com.theeclecticwitch.powertothepeople.ui.SourceLine
import com.theeclecticwitch.powertothepeople.ui.openSafely
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlin.time.Duration.Companion.hours
import kotlinx.serialization.Serializable

@Serializable
data class FloorItem(val number: String, val bill: String? = null, val title: String, val how: String? = null)

@Serializable
data class HouseWeek(val week: String, val items: List<FloorItem> = emptyList(), val url: String? = null)

@Serializable
data class SenateNext(val next: String? = null, val plan: String? = null, val previous: String? = null, val url: String? = null)

@Serializable
data class Hearing(
    val id: String,
    val chamber: String,
    val date: String? = null,
    val title: String = "",
    val type: String? = null,
    val status: String? = null,
    val committees: List<String> = emptyList(),
    val room: String? = null,
    val bills: List<String> = emptyList(),
    val url: String? = null,
)

@Serializable
data class OpenRule(
    val id: String,
    val title: String = "",
    val agency: String? = null,
    val posted: String? = null,
    val closes: String? = null,
    val url: String? = null,
    val comment: String? = null,
)

@Serializable
data class Upcoming(
    val house: List<HouseWeek> = emptyList(),
    val senate: SenateNext? = null,
    val hearings: List<Hearing> = emptyList(),
    val comments: List<OpenRule> = emptyList(),
    val checked: String? = null,
)

/**
 * Notices about one aircraft model, one airport's airspace or one stretch of water: real rules, but technical and
 * local. They're tucked behind a switch by kind of notice, never by topic or agency, so nothing is hidden for a view.
 */
private val routineKinds = listOf(
    "Airworthiness Directives", "Airspace Designations", "Special Conditions:", "Safety Zone", "Safety Zones",
    "Drawbridge Operation", "Special Local Regulation", "Anchorage", "Regulated Navigation Area",
)
private val routineAirspace = Regex("""^(Amendment|Establishment|Revocation|Modification) of .*Airspace""")

val OpenRule.isRoutine: Boolean
    get() = routineKinds.any { title.startsWith(it, ignoreCase = true) } || routineAirspace.containsMatchIn(title)

object ComingUp {
    private val source = CachedSource("pd_upcoming.json", 3.hours) { Http.getText("${CongressData.BASE}/upcoming.json") }

    suspend fun load(force: Boolean = false): Upcoming = try {
        Http.json.decodeFromString(source.get(force).text)
    } catch (e: ClientRequestException) {
        if (e.response.status == HttpStatusCode.NotFound) Upcoming() else throw e
    }
}

/** "Items that may be considered under suspension of the rules" -> what that means, briefly. */
private fun howExplained(how: String?): String? = when {
    how == null -> null
    how.contains("suspension", true) ->
        "Under suspension of the rules: a fast track for less controversial bills. Debate is limited and passing takes two-thirds."
    how.contains("rule", true) ->
        "Under a rule: the House first votes on a rule setting how long it debates and what changes may be offered."
    else -> null
}

/** On the Congress tab: what each chamber, its committees and the agencies have coming up. */
@Composable
fun ComingUpCard(onOpen: () -> Unit) {
    val (load, _) = rememberLoad(Unit, "Couldn't load what's coming up.") { ComingUp.load() }
    InfoCard(title = "Coming up", onClick = onOpen) {
        when (val l = load) {
            Load.Loading -> LoadingBox("Loading…")
            is Load.Failed -> Text(l.message, style = MaterialTheme.typography.bodyMedium)
            is Load.Done -> {
                val u = l.value
                val houseItems = u.house.sumOf { it.items.size }
                Text(
                    if (houseItems == 1) "House: 1 bill scheduled for the floor" else if (houseItems > 0) "House: $houseItems bills scheduled for the floor" else "House: no floor schedule posted for this week or next",
                    style = MaterialTheme.typography.bodyLarge,
                )
                u.senate?.next?.let { Text("Senate: meets ${it}${u.senate.plan?.let { p -> " · $p" } ?: ""}", style = MaterialTheme.typography.bodyLarge) }
                if (u.hearings.isNotEmpty()) Text(if (u.hearings.size == 1) "1 committee hearing or meeting ahead" else "${u.hearings.size} committee hearings and meetings ahead", style = MaterialTheme.typography.bodyMedium)
                val rules = u.comments.count { !it.isRoutine }
                if (rules > 0) Text(if (rules == 1) "1 proposed federal rule open for your comment" else "$rules proposed federal rules open for your comment", style = MaterialTheme.typography.bodyMedium)
                Text("See what's coming ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun Link(label: String, url: String?) {
    val uri = LocalUriHandler.current
    url ?: return
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable { openSafely(uri, url) }.padding(vertical = 2.dp),
    )
}

@Composable
fun ComingUpScreen(onBack: () -> Unit, nav: CongressNav) {
    val (load, retry) = rememberLoad(Unit, "Couldn't load what's coming up. Check your connection.") { force -> ComingUp.load(force) }
    Scaffold(topBar = { AppTopBar("Coming Up", onBack) }) { padding ->
        ReadingColumn(Modifier.padding(padding)) {
            when (val l = load) {
                Load.Loading -> LoadingBox()
                is Load.Failed -> ErrorBox(l.message, retry)
                is Load.Done -> LoopingTabs(listOf("Floor", "Hearings", "Comment")) { tab ->
                    when (tab) {
                        0 -> FloorTab(l.value, nav)
                        1 -> HearingsTab(l.value, nav)
                        else -> CommentTab(l.value)
                    }
                }
            }
        }
    }
}

@Composable
private fun FloorTab(u: Upcoming, nav: CongressNav) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                "What the House and Senate plan to take up. Contacting your members works best before they vote.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            InfoCard(title = "The Senate") {
                val s = u.senate
                if (s?.next == null) {
                    Text("The Senate's schedule isn't available right now.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("Next meets: ${s.next}", style = MaterialTheme.typography.titleMedium)
                    s.plan?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    s.previous?.let { Text("Last time: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (s.plan?.contains("pro forma", true) == true) {
                        Text(
                            "A pro forma session is a brief meeting, often minutes long, with no business done.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SourceLine("U.S. Senate", s?.url ?: "https://www.senate.gov/legislative/schedule/floor_schedule.htm")
            }
        }
        if (u.house.isEmpty()) {
            item {
                InfoCard(title = "The House") {
                    Text(
                        "No House floor schedule is posted for this week or next. The House may be in a district work period, " +
                            "when members are home in their districts, or the schedule may not be out yet.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    SourceLine("docs.house.gov, the House floor schedule", "https://docs.house.gov/floor/")
                }
            }
        }
        u.house.forEach { week ->
            item(key = "w${week.week}") {
                Text("The House, week of ${Format.date(week.week)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            week.items.groupBy { it.how }.forEach { (how, items) ->
                item(key = "h${week.week}$how") {
                    howExplained(how)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(items, key = { "${week.week}${it.number}${it.title}" }) { item ->
                    Column(
                        Modifier.fillMaxWidth().clickable(enabled = item.bill != null) { item.bill?.let(nav.bill) }.padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(item.number, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun HearingsTab(u: Upcoming, nav: CongressNav) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                "Committees are where most bills are shaped or stopped. Most hearings are open to the public and many are " +
                    "streamed online.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (u.hearings.isEmpty()) {
            item { Text("No upcoming committee meetings are listed right now.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(u.hearings, key = { it.id }) { h ->
            InfoCard {
                Text(
                    listOfNotNull(h.date?.let { Format.date(it) }, if (h.chamber == "senate") "Senate" else "House", h.type).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (h.status != null && !h.status.equals("Scheduled", true)) {
                    Text(h.status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                }
                Text(h.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (h.committees.isNotEmpty()) Text(h.committees.joinToString("; "), style = MaterialTheme.typography.bodyMedium)
                h.room?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                h.bills.forEach { b ->
                    Text(
                        BillNames.label(b),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { nav.bill(b) },
                    )
                }
                Link("Details on Congress.gov", h.url)
            }
        }
        item { SourceLine("Congress.gov committee schedule", "https://www.congress.gov/committee-schedule") }
    }
}

@Composable
private fun CommentTab(u: Upcoming) {
    var shown by remember { mutableIntStateOf(50) }
    var showRoutine by rememberSaveable { mutableStateOf(false) }
    val routine = u.comments.count { it.isRoutine }
    val rules = if (showRoutine) u.comments else u.comments.filterNot { it.isRoutine }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                "Before a federal agency makes a new rule final, the law requires it to publish the proposal and take " +
                    "comments from anyone. Agencies must consider the comments they receive. These proposals are open now, " +
                    "closing soonest first.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (u.comments.isEmpty()) {
            item { Text("The list of rules open for comment will appear after the next data update.", style = MaterialTheme.typography.bodyMedium) }
        }
        if (routine > 0) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Show routine technical notices ($routine)", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Safety orders for one aircraft model, airspace at one airport, and Coast Guard notices for one " +
                                "stretch of water.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = showRoutine, onCheckedChange = { showRoutine = it })
                }
            }
        }
        items(rules.take(shown), key = { it.id }) { r ->
            InfoCard {
                Text(
                    listOfNotNull(r.agency, r.closes?.let { "comments close ${Format.date(it)}" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(r.title, style = MaterialTheme.typography.titleSmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Link("Read it and comment on Regulations.gov", r.comment ?: r.url)
            }
        }
        if (rules.size > shown) item { OutlinedButton(onClick = { shown += 50 }) { Text("Show more") } }
        item {
            Spacer(Modifier.height(4.dp))
            SourceLine("Regulations.gov", "https://www.regulations.gov/")
        }
    }
}

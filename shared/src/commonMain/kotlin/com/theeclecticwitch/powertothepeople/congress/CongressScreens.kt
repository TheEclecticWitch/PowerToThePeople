package com.theeclecticwitch.powertothepeople.congress

import androidx.compose.material3.Button
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import com.theeclecticwitch.powertothepeople.officials.Official
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Switch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.location.LocationStore
import com.theeclecticwitch.powertothepeople.officials.rememberDelegation
import com.theeclecticwitch.powertothepeople.ui.AppTopBar
import com.theeclecticwitch.powertothepeople.ui.CenteredMessage
import com.theeclecticwitch.powertothepeople.ui.ErrorBox
import com.theeclecticwitch.powertothepeople.ui.Format
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.LoadingBox
import com.theeclecticwitch.powertothepeople.ui.ReadingColumn
import com.theeclecticwitch.powertothepeople.ui.SourceLine
import com.theeclecticwitch.powertothepeople.ui.Tag
import com.theeclecticwitch.powertothepeople.ui.openSafely

/** Where the vote and bill screens can send the reader. */
class CongressNav(
    val vote: (chamber: String, session: Int, roll: Int) -> Unit,
    val bill: (id: String) -> Unit,
    val official: (id: String) -> Unit,
    val memberVotes: (id: String) -> Unit,
    val sponsoredBills: (id: String) -> Unit,
    val recentVotes: () -> Unit,
    val setLocation: () -> Unit,
)

private sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Done<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

/** Runs [block] when [key] changes; "Try again" runs it once more, past the cache. */
@Composable
private fun <T> rememberLoad(key: Any?, failure: String, block: suspend (force: Boolean) -> T): Pair<Load<T>, () -> Unit> {
    var state by remember(key) { mutableStateOf<Load<T>>(Load.Loading) }
    var attempt by remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key, attempt) {
        state = Load.Loading
        state = try {
            Load.Done(block(attempt > 0))
        } catch (e: Exception) {
            Load.Failed(failure)
        }
    }
    return state to { attempt++ }
}

/** Bill titles, once they arrive. Screens show without them first, since the list is large. */
@Composable
private fun rememberBillTitles(): Map<String, BillSummary> {
    var bills by remember { mutableStateOf<Map<String, BillSummary>>(emptyMap()) }
    LaunchedEffect(Unit) {
        bills = try { CongressData.billList() } catch (e: Exception) { emptyMap() }
    }
    return bills
}

private const val OFFLINE = "Couldn't load the voting records. Check your connection."

@Composable
private fun CongressSource() {
    SourceLine(CongressData.SOURCE_NAME, CongressData.SOURCE_URL, "gathered every six hours")
}

// --- On an official's page ---

/** A member of Congress's votes and bills, for their page. Nothing for anyone else. */
@Composable
fun MemberRecordCards(id: String, nav: CongressNav) {
    val (load, retry) = rememberLoad(id, OFFLINE) { force ->
        CongressData.member(id, force)?.let { it to CongressData.castVotes(it) }
    }
    val titles = rememberBillTitles()
    when (val l = load) {
        Load.Loading -> InfoCard(title = "Voting record") { LoadingBox("Loading votes…") }
        is Load.Failed -> InfoCard(title = "Voting record") { ErrorBox(l.message, retry) }
        is Load.Done -> {
            val (record, cast) = l.value ?: run {
                InfoCard(title = "Voting record") {
                    Text("No votes are on file yet. New members appear after the next update.", style = MaterialTheme.typography.bodyMedium)
                }
                return
            }
            val tally = CongressData.tally(record.votes)
            // How they voted compared with the reader comes first: it is the part about the reader.
            AlignmentCard(record, cast, titles, nav)
            InfoCard(title = "Voting record") {
                Text(
                    "${Format.commas(tally.total.toLong())} roll-call votes in the ${Format.ordinal(record.congress)} Congress",
                    style = MaterialTheme.typography.titleMedium,
                )
                TallyLine(tally)
                if (cast.isNotEmpty()) {
                    Text("Latest votes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    cast.take(5).forEachIndexed { i, c ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        VoteRow(c.vote.chamber, c.summary, c.vote.roll, titles, c.vote.vote) {
                            nav.vote(c.vote.chamber, c.vote.session, c.vote.roll)
                        }
                    }
                    OutlinedButton(onClick = { nav.memberVotes(id) }) { Text("See all ${Format.commas(tally.total.toLong())} votes") }
                }
                CongressSource()
            }
            InfoCard(title = "Bills sponsored") {
                val n = record.sponsored.size
                Text(
                    when (n) {
                        0 -> "No bills or resolutions sponsored this Congress"
                        1 -> "1 bill or resolution sponsored this Congress"
                        else -> "${Format.commas(n.toLong())} bills and resolutions sponsored this Congress"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                val newest = record.sponsored.mapNotNull { b -> titles[b]?.let { b to it } }
                    .sortedByDescending { it.second.introduced }.take(3)
                newest.forEachIndexed { i, (b, s) ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    BillRow(b, s) { nav.bill(b) }
                }
                if (n > newest.size) OutlinedButton(onClick = { nav.sponsoredBills(id) }) { Text("See all $n") }
            }
        }
    }
}

@Composable
private fun TallyLine(t: VoteTally) {
    fun pct(n: Int) = if (t.total == 0) "0" else Format.decimals(n * 100.0 / t.total, 1)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Tag("Yea ${t.yea}")
        Tag("Nay ${t.nay}")
        if (t.present > 0) Tag("Present ${t.present}")
        Tag("Did not vote ${t.notVoting} (${pct(t.notVoting)}%)")
        if (t.other > 0) Tag("Named a candidate ${t.other}")
    }
}

/** One roll call: when, what was asked, on what bill, how it came out - and, given, how someone voted. */
@Composable
private fun VoteRow(
    chamber: String,
    summary: VoteSummary?,
    roll: Int,
    titles: Map<String, BillSummary>,
    theirVote: String? = null,
    showBill: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            listOfNotNull(summary?.date?.let { Format.date(it) }, "${chamberName(chamber)} roll call $roll").joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(summary?.question ?: "Roll call $roll", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        val about = summary?.takeIf { showBill }?.let { s ->
            val billTitle = s.bill?.let { titles[it]?.title }
            // The bill's own title says more than the Senate's "Motion to Proceed to S.J.Res. 99".
            listOfNotNull(s.bill?.let { BillNames.label(it) }, billTitle ?: s.title).joinToString(": ").ifBlank { null }
        }
        about?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            summary?.result?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            } ?: Spacer(Modifier.weight(1f))
            theirVote?.let { Spacer(Modifier.width(8.dp)); Tag(if (it.startsWith("You: ")) it else voteLabel(it)) }
        }
    }
}

@Composable
private fun BillRow(id: String, s: BillSummary?, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            listOfNotNull(BillNames.label(id), s?.introduced?.let { "introduced ${Format.date(it)}" }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(s?.title ?: BillNames.label(id), style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        val action = s?.latestAction
        action?.text?.let {
            Text(
                listOfNotNull(action.date?.let { d -> Format.date(d) }, it).joinToString(": "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// --- Every vote a member cast ---

@Composable
fun MemberVotesScreen(id: String, onBack: () -> Unit, nav: CongressNav) {
    val (load, retry) = rememberLoad(id, OFFLINE) { force ->
        CongressData.member(id, force)?.let { it to CongressData.castVotes(it) }
    }
    val titles = rememberBillTitles()
    var filter by remember { mutableStateOf<String?>(null) }
    val name = (load as? Load.Done)?.value?.first?.name
    Scaffold(topBar = { AppTopBar(name?.let { "$it's votes" } ?: "Votes", onBack) }) { padding ->
        ReadingColumn(Modifier.padding(padding)) {
            when (val l = load) {
                Load.Loading -> LoadingBox("Loading votes…")
                is Load.Failed -> ErrorBox(l.message, retry)
                is Load.Done -> {
                    val (record, cast) = l.value ?: run { CenteredMessage("No votes are on file for this member yet."); return@ReadingColumn }
                    val filters = listOf(null to "All", "Yea" to "Yea", "Nay" to "Nay", "Not Voting" to "Did not vote", "Present" to "Present")
                    val shown = cast.filter { filter == null || it.vote.vote == filter }
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                TallyLine(CongressData.tally(record.votes))
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    filters.forEach { (value, label) ->
                                        FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(label) })
                                    }
                                }
                            }
                        }
                        if (shown.isEmpty()) item { Text("No votes match.", style = MaterialTheme.typography.bodyMedium) }
                        items(shown, key = { "${it.vote.chamber}/${it.vote.session}/${it.vote.roll}" }) { c ->
                            VoteRow(c.vote.chamber, c.summary, c.vote.roll, titles, c.vote.vote) {
                                nav.vote(c.vote.chamber, c.vote.session, c.vote.roll)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        item { Spacer(Modifier.height(8.dp)); CongressSource() }
                    }
                }
            }
        }
    }
}

// --- Every bill a member sponsored ---

@Composable
fun SponsoredBillsScreen(id: String, onBack: () -> Unit, nav: CongressNav) {
    val (load, retry) = rememberLoad(id, "Couldn't load the bills. Check your connection.") { force ->
        CongressData.member(id, force)?.let { it to CongressData.billList() }
    }
    val name = (load as? Load.Done)?.value?.first?.name
    Scaffold(topBar = { AppTopBar(name?.let { "Bills by $it" } ?: "Bills sponsored", onBack) }) { padding ->
        ReadingColumn(Modifier.padding(padding)) {
            when (val l = load) {
                Load.Loading -> LoadingBox("Loading bills…")
                is Load.Failed -> ErrorBox(l.message, retry)
                is Load.Done -> {
                    val (record, bills) = l.value ?: run { CenteredMessage("No record is on file for this member yet."); return@ReadingColumn }
                    val sorted = record.sponsored.sortedByDescending { bills[it]?.introduced ?: "" }
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        item {
                            Text(
                                "Bills and resolutions ${record.name} introduced as the main sponsor, newest first.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        items(sorted, key = { it }) { b ->
                            BillRow(b, bills[b]) { nav.bill(b) }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        item { Spacer(Modifier.height(8.dp)); CongressSource() }
                    }
                }
            }
        }
    }
}

// --- One roll call ---

/** The usual order of the answers; anything else (the Speaker's election) follows, largest first. */
private val voteOrder = listOf("Yea", "Nay", "Present", "Not Voting")

@Composable
fun VoteScreen(chamber: String, session: Int, roll: Int, onBack: () -> Unit, nav: CongressNav) {
    val (load, retry) = rememberLoad(Triple(chamber, session, roll), "Couldn't load this vote. Check your connection.") {
        CongressData.vote(chamber, session, roll)
    }
    val titles = rememberBillTitles()
    val location by LocationStore.location.collectAsState()
    val (delegation, _, _) = rememberDelegation(location)
    val uri = LocalUriHandler.current
    var tallyRefresh by remember { mutableIntStateOf(0) }
    // Catch up on any answer that couldn't be sent earlier.
    LaunchedEffect(Unit) { AppTally.sync() }
    Scaffold(topBar = { AppTopBar("${chamberName(chamber)} roll call $roll", onBack) }) { padding ->
        ReadingColumn(Modifier.padding(padding)) {
            when (val l = load) {
                Load.Loading -> LoadingBox("Loading the vote…")
                is Load.Failed -> ErrorBox(l.message, retry)
                is Load.Done -> {
                    val v = l.value
                    val groups = v.positions.groupBy { it.vote }
                    val order = voteOrder.filter { it in groups } +
                        groups.keys.filter { it !in voteOrder }.sortedByDescending { groups[it]?.size ?: 0 }
                    val mineIds = delegation?.let { d -> listOfNotNull(d.representative) + d.senators }?.map { it.id }.orEmpty()
                    val mine = v.positions.filter { it.id != null && it.id in mineIds }
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "${Format.date(v.date)} · ${chamberName(chamber)} · ${Format.ordinal(v.congress)} Congress, session ${v.session}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(v.question ?: "Roll call $roll", style = MaterialTheme.typography.headlineSmall)
                                val billTitle = v.bill?.let { titles[it]?.title }
                                (v.title ?: billTitle)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                                v.result?.let { Tag(it) }
                            }
                        }
                        v.bill?.let { b ->
                            item {
                                InfoCard(onClick = { nav.bill(b) }) {
                                    Text("The bill", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(BillNames.label(b), style = MaterialTheme.typography.titleMedium)
                                    titles[b]?.title?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                }
                            }
                        }
                        if ("Yea" in groups || "Nay" in groups) {
                            val key = MyPositions.key(chamber, session, roll)
                            item { YourViewCard(key) { tallyRefresh++ } }
                            item { AppTallyCard(key, tallyRefresh) }
                        }
                        if (mine.isNotEmpty()) {
                            item {
                                InfoCard(title = "Your members") {
                                    mine.forEach { p -> PositionRow(p, showVote = true) { p.id?.let(nav.official) } }
                                }
                            }
                        }
                        item {
                            InfoCard(title = "Totals") {
                                order.forEach { vote ->
                                    val people = groups[vote].orEmpty()
                                    val byParty = people.groupingBy { it.party ?: "?" }.eachCount().entries
                                        .sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${it.value}" }
                                    Text("${voteLabel(vote)}: ${people.size}  ($byParty)", style = MaterialTheme.typography.bodyMedium)
                                }
                                v.type?.let {
                                    Text("Vote type: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        order.forEach { vote ->
                            val people = groups[vote].orEmpty().sortedBy { it.name.substringAfterLast(' ') }
                            item(key = "h:$vote") {
                                Text(
                                    "${voteLabel(vote)} (${people.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            items(people, key = { "$vote:${it.id ?: it.name}" }) { p ->
                                PositionRow(p, showVote = false) { p.id?.let(nav.official) }
                            }
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                v.source?.let { src ->
                                    Text(
                                        "Read the official record",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        textDecoration = TextDecoration.Underline,
                                        modifier = Modifier.clickable { openSafely(uri, src) }.padding(vertical = 4.dp),
                                    )
                                }
                                SourceLine(
                                    if (chamber == "senate") "U.S. Senate" else "Office of the Clerk, U.S. House",
                                    v.source,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PositionRow(p: Position, showVote: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = p.id != null, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(p.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            listOfNotNull(p.party, p.state).joinToString("-"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showVote) { Spacer(Modifier.width(8.dp)); Tag(voteLabel(p.vote)) }
    }
}

/**
 * Lets the reader say how they would have voted, and - only if they turn it on - add that answer to the
 * anonymous app-wide count. [onShared] runs after the server has the new answer, so the count can refresh.
 */
@Composable
private fun YourViewCard(
    key: String,
    question: String = "How would you have voted?",
    yes: String = "Yea",
    no: String = "Nay",
    usedFor: String = "your members' pages will show how often they voted the way you would have",
    onShared: () -> Unit,
) {
    val positions by MyPositions.flow.collectAsState()
    val prefs by AppTally.prefsFlow.collectAsState()
    val scope = rememberCoroutineScope()
    val current = positions[key]
    fun syncThenRefresh() = scope.launch { AppTally.sync(); onShared() }
    InfoCard(title = "Your view") {
        Text(question, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Stored as Yea / Nay either way; on a bill they mean support / oppose.
            listOf("Yea" to yes, "Nay" to no).forEach { (v, label) ->
                FilterChip(
                    selected = current == v,
                    onClick = {
                        MyPositions.set(key, if (current == v) null else v)
                        syncThenRefresh()
                    },
                    label = { Text(label) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Add my answers to the app-wide count (anonymous)",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Switch(checked = prefs.share, onCheckedChange = { AppTally.setSharing(it); syncThenRefresh() })
        }
        Text(
            when {
                prefs.share -> "Your answers are counted with no name, address or location attached, and stay on this device " +
                    "too. Turn this off to withdraw them from the count."
                current == null -> "Answer, and $usedFor. Kept only on this device unless you turn on the count above."
                else -> "Saved on this device only. Tap your answer again to clear it."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** How app users answered this roll call, once enough have that no one's answer can be guessed. */
@Composable
private fun AppTallyCard(key: String, refresh: Int, yes: String = "Yea", no: String = "Nay") {
    var tally by remember(key) { mutableStateOf<Tally?>(null) }
    var failed by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key, refresh) {
        try {
            tally = AppTally.get(key)
            failed = false
        } catch (e: Exception) {
            failed = true
        }
    }
    val t = tally
    InfoCard(title = "How app users answered") {
        when {
            t == null && failed -> Text("Couldn't load the app-wide count. Check your connection.", style = MaterialTheme.typography.bodyMedium)
            t == null -> LoadingBox("Loading the count…")
            t.yea != null && t.nay != null -> {
                val pct = { n: Int -> Format.decimals(n * 100.0 / t.total, 0) }
                Text(
                    "$yes ${pct(t.yea)}% · $no ${pct(t.nay)}%",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "${Format.commas(t.total.toLong())} people answered (${Format.commas(t.yea.toLong())} $yes, ${Format.commas(t.nay.toLong())} $no)",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            t.total == 0 -> Text("No one has added an answer yet.", style = MaterialTheme.typography.bodyMedium)
            else -> Text(
                "${t.total} ${if (t.total == 1) "person has" else "people have"} answered so far. The count appears once " +
                    "${t.minShown} have, so no one's answer can be guessed.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            "Not a poll: it counts only people who use this app and chose to add their answers.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The reader's answers set beside this member's votes: counts and the votes themselves, no score. */
@Composable
private fun AlignmentCard(record: MemberRecord, cast: List<CastVote>, titles: Map<String, BillSummary>, nav: CongressNav) {
    val positions by MyPositions.flow.collectAsState()
    val a = align(record.votes, positions, record.congress)
    var showAll by remember(record.id) { mutableStateOf(false) }
    InfoCard(title = "You and ${record.name}") {
        if (a.compared.isEmpty()) {
            Text(
                "Open any vote, say how you would have voted, and this card will show how often ${record.name} " +
                    "voted the same way. Your answers stay on this device.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = nav.recentVotes) { Text("See the latest votes") }
            return@InfoCard
        }
        a.sameShare?.let { share ->
            Text(
                "Voted the way you would have on ${a.same} of ${a.same + a.different} votes (${Format.decimals(share * 100, 0)}%)",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag("Same as you ${a.same}")
            Tag("Different ${a.different}")
            if (a.memberDidNotTakeSide > 0) Tag("Didn't vote Yea or Nay ${a.memberDidNotTakeSide}")
        }
        Text(
            "Based only on the ${a.compared.size} vote${if (a.compared.size == 1) "" else "s"} you answered. " +
                "Answer more for a fuller picture.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val summaries = cast.associate { MyPositions.key(it.vote.chamber, it.vote.session, it.vote.roll, record.congress) to it.summary }
        val rows = if (showAll) a.compared else a.compared.take(5)
        rows.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val key = MyPositions.key(c.vote.chamber, c.vote.session, c.vote.roll, record.congress)
            VoteRow(c.vote.chamber, summaries[key], c.vote.roll, titles) {
                nav.vote(c.vote.chamber, c.vote.session, c.vote.roll)
            }
            Text(
                "You: ${c.mine} · ${record.name.substringAfterLast(' ')}: ${voteLabel(c.vote.vote)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        if (!showAll && a.compared.size > 5) {
            OutlinedButton(onClick = { showAll = true }) { Text("Show all ${a.compared.size}") }
        }
    }
}

// --- The latest roll calls in both chambers ---

/** Newest first across both chambers. */
private suspend fun latestVotes(force: Boolean): List<BillVote> =
    CongressData.voteLists(force).flatMap { (key, votes) ->
        val (chamber, session) = key.split('/')
        votes.map { BillVote(chamber, session.toInt(), it) }
    }.sortedWith(compareByDescending<BillVote> { it.summary.date }.thenByDescending { it.summary.roll })

@Composable
fun LatestVotesCard(nav: CongressNav) {
    val (load, _) = rememberLoad(Unit, OFFLINE) { latestVotes(false) }
    val titles = rememberBillTitles()
    val positions by MyPositions.flow.collectAsState()
    InfoCard(title = "Latest votes in Congress") {
        when (val l = load) {
            Load.Loading -> LoadingBox("Loading votes…")
            is Load.Failed -> Text(l.message, style = MaterialTheme.typography.bodyMedium)
            is Load.Done -> {
                l.value.take(3).forEachIndexed { i, v ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val mine = positions[MyPositions.key(v.chamber, v.session, v.summary.roll)]
                    VoteRow(v.chamber, v.summary, v.summary.roll, titles, mine?.let { "You: $it" }) {
                        nav.vote(v.chamber, v.session, v.summary.roll)
                    }
                }
                Text(
                    "See all votes, and say how you would have voted ›",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.clickable(onClick = nav.recentVotes).padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
fun RecentVotesScreen(onBack: () -> Unit, nav: CongressNav) {
    val (load, retry) = rememberLoad(Unit, OFFLINE) { force -> latestVotes(force) }
    val titles = rememberBillTitles()
    val positions by MyPositions.flow.collectAsState()
    var chamber by remember { mutableStateOf<String?>(null) }
    var unanswered by remember { mutableStateOf(false) }
    Scaffold(topBar = { AppTopBar("Votes in Congress", onBack) }) { padding ->
        ReadingColumn(Modifier.padding(padding)) {
            when (val l = load) {
                Load.Loading -> LoadingBox("Loading votes…")
                is Load.Failed -> ErrorBox(l.message, retry)
                is Load.Done -> {
                    val shown = l.value.filter { v ->
                        (chamber == null || v.chamber == chamber) &&
                            (!unanswered || MyPositions.key(v.chamber, v.session, v.summary.roll) !in positions)
                    }
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Every roll-call vote this Congress, newest first. Open one to see how each member voted " +
                                        "and to say how you would have voted.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(null to "Both chambers", "senate" to "Senate", "house" to "House").forEach { (value, label) ->
                                        FilterChip(selected = chamber == value, onClick = { chamber = value }, label = { Text(label) })
                                    }
                                    FilterChip(selected = unanswered, onClick = { unanswered = !unanswered }, label = { Text("Not answered yet") })
                                }
                            }
                        }
                        items(shown, key = { "${it.chamber}/${it.session}/${it.summary.roll}" }) { v ->
                            val mine = positions[MyPositions.key(v.chamber, v.session, v.summary.roll)]
                            VoteRow(v.chamber, v.summary, v.summary.roll, titles, mine?.let { "You: $it" }) {
                                nav.vote(v.chamber, v.session, v.summary.roll)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        item { Spacer(Modifier.height(8.dp)); CongressSource() }
                    }
                }
            }
        }
    }
}

/**
 * A short, plain message to a member about a bill, in the reader's own position. The reader can edit it
 * before it goes anywhere; nothing is sent by the app itself.
 */
internal fun constituentMessage(member: Official, bill: String, billTitle: String?, place: String, supports: Boolean): String {
    val title = if (member.office.contains("Senator")) "Senator" else "Representative"
    val label = BillNames.label(bill)
    val named = billTitle?.let { "$label, $it" } ?: label
    return "Dear $title ${member.name},\n\n" +
        "As your constituent in $place, I ${if (supports) "support" else "oppose"} $named. " +
        "I ask you to vote ${if (supports) "yes" else "no"} on it.\n\n" +
        "Thank you."
}

/**
 * Lets the reader contact the members who represent them about this bill: call their Washington office, or
 * copy a ready message and open their official contact form. Members of Congress only take messages from
 * their own constituents, which is why it lists the reader's own members rather than the bill's sponsors.
 */
@Composable
private fun TellYourMembersCard(billId: String, bill: Bill, nav: CongressNav) {
    val location by LocationStore.location.collectAsState()
    val (delegation, _, _) = rememberDelegation(location)
    val positions by MyPositions.flow.collectAsState()
    val stance = positions[MyPositions.billKey(billId)]
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    var copiedFor by remember { mutableStateOf<String?>(null) }
    var forms by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(Unit) { forms = CongressData.contactForms() }
    InfoCard(title = "Tell your members") {
        val loc = location
        if (loc == null) {
            Text(
                "Set your location to see who represents you, and contact them about this bill in a tap.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = nav.setLocation) { Text("Set my location") }
            return@InfoCard
        }
        val d = delegation
        if (d == null) {
            LoadingBox("Finding your members…")
            return@InfoCard
        }
        val members = d.senators + listOfNotNull(d.representative)
        Text(
            when (stance) {
                "Yea" -> "Let them know you support ${BillNames.label(billId)}. Pick who to contact:"
                "Nay" -> "Let them know you oppose ${BillNames.label(billId)}. Pick who to contact:"
                else -> "Choose Support or Oppose above and a short message will be written for you. Or just call:"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        val place = "${loc.cityOrCounty}, ${loc.stateAbbr}"
        Text(
            "Your representative votes on bills in the House; your senators vote in the Senate. Calling? Give your name, " +
                "say you live in $place, and that you ${when (stance) { "Yea" -> "support"; "Nay" -> "oppose"; else -> "have a view on" }} " +
                "${BillNames.label(billId)}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        members.forEachIndexed { i, m ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(m.name, style = MaterialTheme.typography.titleSmall)
            Text(m.office, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                m.phone?.let { phone ->
                    OutlinedButton(onClick = { openSafely(uri, "tel:${phone.filter { it.isDigit() }}") }) { Text("Call") }
                }
                // A checked contact form if there is one; otherwise their website, labelled as such.
                val form = forms[m.id] ?: m.contactForm
                val page = form ?: m.website
                if (page != null && stance != null) {
                    Button(onClick = {
                        clipboard.setText(AnnotatedString(constituentMessage(m, billId, bill.title, place, stance == "Yea")))
                        copiedFor = m.id
                        openSafely(uri, page)
                    }) { Text(if (form != null) "Write" else "Website") }
                }
            }
            if (copiedFor == m.id) {
                Text(
                    if (forms[m.id] ?: m.contactForm != null) {
                        "Message copied. Paste it into the message box on their form, along with your name and address, " +
                            "which offices ask for to confirm you live in their district."
                    } else {
                        "Message copied. Their contact page couldn't be found, so this opened their website: look for " +
                            "\"Contact\", or call instead."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        if (stance != null) {
            Text(
                "What Write copies, addressed to each member by name. You can change it on their form before sending:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                constituentMessage(members.firstOrNull() ?: return@InfoCard, billId, bill.title, place, stance == "Yea"),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// --- One bill ---

@Composable
fun BillScreen(id: String, onBack: () -> Unit, nav: CongressNav) {
    val (load, retry) = rememberLoad(id, "Couldn't load this bill. Check your connection.") {
        CongressData.bill(id) to CongressData.votesOnBill(id)
    }
    var tallies by remember(id) { mutableStateOf<Map<String, Tally>>(emptyMap()) }
    var billTallyRefresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { AppTally.sync() }
    LaunchedEffect(load) {
        val votes = (load as? Load.Done)?.value?.second ?: return@LaunchedEffect
        tallies = try {
            AppTally.getMany(votes.map { MyPositions.key(it.chamber, it.session, it.summary.roll) })
        } catch (e: Exception) {
            emptyMap()
        }
    }
    val uri = LocalUriHandler.current
    Scaffold(topBar = { AppTopBar(BillNames.label(id), onBack) }) { padding ->
        ReadingColumn(Modifier.padding(padding)) {
            when (val l = load) {
                Load.Loading -> LoadingBox("Loading the bill…")
                is Load.Failed -> ErrorBox(l.message, retry)
                is Load.Done -> {
                    val (b, votes) = l.value
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            listOfNotNull(BillNames.label(id), b.origin?.let { "from the $it" }, b.introduced?.let { "introduced ${Format.date(it)}" })
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(b.title ?: BillNames.label(id), style = MaterialTheme.typography.headlineSmall)
                        b.policyArea?.let { Tag(it) }
                        b.laws.forEach { law ->
                            InfoCard { Text("Became ${law.type ?: "law"} ${law.number}", style = MaterialTheme.typography.titleMedium) }
                        }
                        b.latestAction?.let { a ->
                            InfoCard(title = "Latest action") {
                                a.date?.let { Text(Format.date(it), style = MaterialTheme.typography.labelLarge) }
                                a.text?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                        val viewKey = MyPositions.billKey(id)
                        YourViewCard(
                            viewKey, "Do you support this bill?", "Support", "Oppose",
                            usedFor = "a short message to your members will be written for you below",
                        ) { billTallyRefresh++ }
                        AppTallyCard(viewKey, billTallyRefresh, "Support", "Oppose")
                        TellYourMembersCard(id, b, nav)
                        InfoCard(title = if (b.sponsors.size == 1) "Sponsor" else "Sponsors") {
                            b.sponsors.forEach { s ->
                                Text(
                                    s.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (s.id != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    textDecoration = if (s.id != null) TextDecoration.Underline else null,
                                    modifier = Modifier.clickable(enabled = s.id != null) { s.id?.let(nav.official) }.padding(vertical = 4.dp),
                                )
                            }
                            b.cosponsorCount?.let {
                                Text(
                                    if (it == 0) "No cosponsors" else "$it cosponsor${if (it == 1) "" else "s"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        InfoCard(title = "Roll-call votes") {
                            if (votes.isEmpty()) {
                                Text(
                                    "No recorded votes on this bill. Most bills never reach one, and many pass by voice vote or unanimous consent.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            votes.forEachIndexed { i, v ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                VoteRow(v.chamber, v.summary, v.summary.roll, emptyMap(), showBill = false) {
                                    nav.vote(v.chamber, v.session, v.summary.roll)
                                }
                                tallies[MyPositions.key(v.chamber, v.session, v.summary.roll)]?.let { t ->
                                    Text(
                                        if (t.yea != null && t.nay != null) {
                                            "App users: Yea ${Format.decimals(t.yea * 100.0 / t.total, 0)}% · " +
                                                "Nay ${Format.decimals(t.nay * 100.0 / t.total, 0)}% (${Format.commas(t.total.toLong())} people)"
                                        } else {
                                            "App users: ${t.total} answered so far (shown at ${t.minShown})"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                        }
                        b.url?.let { link ->
                            Text(
                                "Read the bill and its full history on Congress.gov",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier.clickable { openSafely(uri, link) }.padding(vertical = 4.dp),
                            )
                        }
                        SourceLine("Congress.gov (Library of Congress)", b.url ?: CongressData.SOURCE_URL)
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}


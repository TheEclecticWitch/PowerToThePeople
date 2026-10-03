package com.theeclecticwitch.powertothepeople.congress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.theeclecticwitch.powertothepeople.civics.Civics
import com.theeclecticwitch.powertothepeople.officials.OfficialRow
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.location.LocationStore
import com.theeclecticwitch.powertothepeople.officials.rememberDelegation
import com.theeclecticwitch.powertothepeople.ui.AppTopBar
import com.theeclecticwitch.powertothepeople.ui.Format
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.LoadingBox
import com.theeclecticwitch.powertothepeople.ui.ReadingColumn

/**
 * The most recent roll call and how the reader's own members voted on it. No "how would you vote" here: a
 * one-line summary is not enough to decide on, so answering waits for the vote's own page.
 */
@Composable
fun LatestVoteCard(nav: CongressNav) {
    val (load, _) = rememberLoad(Unit, "Couldn't load the latest vote. Check your connection.") { latestVotes(false).firstOrNull() }
    val titles = rememberBillTitles()
    val location by LocationStore.location.collectAsState()
    val (delegation, _, _) = rememberDelegation(location)
    var detail by remember { mutableStateOf<VoteDetail?>(null) }
    val latest = (load as? Load.Done)?.value
    LaunchedEffect(latest) {
        detail = latest?.let { try { CongressData.vote(it.chamber, it.session, it.summary.roll) } catch (e: Exception) { null } }
    }
    InfoCard(title = "Latest vote", onClick = latest?.let { { nav.vote(it.chamber, it.session, it.summary.roll) } }) {
        when (val l = load) {
            Load.Loading -> LoadingBox("Loading…")
            is Load.Failed -> Text(l.message, style = MaterialTheme.typography.bodyMedium)
            is Load.Done -> {
                val v = l.value ?: return@InfoCard
                VoteRow(v.chamber, v.summary, v.summary.roll, titles) { nav.vote(v.chamber, v.session, v.summary.roll) }
                val mine = delegation?.let { d -> listOfNotNull(d.representative) + d.senators }.orEmpty()
                val positions = detail?.positions.orEmpty()
                val how = mine.mapNotNull { m -> positions.firstOrNull { it.id == m.id }?.let { "${Format.surname(m.name)}: ${voteLabel(it.vote)}" } }
                if (how.isNotEmpty()) {
                    Text("Your members: ${how.joinToString(" · ")}", style = MaterialTheme.typography.bodyLarge)
                }
                Text("See all votes ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

/** The Congress tab: Congress as a whole, then the Senate and the House each on their own tab. */
@Composable
fun CongressScreen(
    nav: CongressNav,
    onLegislation: () -> Unit,
    onDirectory: (chamber: String?) -> Unit,
    onSessions: () -> Unit,
    onLearn: () -> Unit,
    onOfficial: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val location by LocationStore.location.collectAsState()
    val (delegation, _, _) = rememberDelegation(location)
    Scaffold(topBar = { AppTopBar("Congress") }) { padding ->
        ReadingColumn(Modifier.padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                PrimaryTabRow(selectedTabIndex = tab) {
                    listOf("Congress", "Senate", "House").forEachIndexed { i, label ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                    }
                }
                val part = Civics.parts.first { it.id == listOf("congress", "senate", "house")[tab] }
                val chamber = listOf(null, "senate", "house")[tab]
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // What this body is for, briefly, with the full explanation a tap away.
                    InfoCard(title = part.title, onClick = onLearn) {
                        Text(part.role, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(part.about, style = MaterialTheme.typography.bodyMedium)
                        part.facts.take(2).forEach { (k, v) ->
                            Text("$k: $v", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("How it works ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    }
                    // The reader's own members in this chamber.
                    val mine = when (chamber) {
                        "senate" -> delegation?.senators.orEmpty()
                        "house" -> listOfNotNull(delegation?.representative)
                        else -> delegation?.let { it.senators + listOfNotNull(it.representative) }.orEmpty()
                    }
                    if (mine.isNotEmpty()) {
                        Text(
                            when (chamber) { "senate" -> "Your senators"; "house" -> "Your representative"; else -> "Your members of Congress" },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        mine.forEach { OfficialRow(it) { onOfficial(it.id) } }
                    }
                    LatestVotesCard(nav, chamber)
                    ChamberSessionCard(chamber, onSessions)
                    if (chamber == null) {
                        InfoCard(title = "Find legislation", onClick = onLegislation) {
                            Text(
                                "Any bill in Congress, any executive order, or a bill in your state legislature, by number or name.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text("Search ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    InfoCard(
                        title = when (chamber) { "senate" -> "All 100 senators"; "house" -> "Every representative"; else -> "All of Congress" },
                        onClick = { onDirectory(chamber) },
                    ) {
                        Text(
                            "Their votes, bills, committees and campaign money. Search by name or state.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text("Browse ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}

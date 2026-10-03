package com.theeclecticwitch.powertothepeople.alerts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import com.theeclecticwitch.powertothepeople.congress.Bill
import com.theeclecticwitch.powertothepeople.congress.BillTopics
import com.theeclecticwitch.powertothepeople.congress.BillNames
import com.theeclecticwitch.powertothepeople.congress.CongressData
import com.theeclecticwitch.powertothepeople.ui.Format
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.produceState
import com.theeclecticwitch.powertothepeople.congress.CongressNav
import com.theeclecticwitch.powertothepeople.location.LocationStore
import com.theeclecticwitch.powertothepeople.ui.AppTopBar
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.ReadingColumn
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Opens what an alert is about: its bill, or its roll call. */
fun openAlert(item: AlertItem, nav: CongressNav) {
    item.bill?.let { nav.bill(it); return }
    val parts = item.vote?.split('/') ?: return
    if (parts.size == 3) nav.vote(parts[0], parts[1].toIntOrNull() ?: return, parts[2].toIntOrNull() ?: return)
}

private fun whenSeen(at: String): String = try {
    val t = Instant.parse(at).toLocalDateTime(TimeZone.currentSystemDefault())
    "${t.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${t.day}"
} catch (e: Exception) {
    ""
}

@Composable
private fun SwitchRow(label: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun AlertsScreen(onBack: () -> Unit, nav: CongressNav, onLegislation: () -> Unit) {
    val prefs by Alerts.prefs.collectAsState()
    val history by Alerts.history.collectAsState()
    val location by LocationStore.location.collectAsState()
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    val askToNotify = rememberNotificationPermission { granted ->
        denied = !granted
        if (granted) Alerts.setNotify(true)
    }
    fun checkNow() {
        checking = true
        scope.launch {
            Alerts.check(notify = false)
            checking = false
        }
    }
    LaunchedEffect(Unit) { checkNow() }

    Scaffold(topBar = { AppTopBar("Alerts", onBack) }) { padding ->
        ReadingColumn(Modifier.padding(padding)) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    InfoCard(title = "What to tell you about") {
                        SwitchRow(
                            "How my members vote",
                            if (location == null) "Set your location first, so the app knows who your members are."
                            else "Each roll call your two senators and your representative vote on.",
                            prefs.myMembers,
                        ) { Alerts.setMyMembers(it) }
                        if (location == null) OutlinedButton(onClick = nav.setLocation) { Text("Set my location") }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text("Bills I follow", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "You'll hear when a bill you follow moves, comes up for a vote, is scheduled for the House floor or " +
                                "is taken up in a committee meeting. Follow a bill with the bell on its page.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        prefs.bills.forEach { bill ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    BillNames.label(bill),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f).clickable { nav.bill(bill) }.padding(vertical = 6.dp),
                                )
                                TextButton(onClick = { Alerts.unfollow(bill) }) { Text("Unfollow") }
                            }
                        }
                        if (prefs.bills.isEmpty()) OutlinedButton(onClick = onLegislation) { Text("Find a bill to follow") }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        TopicsSection(prefs, onFollowed = { checkNow() })
                    }
                }
                item {
                    InfoCard(title = "Notifications") {
                        if (Notifications.supported) {
                            SwitchRow(
                                "Notify me",
                                "The app checks every few hours, even when closed. On an iPhone, iOS decides when, so " +
                                    "alerts can come hours late.",
                                prefs.notify,
                            ) { on -> if (on) askToNotify() else Alerts.setNotify(false) }
                            if (denied) {
                                Text(
                                    "Notifications are turned off for this app in your phone's settings.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        } else {
                            Text("On a computer the app checks each time it opens, and what's new waits below.", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            "What you follow stays on this device. The checks only download the same public files everyone " +
                                "gets, so no one can tell what you follow.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { checkNow() }, enabled = !checking) { Text(if (checking) "Checking…" else "Check now") }
                        if (history.isNotEmpty()) TextButton(onClick = { Alerts.clearHistory() }) { Text("Clear") }
                    }
                }
                if (history.isEmpty()) {
                    item {
                        Text(
                            "Nothing yet. The first check only notes where things stand; from then on, anything new shows up here.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                items(history) { a ->
                    Column(
                        Modifier.fillMaxWidth().clickable { openAlert(a, nav) }.padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(whenSeen(a.at), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(a.title, style = MaterialTheme.typography.titleSmall)
                        Text(a.text, style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/**
 * Subjects to follow: the policy areas the Congressional Research Service assigns every bill, listed in full
 * and in alphabetical order, so none is put forward over another.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicsSection(prefs: AlertPrefs, onFollowed: () -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    val topics by produceState<BillTopics?>(null) { value = runCatching { CongressData.topics() }.getOrNull() }
    Text("Topics I follow", style = MaterialTheme.typography.bodyLarge)
    Text(
        "You'll hear when a bill on a subject you choose takes a step, such as a committee vote or passing the House " +
            "or Senate. Subjects are the ones the Library of Congress gives every bill.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (prefs.topics.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            prefs.topics.sorted().forEach { t ->
                InputChip(
                    selected = true,
                    onClick = { Alerts.unfollowTopic(t) },
                    label = { Text(t) },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Stop following $t", Modifier.size(16.dp)) },
                )
            }
        }
        SwitchRow(
            "Include new bills",
            "Also tell me when a bill is introduced or sent to a committee. Thousands are each year, and most go no further.",
            prefs.topicsIncludeNew,
        ) { Alerts.setTopicsIncludeNew(it) }
    }
    if (!choosing) {
        OutlinedButton(onClick = { choosing = true }) { Text(if (prefs.topics.isEmpty()) "Choose topics" else "Add or remove topics") }
    } else {
        val list = topics?.topics.orEmpty()
        if (topics == null) {
            Text("The list of topics isn't available right now. Try again later.", style = MaterialTheme.typography.bodyMedium)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { t ->
                val on = t.name in prefs.topics
                FilterChip(
                    selected = on,
                    onClick = {
                        if (on) Alerts.unfollowTopic(t.name) else { Alerts.followTopic(t.name); onFollowed() }
                    },
                    label = { Text("${t.name} (${Format.commas(t.bills.toLong())})") },
                )
            }
        }
        TextButton(onClick = { choosing = false }) { Text("Done") }
    }
}

/** The bell on a bill's page: follow it, or stop. */
@Composable
fun FollowBillButton(bill: String) {
    val prefs by Alerts.prefs.collectAsState()
    val following = bill in prefs.bills
    val scope = rememberCoroutineScope()
    TextButton(onClick = {
        if (following) {
            Alerts.unfollow(bill)
        } else {
            Alerts.follow(bill)
            // Note where it stands now, so only what happens next is reported.
            scope.launch { Alerts.check(notify = false) }
        }
    }) {
        Icon(if (following) Icons.Filled.Notifications else Icons.Outlined.Notifications, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(if (following) "Following" else "Follow")
    }
}

/** On Overview: the bills the reader follows, each with where it last stood. */
@Composable
fun FollowedBillsCard(nav: CongressNav, onAlerts: () -> Unit, onLegislation: () -> Unit) {
    val prefs by Alerts.prefs.collectAsState()
    val bills by produceState<Map<String, Bill?>>(emptyMap(), prefs.bills) {
        value = prefs.bills.associateWith { runCatching { CongressData.bill(it) }.getOrNull() }
    }
    InfoCard(title = "Bills I follow") {
        if (prefs.bills.isEmpty()) {
            Text("Tap the bell on any bill's page to follow it. It will show up here, and you can be alerted when it moves.", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Find legislation ›",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.clickable(onClick = onLegislation).padding(vertical = 4.dp),
            )
            if (prefs.topics.isNotEmpty()) LatelyInTopics(prefs, nav)
            return@InfoCard
        }
        prefs.bills.forEachIndexed { i, id ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val b = bills[id]
            Column(
                Modifier.fillMaxWidth().clickable { nav.bill(id) }.padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(BillNames.label(id), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                b?.title?.let { Text(it, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                b?.latestAction?.let { a ->
                    Text(
                        listOfNotNull(a.date?.let { Format.date(it) }, a.text).joinToString(": "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            "Alerts ›",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.clickable(onClick = onAlerts).padding(vertical = 4.dp),
        )
        if (prefs.topics.isNotEmpty()) LatelyInTopics(prefs, nav)
    }
}

/** Under the followed bills: recent steps by bills on the subjects the reader follows. */
@Composable
private fun LatelyInTopics(prefs: AlertPrefs, nav: CongressNav) {
    val topics by produceState<BillTopics?>(null) { value = runCatching { CongressData.topics() }.getOrNull() }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text("Lately in your topics", style = MaterialTheme.typography.titleSmall)
    val t = topics ?: return
    val moves = latelyInTopics(t.moves, prefs.topics, prefs.topicsIncludeNew)
    moves.forEach { m ->
        Column(
            Modifier.fillMaxWidth().clickable { nav.bill(m.bill) }.padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("${m.policyArea} · ${BillNames.label(m.bill)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            m.title?.let { Text(it, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            m.action?.let { a ->
                Text(
                    listOfNotNull(a.date?.let { Format.date(it) }, a.text).joinToString(": "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    val quiet = prefs.topics.filter { topic -> moves.none { it.policyArea == topic } }.sorted()
    if (quiet.isNotEmpty()) {
        Text(
            "Nothing in the last two weeks on ${quiet.joinToString(", ")}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

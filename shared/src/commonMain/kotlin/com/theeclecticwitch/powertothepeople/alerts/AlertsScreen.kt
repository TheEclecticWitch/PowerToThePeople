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
import com.theeclecticwitch.powertothepeople.congress.BillNames
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

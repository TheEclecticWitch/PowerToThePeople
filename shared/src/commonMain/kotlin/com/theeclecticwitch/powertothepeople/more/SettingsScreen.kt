package com.theeclecticwitch.powertothepeople.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.congress.AppTally
import com.theeclecticwitch.powertothepeople.congress.MyPositions
import com.theeclecticwitch.powertothepeople.location.LocationStore
import com.theeclecticwitch.powertothepeople.ui.AppTopBar
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.ReadingColumn
import kotlinx.coroutines.launch

/** The reader's own settings: where they live, and what (if anything) leaves the device. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onLocation: () -> Unit) {
    val location by LocationStore.location.collectAsState()
    val prefs by AppTally.prefsFlow.collectAsState()
    val answers by MyPositions.flow.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { AppTopBar("Settings", onBack) }) { padding ->
        ReadingColumn(Modifier.padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoCard(title = "Your location") {
                    Text(location?.matchedAddress ?: "Not set yet", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Used to find who represents you. It stays on this device; it was sent only once, to the U.S. " +
                            "Census Bureau, to look up your districts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onLocation) { Text(if (location == null) "Set my location" else "Change my location") }
                }
                InfoCard(title = "Your answers on votes and bills") {
                    Text(
                        "You've answered ${answers.size} vote${if (answers.size == 1) "" else "s"} and bill${if (answers.size == 1) "" else "s"}. " +
                            "They're kept on this device and used to show how often your members voted the way you would.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Add my answers to the app-wide count (anonymous)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = prefs.share, onCheckedChange = { on ->
                            AppTally.setSharing(on)
                            scope.launch { AppTally.sync() }
                        })
                    }
                    Text(
                        "When on, each answer is counted with no name, address or location attached. Turning it off " +
                            "withdraws your answers from the count.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (answers.isNotEmpty()) {
                        OutlinedButton(onClick = { confirmClear = true }) { Text("Clear all my answers") }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all your answers?") },
            text = { Text("Your ${answers.size} answers will be removed from this device, and from the app-wide count if you shared them. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    MyPositions.clearAll()
                    scope.launch { AppTally.sync() }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

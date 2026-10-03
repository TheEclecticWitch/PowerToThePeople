package com.theeclecticwitch.powertothepeople.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.debt.DebtCard
import com.theeclecticwitch.powertothepeople.doomsday.DoomsdayCard
import com.theeclecticwitch.powertothepeople.location.LocationStore
import com.theeclecticwitch.powertothepeople.officials.OfficialPhoto
import com.theeclecticwitch.powertothepeople.officials.rememberDelegation
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.LoadingBox
import com.theeclecticwitch.powertothepeople.ui.ReadingColumn
import com.theeclecticwitch.powertothepeople.ui.theme.CaslonFeatures
import com.theeclecticwitch.powertothepeople.ui.theme.LocalAppFonts

@Composable
fun HomeScreen(
    onDebt: () -> Unit,
    onDoomsday: () -> Unit,
    onConstitution: () -> Unit,
    onOfficials: () -> Unit,
    onOfficial: (String) -> Unit,
    onSetLocation: () -> Unit,
) {
    val location by LocationStore.location.collectAsState()
    val (delegation, _, _) = rememberDelegation(location)
    Scaffold { padding ->
        ReadingColumn(Modifier.padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                    Text(
                        "Power to the People",
                        style = MaterialTheme.typography.headlineLarge.copy(fontFamily = LocalAppFonts.current.caslonDisplay),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Your government, in plain view.",
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val loc = location
                if (loc == null) {
                    InfoCard(title = "Who represents you?") {
                        Text(
                            "Enter your address once to see your members of Congress, your districts, and how to reach them.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(onClick = onSetLocation) { Text("Set my location") }
                    }
                } else {
                    InfoCard(title = "Your representatives in Washington", onClick = onOfficials) {
                        Text("${loc.cityOrCounty}, ${loc.stateAbbr} · ${loc.congressionalDistrictLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val d = delegation
                        if (d == null) {
                            LoadingBox("Finding your representatives…")
                        } else {
                            (d.senators + listOfNotNull(d.representative)).forEach { o ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                    OfficialPhoto(o, 44)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(o.name, style = MaterialTheme.typography.titleSmall)
                                        Text(o.office, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        Text("All my officials ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    }
                }

                DebtCard(onOpen = onDebt)

                DoomsdayCard(onOpen = onDoomsday)

                InfoCard(title = "The Constitution", onClick = onConstitution) {
                    Text(
                        "We the People of the United States, in Order to form a more perfect Union…",
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = LocalAppFonts.current.caslon, fontFeatureSettings = CaslonFeatures),
                        fontStyle = FontStyle.Italic,
                    )
                    Text(
                        "The full text, all 27 amendments, and plain-English summaries. Works without a connection.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Read it ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

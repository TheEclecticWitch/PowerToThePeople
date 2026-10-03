package com.theeclecticwitch.powertothepeople.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.theeclecticwitch.powertothepeople.congress.CongressNav
import com.theeclecticwitch.powertothepeople.congress.LatestVoteCard
import com.theeclecticwitch.powertothepeople.congress.SessionCard
import com.theeclecticwitch.powertothepeople.constitution.Originals
import com.theeclecticwitch.powertothepeople.debt.DebtCard
import com.theeclecticwitch.powertothepeople.doomsday.DoomsdayCard
import com.theeclecticwitch.powertothepeople.location.LocationStore
import com.theeclecticwitch.powertothepeople.location.today
import com.theeclecticwitch.powertothepeople.officials.Official
import com.theeclecticwitch.powertothepeople.officials.OfficialPhoto
import com.theeclecticwitch.powertothepeople.officials.rememberDelegation
import com.theeclecticwitch.powertothepeople.officials.rememberStateDelegation
import com.theeclecticwitch.powertothepeople.ui.Format
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.ReadingColumn
import com.theeclecticwitch.powertothepeople.ui.SettingsButton
import com.theeclecticwitch.powertothepeople.ui.theme.LocalAppFonts

/**
 * Today: the founding documents beside the President and Vice President, then the reader's own officials,
 * then what Congress has been doing. Everything else is a tab or a tap away.
 */
@Composable
fun HomeScreen(
    onDebt: () -> Unit,
    onDoomsday: () -> Unit,
    onSessions: () -> Unit,
    onLegislation: () -> Unit,
    congressNav: CongressNav,
    onConstitution: () -> Unit,
    onBillOfRights: () -> Unit,
    onAllCongress: () -> Unit,
    onHowGovernment: () -> Unit,
    onOfficial: (String) -> Unit,
    onSetLocation: () -> Unit,
) {
    val location by LocationStore.location.collectAsState()
    val (delegation, _, _) = rememberDelegation(location)
    val (state, _, _) = rememberStateDelegation(location)
    val now = today()
    Scaffold { padding ->
        ReadingColumn(Modifier.padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Our Democratic Republic",
                            style = MaterialTheme.typography.headlineLarge.copy(fontFamily = LocalAppFonts.current.caslonDisplay),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        SettingsButton()
                    }
                    Text(
                        listOfNotNull(
                            "${now.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}, ${Format.date(now)}",
                            location?.let { "${it.cityOrCounty}, ${it.stateAbbr}" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // The founding documents beside the nation's two highest offices.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DocumentTile("Constitution", Originals.pages.first().thumbnail, Modifier.weight(1f), onConstitution)
                    PersonTile(delegation?.president, "President", Modifier.weight(1f), onOfficial)
                    PersonTile(delegation?.vicePresident, "Vice President", Modifier.weight(1f), onOfficial)
                    DocumentTile("Bill of Rights", Originals.pages[Originals.billOfRightsIndex].thumbnail, Modifier.weight(1f), onBillOfRights)
                }

                // Everyone else who represents the reader.
                if (location == null) {
                    InfoCard(title = "Who represents you?") {
                        Text(
                            "Enter your address once to see your members of Congress, your governor, your districts, and how to reach them.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(onClick = onSetLocation) { Text("Set my location") }
                    }
                } else {
                    val d = delegation
                    val governor = state?.executives?.firstOrNull { it.office.startsWith("Governor") }
                    // The Congress tiles stay together (senators, representative, all of Congress); the
                    // governor follows. Four to a row, like the row above.
                    val tiles: List<Official?> = d?.senators.orEmpty() + listOfNotNull(d?.representative) + null + listOfNotNull(governor)
                    tiles.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // null marks the All Congress tile.
                            row.forEach { o ->
                                if (o == null) AllCongressTile(Modifier.weight(1f), onAllCongress)
                                else PersonTile(o, roleOf(o), Modifier.weight(1f), onOfficial)
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }

                InfoCard(title = "How our government works", onClick = onHowGovernment) {
                    Text(
                        "What Congress, the Senate, the House, the President and the courts each do, who answers to whom, " +
                            "and how a bill becomes a law, in plain words.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("Learn ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                }

                InfoCard(title = "Find legislation", onClick = onLegislation) {
                    Text(
                        "Heard about a bill or an executive order in the news? Look it up by number or name: Congress, the President, and your state.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("Search ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                }

                LatestVoteCard(congressNav)

                SessionCard(onOpen = onSessions)

                DebtCard(onOpen = onDebt)

                DoomsdayCard(onOpen = onDoomsday)
            }
        }
    }
}

private fun roleOf(o: Official): String = when {
    o.office.contains("Senator") -> "Senator"
    o.office.startsWith("Governor") -> "Governor"
    o.office.contains("Delegate") -> "Delegate"
    else -> "Representative"
}

/** A face, a surname and a role; tap for their page. */
@Composable
private fun PersonTile(o: Official?, role: String, modifier: Modifier, onOfficial: (String) -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(enabled = o != null) { o?.let { onOfficial(it.id) } }.padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (o != null) OfficialPhoto(o, 52) else Spacer(Modifier.size(52.dp))
        Text(
            o?.name?.let(Format::surname) ?: "…",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            role,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Everyone in Congress, as a tile beside the reader's own members. */
@Composable
private fun AllCongressTile(modifier: Modifier, onOpen: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onOpen).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text("535", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text("All Congress", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(
            "Directory",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** One of the founding documents, shown as its own first page. */
@Composable
private fun DocumentTile(title: String, image: String, modifier: Modifier, onOpen: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onOpen).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AsyncImage(
            model = image,
            contentDescription = "The original $title",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 42.dp, height = 52.dp).clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Text(
            title,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = LocalAppFonts.current.caslon),
            fontStyle = FontStyle.Italic,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

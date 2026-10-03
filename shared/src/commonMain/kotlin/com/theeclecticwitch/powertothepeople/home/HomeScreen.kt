package com.theeclecticwitch.powertothepeople.home

import com.theeclecticwitch.powertothepeople.ui.theme.OnSurfaceColors
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.theeclecticwitch.powertothepeople.briefing.ThisWeekCard
import com.theeclecticwitch.powertothepeople.congress.CongressNav
import com.theeclecticwitch.powertothepeople.alerts.FollowedBillsCard
import com.theeclecticwitch.powertothepeople.congress.LatestVoteCard
import com.theeclecticwitch.powertothepeople.congress.ComingUpCard
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
import com.theeclecticwitch.powertothepeople.ui.FitText
import com.theeclecticwitch.powertothepeople.ui.ReadingColumn
import com.theeclecticwitch.powertothepeople.ui.SettingsButton
import com.theeclecticwitch.powertothepeople.ui.theme.LocalAppFonts
import com.theeclecticwitch.powertothepeople.voting.ElectionCard

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
    onVoting: () -> Unit,
    onHowGovernment: () -> Unit,
    onOfficial: (String) -> Unit,
    onSetLocation: () -> Unit,
    onAlerts: () -> Unit,
    onComingUp: () -> Unit,
    onElections: () -> Unit,
) {
    val location by LocationStore.location.collectAsState()
    val (delegation, _, _) = rememberDelegation(location)
    val (state, _, _) = rememberStateDelegation(location)
    val now = today()
    val header: @Composable () -> Unit = {
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
    }
    val setLocationCard: @Composable () -> Unit = {
        InfoCard(title = "Who represents you?") {
            Text(
                "Enter your address once to see your members of Congress, your governor, your districts, and how to reach them.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onSetLocation) { Text("Set my location") }
        }
    }
    val governor = state?.executives?.firstOrNull { it.office.startsWith("Governor") }
    val howItWorks: @Composable () -> Unit = {
        InfoCard(title = "How our government works", onClick = onHowGovernment) {
            Text(
                "What Congress, the Senate, the House, the President and the courts each do, who answers to whom, " +
                    "and how a bill becomes a law, in plain words.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("Learn ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        }
    }
    val findLegislation: @Composable () -> Unit = {
        InfoCard(title = "Find legislation", onClick = onLegislation) {
            Text(
                "Heard about a bill or an executive order in the news? Look it up by number or name: Congress, the President, and your state.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("Search ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        }
    }
    Scaffold { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            if (maxWidth >= 900.dp) {
                // A computer: quick references at a glance. The officials in one row, the cards in three columns.
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    header()
                    TileRow(gap = 8.dp, tiles = buildList {
                        add { m -> DocumentTile("Constitution", Originals.pages.first().thumbnail, m, onConstitution) }
                        add { m -> DocumentTile("Bill of Rights", Originals.pages[Originals.billOfRightsIndex].thumbnail, m, onBillOfRights) }
                        add { m -> PersonTile(delegation?.president, "President", m, onOfficial) }
                        add { m -> PersonTile(delegation?.vicePresident, "Vice President", m, onOfficial) }
                        if (location != null) {
                            delegation?.senators.orEmpty().forEach { o -> add { m -> PersonTile(o, "Senator", m, onOfficial) } }
                            delegation?.representative?.let { o -> add { m -> PersonTile(o, roleOf(o), m, onOfficial) } }
                            governor?.let { o -> add { m -> PersonTile(o, "Governor", m, onOfficial) } }
                        }
                        add { m -> AllCongressTile(m, onAllCongress) }
                    })
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (location == null) setLocationCard()
                            ElectionCard(onOpen = onVoting, onElections = onElections)
                            ComingUpCard(onComingUp)
                            FollowedBillsCard(congressNav, onAlerts = onAlerts, onLegislation = onLegislation)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            findLegislation()
                            ThisWeekCard()
                            LatestVoteCard(congressNav)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            SessionCard(onOpen = onSessions)
                            DebtCard(onOpen = onDebt)
                            DoomsdayCard(onOpen = onDoomsday)
                            howItWorks()
                        }
                    }
                }
            } else {
                ReadingColumn {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        header()

                        // The founding documents beside the nation's two highest offices.
                        TileRow(
                            listOf(
                                { m -> DocumentTile("Constitution", Originals.pages.first().thumbnail, m, onConstitution) },
                                { m -> PersonTile(delegation?.president, "President", m, onOfficial) },
                                { m -> PersonTile(delegation?.vicePresident, "Vice President", m, onOfficial) },
                                { m -> DocumentTile("Bill of Rights", Originals.pages[Originals.billOfRightsIndex].thumbnail, m, onBillOfRights) },
                            ),
                        )

                        // Everyone else who represents the reader.
                        if (location == null) {
                            setLocationCard()
                        } else {
                            val d = delegation
                            // The Congress tiles stay together (senators, representative, all of Congress); the
                            // governor follows. Four to a row, like the row above.
                            val tiles: List<Official?> = d?.senators.orEmpty() + listOfNotNull(d?.representative) + null + listOfNotNull(governor)
                            tiles.chunked(4).forEach { row ->
                                // null marks the All Congress tile.
                                TileRow(
                                    row.map { o ->
                                        { m: Modifier -> if (o == null) AllCongressTile(m, onAllCongress) else PersonTile(o, roleOf(o), m, onOfficial) }
                                    },
                                    columns = 4,
                                )
                            }
                        }

                        ElectionCard(onOpen = onVoting, onElections = onElections)
                        howItWorks()
                        findLegislation()
                        ThisWeekCard()
                        LatestVoteCard(congressNav)
                        SessionCard(onOpen = onSessions)
                        DebtCard(onOpen = onDebt)
                        DoomsdayCard(onOpen = onDoomsday)
                        FollowedBillsCard(congressNav, onAlerts = onAlerts, onLegislation = onLegislation)
                    }
                }
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

/**
 * Tiles side by side, each as tall as the tallest, so a role that takes two lines ("Vice President" on a narrow
 * phone) doesn't leave its neighbors short. [columns] keeps a part-filled row's tiles the same width as a full one's.
 * (A Row with IntrinsicSize.Min can't do this: the tiles are built on BoxWithConstraints.)
 */
@Composable
private fun TileRow(tiles: List<@Composable (Modifier) -> Unit>, columns: Int = tiles.size, gap: Dp = 6.dp) {
    SubcomposeLayout { constraints ->
        val gapPx = gap.roundToPx()
        val width = ((constraints.maxWidth - gapPx * (columns - 1)) / columns).coerceAtLeast(0)
        val natural = subcompose("measure") { tiles.forEach { it(Modifier) } }
            .map { it.measure(Constraints(minWidth = width, maxWidth = width)) }
        val height = natural.maxOfOrNull { it.height } ?: 0
        val placeables = subcompose("place") { tiles.forEach { it(Modifier) } }
            .map { it.measure(Constraints.fixed(width, height)) }
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { i, p -> p.placeRelative(i * (width + gapPx), 0) }
        }
    }
}

/** A face, a surname and a role; tap for their page. */
@Composable
private fun PersonTile(o: Official?, role: String, modifier: Modifier, onOfficial: (String) -> Unit) {
    OnSurfaceColors {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(enabled = o != null) { o?.let { onOfficial(it.id) } }.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // As large as the tile allows: people recognize faces before names.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val side = maxWidth.value.toInt()
            if (o != null) OfficialPhoto(o, side) else Spacer(Modifier.size(maxWidth))
        }
        FitText(o?.name?.let(Format::surname) ?: "…", MaterialTheme.typography.labelLarge)
        // At large text sizes "Vice President" takes two lines rather than shrinking out of reach.
        FitText(role, MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}
}

/** Everyone in Congress, as a tile beside the reader's own members. */
@Composable
private fun AllCongressTile(modifier: Modifier, onOpen: () -> Unit) {
    OnSurfaceColors {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onOpen).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text("535", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        FitText("All Congress", MaterialTheme.typography.labelLarge)
        FitText("Directory", MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
}

/** One of the founding documents, shown as its own first page. */
@Composable
private fun DocumentTile(title: String, image: String, modifier: Modifier, onOpen: () -> Unit) {
    OnSurfaceColors {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onOpen).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        AsyncImage(
            model = image,
            contentDescription = "The original $title",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth(0.8f).aspectRatio(0.8f).clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        // A single word stays whole on one line; "Bill of Rights" may take two.
        FitText(
            title,
            MaterialTheme.typography.labelLarge.copy(fontFamily = LocalAppFonts.current.caslon, fontStyle = FontStyle.Italic),
            maxLines = if (' ' in title) 2 else 1,
        )
    }
}
}

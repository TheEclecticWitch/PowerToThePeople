package com.theeclecticwitch.powertothepeople.census

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.location.LocationStore
import com.theeclecticwitch.powertothepeople.officials.StateNames
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import com.theeclecticwitch.powertothepeople.ui.AppTopBar
import com.theeclecticwitch.powertothepeople.ui.CardPage
import com.theeclecticwitch.powertothepeople.ui.ErrorBox
import com.theeclecticwitch.powertothepeople.ui.Format
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.LoadingBox
import com.theeclecticwitch.powertothepeople.ui.PageColumn
import com.theeclecticwitch.powertothepeople.ui.SourceLine
import com.theeclecticwitch.powertothepeople.ui.fullWidth
import kotlinx.serialization.Serializable

// --- The hand-copied tables (tools/gather/census_data), as published. ---

@Serializable
data class TableSource(val name: String, val url: String, val table: String? = null, val page: String? = null) {
    /** The page the table is published on, or the file itself. */
    val link: String get() = page ?: url
}

@Serializable
data class StateEstimate(val state: String, val estimate: Long? = null)

@Serializable
data class UnauthorizedEstimates(
    val source: TableSource,
    val asOf: String? = null,
    val definition: String? = null,
    val us: Long? = null,
    /** The country's estimate in earlier years, keyed by year. */
    val usHistory: Map<String, Long> = emptyMap(),
    val states: List<StateEstimate> = emptyList(),
    val notes: List<String> = emptyList(),
)

@Serializable
data class GovCounts(
    val state: String? = null,
    val county: Int? = null,
    val municipal: Int? = null,
    val township: Int? = null,
    val special: Int? = null,
    val school: Int? = null,
    val total: Int? = null,
)

@Serializable
data class LocalGovernments(
    val source: TableSource,
    val asOf: String? = null,
    val us: GovCounts? = null,
    val states: List<GovCounts> = emptyList(),
    val counties: Map<String, GovCounts> = emptyMap(),
)

// --- Figures as words. ---

private fun pct(part: Long?, whole: Long?): Double? =
    if (part == null || whole == null || whole == 0L) null else part * 100.0 / whole

fun percent(value: Double?): String = value?.let { Format.decimals(it, 1) + "%" } ?: "not available"

fun wholeDollars(value: Long): String = "$" + Format.commas(value)

private fun count(value: Long?): String = value?.let { Format.commas(it) } ?: "not available"


/** Chart colors: the validated categorical slots 1-3 (blue, orange, aqua), stepped for light and dark. */
@Composable
private fun seriesColors(): List<Color> =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        listOf(Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70))
    } else {
        listOf(Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A))
    }

// --- The screen. ---

/**
 * The people in each of the reader's areas, from the Census Bureau: who lives there, citizenship and
 * where people were born, income, homes, schooling, health insurance, languages, and how many local
 * governments there are.
 */
@Composable
fun CommunityScreen(onBack: () -> Unit, onSetLocation: () -> Unit, onSeats: () -> Unit) {
    val location by LocationStore.location.collectAsState()
    var attempt by remember { mutableIntStateOf(0) }
    var figures by remember { mutableStateOf<CommunityFigures?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var chosen by remember { mutableIntStateOf(0) }
    /** A place the reader picked, as (state, area key); null shows where they live. */
    var elsewhere by remember { mutableStateOf<Pair<String, String>?>(null) }
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(location?.enteredAddress, elsewhere, attempt) {
        val loc = location
        val other = elsewhere
        error = null
        figures = null
        chosen = 0
        try {
            figures = when {
                other != null -> CensusData.elsewhere(other.first, StateNames.of(other.first), other.second)
                loc != null -> CensusData.community(loc)
                else -> null
            }
        } catch (e: Exception) {
            error = "The Census figures couldn't be loaded. Check your connection and try again."
        }
    }
    if (picking) {
        PlacePicker(
            onBack = { picking = false },
            onPicked = { state, key -> elsewhere = state to key; picking = false },
        )
        return
    }
    Scaffold(topBar = { AppTopBar(if (elsewhere != null) "Another Place" else "Your Community", onBack) }) { padding ->
        PageColumn(Modifier.padding(padding)) {
            val loc = location
            val f = figures
            when {
                loc == null && elsewhere == null -> CardPage {
                    InfoCard(title = "Your community by the numbers") {
                        Text(
                            "Set your location to see the Census Bureau's figures for your town, county, districts and state, " +
                                "or look at any place in the country.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        OutlinedButton(onClick = onSetLocation) { Text("Set my location") }
                        OutlinedButton(onClick = { picking = true }) { Text("Choose a place") }
                    }
                }
                error != null && f == null -> ErrorBox(error!!) { attempt++ }
                f == null -> LoadingBox("Loading the Census figures…")
                else -> {
                    val area = f.areas.getOrNull(chosen) ?: f.areas.first()
                    CardPage(spacing = 12.dp) {
                        AreaPicker(
                            f.areas, chosen, { chosen = it },
                            onOther = { picking = true },
                            onMine = if (f.elsewhere && loc != null) ({ elsewhere = null }) else null,
                        )
                        CommunityCards(f, area, onSeats)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AreaPicker(areas: List<Area>, chosen: Int, onPick: (Int) -> Unit, onOther: () -> Unit, onMine: (() -> Unit)?) {
    Column(Modifier.fullWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Show the figures for", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            areas.forEachIndexed { i, a ->
                FilterChip(selected = i == chosen, onClick = { onPick(i) }, label = { Text(a.label) })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOther) { Text("Choose another place") }
            onMine?.let { OutlinedButton(onClick = it) { Text("Back to where I live") } }
        }
    }
}

/**
 * Any state, then any of its districts, counties, towns or townships. The lists are long (Pennsylvania
 * has over 2,500 townships), so there is a search box and only matching names are shown.
 */
/** How the place lists are ordered. */
private enum class PlaceOrder(val label: String) { Name("A to Z"), MostBornHere("Most born in U.S."), FewestBornHere("Fewest born in U.S.") }

private fun <T> List<T>.ordered(order: PlaceOrder, pct: (T) -> Double?): List<T> = when (order) {
    PlaceOrder.Name -> this
    // Places with no figure go last either way.
    PlaceOrder.MostBornHere -> sortedWith(compareBy<T> { pct(it) == null }.thenByDescending { pct(it) })
    PlaceOrder.FewestBornHere -> sortedWith(compareBy<T> { pct(it) == null }.thenBy { pct(it) })
}

/** The three ways to order a list. They wrap onto a second line on a narrow phone rather than run off the edge. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderChips(order: PlaceOrder, onOrder: (PlaceOrder) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlaceOrder.entries.forEach { o -> FilterChip(selected = o == order, onClick = { onOrder(o) }, label = { Text(o.label) }) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlacePicker(onBack: () -> Unit, onPicked: (String, String) -> Unit) {
    var order by remember { mutableStateOf(PlaceOrder.Name) }
    var state by remember { mutableStateOf<String?>(null) }
    var choices by remember { mutableStateOf<Map<AreaKind, List<AreaChoice>>?>(null) }
    var kind by remember { mutableStateOf<AreaKind?>(null) }
    var search by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val statesBorn by produceState(emptyMap<String, Double>()) { value = CensusData.stateBornHere() }
    var stateBorn by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(state, attempt) {
        val s = state ?: return@LaunchedEffect
        choices = null
        stateBorn = CensusData.stateBornHere(s)
        failed = false
        try {
            choices = CensusData.choices(s).also { kind = it.keys.firstOrNull() }
        } catch (e: Exception) {
            failed = true
        }
    }
    val back = { if (state != null) { state = null; search = "" } else onBack() }
    Scaffold(topBar = { AppTopBar(state?.let { StateNames.of(it) } ?: "Choose a State", back) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val s = state
            val c = choices
            when {
                s == null -> LazyColumn(Modifier.fillMaxSize()) {
                    item { BornHereNote() }
                    item { OrderChips(order) { order = it } }
                    items(StateNames.all.filter { it.first in STATE_FIPS }.ordered(order) { statesBorn[it.first] }) { (abbr, name) ->
                        PickRow(name, statesBorn[abbr]) { state = abbr }
                    }
                }
                failed -> ErrorBox("This state's figures couldn't be loaded. Check your connection and try again.") { attempt++ }
                c == null -> LoadingBox()
                else -> {
                    PickRow("All of ${StateNames.of(s)}", stateBorn) { onPicked(s, "state") }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        c.keys.forEach { k -> FilterChip(selected = k == kind, onClick = { kind = k; search = "" }, label = { Text(k.plural) }) }
                    }
                    OutlinedTextField(
                        search, { search = it },
                        label = { Text("Search by name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OrderChips(order) { order = it }
                    val list = c[kind].orEmpty().filter { search.isBlank() || it.name.contains(search.trim(), ignoreCase = true) }
                        .ordered(order) { it.bornHerePct }
                    LazyColumn(Modifier.fillMaxSize()) {
                        item { BornHereNote() }
                        items(list, key = { it.key }) { choice -> PickRow(choice.name, choice.bornHerePct) { onPicked(s, choice.key) } }
                        if (list.isEmpty()) item { Note("Nothing by that name here.") }
                    }
                }
            }
        }
    }
}

/** A place to pick, with the share of its people born in the U.S. beside it. */
@Composable
private fun PickRow(text: String, bornHere: Double? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
            bornHere?.let {
                Text("${percent(it)} born in U.S.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun BornHereNote() {
    Text(
        "Beside each place: the share of its people born in the U.S. (counting Puerto Rico and the U.S. territories), " +
            "from the Census Bureau's latest American Community Survey.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun CommunityCards(f: CommunityFigures, area: Area, onSeats: () -> Unit) {
    val a = area.figures
    val state = f.state.takeIf { area.kind != AreaKind.State && area.kind != AreaKind.Country }
    val us = f.us.takeIf { area.kind != AreaKind.Country }
    val sourceDetail = "${f.vintage - 4}-${f.vintage} survey"

    /** "State 12.1% · U.S. 14.0%", for any share worked out the same way. */
    @Composable
    fun Compare(share: (AreaFigures) -> Double?) {
        val parts = listOfNotNull(
            state?.let { s -> share(s)?.let { "${f.stateName} ${percent(it)}" } },
            us?.let { u -> share(u)?.let { "U.S. ${percent(it)}" } },
        )
        if (parts.isNotEmpty()) Note(parts.joinToString(" · "))
    }

    InfoCard(title = "People") {
        Text(area.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Stat("Population", count(a.pop))
        Stat("Median age", a.age?.let { Format.decimals(it, 1) + " years" } ?: "not available")
        Stat("Veterans", "${count(a.veterans)} (${percent(pct(a.veterans, a.adults18))} of adults)")
        Compare { pct(it.veterans, it.adults18) }
        if (area.kind == AreaKind.State) {
            TextButton(onClick = onSeats) { Text("${f.stateName}'s seats in the House ›") }
        }
        SourceLine("U.S. Census Bureau, American Community Survey", f.source.url, sourceDetail)
    }

    CitizenshipCard(a, state, us, f)
    HistoryCard(area, f)

    if (a.countries.isNotEmpty()) {
        InfoCard(title = "Where people born in other countries were born") {
            val foreign = a.foreignBorn
            a.countries.forEach { (country, n) ->
                Stat(country, "${Format.commas(n)} (${percent(pct(n, foreign))})")
            }
            Note("Share of everyone here who was born outside the United States. The ten most common countries.")
            SourceLine("U.S. Census Bureau, table B05006", f.source.url, sourceDetail)
        }
    }

    InfoCard(title = "Money") {
        Stat("Median household income", a.income?.let { wholeDollars(it) } ?: "not available")
        val incomes = listOfNotNull(
            state?.income?.let { "${f.stateName} ${wholeDollars(it)}" },
            us?.income?.let { "U.S. ${wholeDollars(it)}" },
        )
        if (incomes.isNotEmpty()) Note(incomes.joinToString(" · "))
        Stat("Living below the poverty line", percent(pct(a.poverty, a.povertyTotal)))
        Compare { pct(it.poverty, it.povertyTotal) }
        Note("Half of households earn more than the median and half earn less. Dollars are adjusted to ${f.vintage} values.")
    }

    InfoCard(title = "Homes") {
        Stat("Owned by the people living in them", percent(pct(a.owned, a.homes)))
        Compare { pct(it.owned, it.homes) }
        Stat("Rented", percent(pct(a.rented, a.homes)))
    }

    InfoCard(title = "School and health insurance") {
        Stat("Adults 25 and older with a bachelor's degree or more", percent(pct(a.bachelors, a.adults25)))
        Compare { pct(it.bachelors, it.adults25) }
        Stat("People without health insurance", percent(pct(a.uninsured, a.insuranceTotal)))
        Compare { pct(it.uninsured, it.insuranceTotal) }
    }

    InfoCard(title = "Languages spoken at home") {
        val other = if (a.speakers != null && a.englishOnly != null && a.spanish != null) a.speakers - a.englishOnly - a.spanish else null
        Stat("English only", percent(pct(a.englishOnly, a.speakers)))
        Compare { pct(it.englishOnly, it.speakers) }
        Stat("Spanish", percent(pct(a.spanish, a.speakers)))
        Stat("Another language", percent(pct(other, a.speakers)))
        Note("People 5 and older. Many who speak another language at home also speak English.")
    }

    LocalGovernmentsCard(area, f)

    InfoCard(title = "About these figures") {
        Text(
            "These come from the Census Bureau's American Community Survey, which asks about 3.5 million households a " +
                "year. The figures here pool the answers from ${f.vintage - 4} to ${f.vintage}, so they describe those years, " +
                "not today. They are estimates: the smaller the area, the wider the margin of error.",
            style = MaterialTheme.typography.bodyMedium,
        )
        SourceLine(f.source.name, f.source.url)
    }
}

@Composable
private fun CitizenshipCard(
    a: AreaFigures,
    state: AreaFigures?,
    us: AreaFigures?,
    f: CommunityFigures,
) {
    val estimates by produceState<UnauthorizedEstimates?>(null) { value = CensusData.table("unauthorized-estimates.json") }
    InfoCard(title = "Citizenship") {
        val total = a.citTotal
        val parts = listOf(
            "Born U.S. citizens" to a.bornCitizens,
            "Naturalized citizens" to a.naturalized,
            "Not U.S. citizens" to a.notCitizen,
        )
        ShareBar(parts.map { it.second ?: 0L })
        val colors = seriesColors()
        parts.forEachIndexed { i, (label, n) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp)) { Canvas(Modifier.fillMaxSize()) { drawRoundRect(colors[i], cornerRadius = CornerRadius(3f)) } }
                Spacer(Modifier.size(8.dp))
                Stat(label, "${count(n)} (${percent(pct(n, total))})")
            }
        }
        val compare = listOfNotNull(
            state?.let { "${f.stateName}: ${percent(pct(it.notCitizen, it.citTotal))} not citizens" },
            us?.let { "U.S.: ${percent(pct(it.notCitizen, it.citTotal))}" },
        )
        if (compare.isNotEmpty()) Note(compare.joinToString(" · "))
        Note(
            "Born U.S. citizens includes people born in the 50 states, D.C., Puerto Rico or a U.S. territory, and people " +
                "born abroad to an American parent. Naturalized citizens were born citizens of another country and " +
                "became Americans later.",
        )
        Text(
            "\"Not U.S. citizens\" counts everyone who isn't a citizen, whatever their legal status: green-card holders, " +
                "people here on work or student visas, refugees, and people here without permission. The Census Bureau " +
                "doesn't ask about legal status.",
            style = MaterialTheme.typography.bodyMedium,
        )
        SourceLine("U.S. Census Bureau, table B05001", f.source.url, "${f.vintage - 4}-${f.vintage} survey")

        estimates?.let { e ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Immigrants here without legal permission", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            val mine = e.states.firstOrNull { it.state == f.stateAbbr }?.estimate
            val asOf = e.asOf?.let { " as of ${Format.date("$it-01").ifBlank { it }}" } ?: ""
            e.us?.let { Stat("United States$asOf", "about ${Format.commas(it)}") }
            if (e.usHistory.size > 1) {
                Note("Earlier estimates for the country: " + e.usHistory.entries.sortedBy { it.key }
                    .joinToString(", ") { (y, n) -> "$y about ${Format.commas(n)}" } + ". Homeland Security warns these years aren't fully comparable.")
            }
            if (mine != null) {
                Stat(f.stateName, "about ${Format.commas(mine)}")
            } else {
                Note("The estimate lists only the states with the most; ${f.stateName} isn't one of them.")
            }
            Note(
                "An estimate by the Department of Homeland Security, not a count: no survey can count people living " +
                    "here without permission directly. It is made for the country and the largest states only, so there " +
                    "is no figure for a county, town or district.",
            )
            e.definition?.let { Note("Homeland Security's definition: $it") }
            SourceLine("U.S. Department of Homeland Security, Office of Homeland Security Statistics", e.source.link)
        }
    }
}

/** One bar split into shares, with a 2 dp gap between them. */
@Composable
private fun ShareBar(values: List<Long>) {
    val total = values.sum().takeIf { it > 0 } ?: return
    val colors = seriesColors()
    Canvas(Modifier.fillMaxWidth().height(18.dp)) {
        val gap = 2.dp.toPx()
        var x = 0f
        val shown = values.count { it > 0 }
        val room = size.width - gap * (shown - 1).coerceAtLeast(0)
        values.forEachIndexed { i, v ->
            if (v <= 0) return@forEachIndexed
            val w = room * v / total
            drawRoundRect(colors[i], Offset(x, 0f), Size(w, size.height), CornerRadius(4.dp.toPx()))
            x += w + gap
        }
    }
}

/**
 * Immigrants as a share of everyone, over the years: the census every ten years from 1850 to 2000,
 * then the yearly survey. For a district or town, whose lines are redrawn or too small for the yearly
 * survey, it shows the county's or the state's.
 */
private class ShareOverTime(val place: String, val points: List<Pair<Int, Double>>, val county: Boolean, val fiveYear: Boolean)

@Composable
private fun HistoryCard(area: Area, f: CommunityFigures) {
    val history by produceState<ShareOverTime?>(null, area.key, f.stateAbbr) {
        val older = CensusData.foreignBornHistory()
        fun shares(recent: CitizenshipHistory?) = recent?.years.orEmpty().map { it.year to it.foreignBorn * 100.0 / it.population }
        val county = area.countyCode?.let { CensusData.citizenshipYears(f.stateAbbr, it) }
            ?.takeIf { it.years.size >= 2 }
        value = when {
            area.kind == AreaKind.Country -> ShareOverTime(
                "the United States",
                older?.us.orEmpty().mapNotNull { y -> y.pct?.let { y.year to it } } + shares(CensusData.citizenshipYears(null)),
                county = false, fiveYear = false,
            )
            county != null -> ShareOverTime(area.countyName ?: "the county", shares(county), county = true, fiveYear = county.fiveYear)
            else -> ShareOverTime(
                f.stateName,
                older?.states?.get(f.stateAbbr).orEmpty().mapNotNull { y -> y.pct?.let { y.year to it } } +
                    shares(CensusData.citizenshipYears(f.stateAbbr)),
                county = false, fiveYear = false,
            )
        }?.takeIf { it.points.size >= 2 }?.let { ShareOverTime(it.place, it.points.sortedBy { p -> p.first }, it.county, it.fiveYear) }
    }
    val h = history ?: return
    val place = h.place
    val points = h.points
    InfoCard(title = "Immigrants over the years") {
        Text("People born in another country, as a share of everyone in $place", style = MaterialTheme.typography.bodyMedium)
        if (area.kind != AreaKind.Country && !place.equals(area.label, ignoreCase = true)) {
            Note("Shown for $place: district lines change and towns are too small for yearly figures.")
        }
        var picked by remember(place) { mutableStateOf<Int?>(null) }
        LineChart(points, picked) { picked = if (picked == it) null else it }
        val high = points.maxBy { it.second }
        val low = points.minBy { it.second }
        Note("Highest: ${percent(high.second)} in ${high.first} · Lowest: ${percent(low.second)} in ${low.first}")
        var showAll by remember(place) { mutableStateOf(false) }
        val rows = if (showAll) points.reversed() else points.reversed().take(6)
        Note("Tap a year, or the chart, to mark it on the chart.")
        rows.forEach { (year, p) ->
            val on = year == picked
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { picked = if (on) null else year }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Stat("$year", "${percent(p)} immigrants · ${percent(100 - p)} born citizens")
            }
        }
        if (points.size > 6) {
            TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Show fewer years" else "Show every year") }
        }
        Note(
            "Immigrants here means everyone born in another country to non-American parents: naturalized citizens and " +
                "non-citizens alike. " + when {
                    h.fiveYear -> "2000 is from the census. After that the county is too small for a figure from one " +
                        "year's survey alone, so each year's figure pools the five years ending then (2009 is 2005-2009)."
                    h.county -> "2000 is from the census; from 2005 on, from the yearly survey (none for 2020, when the " +
                        "pandemic cut the survey short). The yearly figures come from a sample of households, so in a " +
                        "smaller county they can swing from one year to the next. The Census Bureau's county figures " +
                        "from before 2000 are only in printed reports."
                    else -> "Every ten years from 1850 to 2000 the figure is from the census; from 2005 on, from the " +
                        "yearly survey (none for 2020, when the pandemic cut the survey short)."
                },
        )
        SourceLine("U.S. Census Bureau", "https://www.census.gov/topics/population/foreign-born.html")
    }
}

/**
 * One line, years along the bottom at their true spacing, with the first and last values labeled.
 * Tapping picks the nearest year, which is marked with a guide line and named above the chart.
 */
@Composable
private fun LineChart(points: List<Pair<Int, Double>>, picked: Int?, onPick: (Int) -> Unit) {
    val line = seriesColors()[0]
    val grid = MaterialTheme.colorScheme.outlineVariant
    val guide = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val first = points.first()
    val last = points.last()
    val top = (points.maxOf { it.second } * 1.15).coerceAtLeast(5.0)
    val chosen = points.firstOrNull { it.first == picked }
    val pick by rememberUpdatedState(onPick)
    Column {
        if (chosen != null) {
            Text(
                "${chosen.first}: ${percent(chosen.second)} immigrants",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Text("${percent(first.second)}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text("${percent(last.second)}", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
        Canvas(
            Modifier.fillMaxWidth().height(140.dp).pointerInput(points) {
                detectTapGestures { tap ->
                    val span = (last.first - first.first).coerceAtLeast(1)
                    val year = first.first + tap.x / size.width * span
                    pick(points.minBy { kotlin.math.abs(it.first - year) }.first)
                }
            },
        ) {
            val minYear = first.first
            val span = (last.first - minYear).coerceAtLeast(1)
            fun at(p: Pair<Int, Double>) = Offset(
                (p.first - minYear) / span.toFloat() * size.width,
                size.height - (p.second / top * size.height).toFloat(),
            )
            listOf(0.0, top / 2, top).forEach { v ->
                val y = size.height - (v / top * size.height).toFloat()
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            val path = Path()
            points.forEachIndexed { i, p -> at(p).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
            drawPath(path, line, style = Stroke(width = 2.dp.toPx()))
            listOf(first, last).forEach {
                drawCircle(surface, radius = 6.dp.toPx(), center = at(it))
                drawCircle(line, radius = 4.dp.toPx(), center = at(it))
            }
            chosen?.let {
                val c = at(it)
                drawLine(guide, Offset(c.x, 0f), Offset(c.x, size.height), strokeWidth = 1.dp.toPx())
                drawCircle(surface, radius = 8.dp.toPx(), center = c)
                drawCircle(line, radius = 6.dp.toPx(), center = c)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text("${first.first}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text("${last.first}", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LocalGovernmentsCard(area: Area, f: CommunityFigures) {
    val data by produceState<LocalGovernments?>(null) { value = CensusData.table("local-governments.json") }
    val d = data ?: return
    val countyCode = area.countyCode?.let { (STATE_FIPS[f.stateAbbr] ?: "") + it }
    val (where, counts) = when {
        area.kind == AreaKind.Country -> "the United States" to d.us
        area.kind == AreaKind.County && countyCode != null && d.counties[countyCode] != null ->
            (area.countyName ?: area.label) to d.counties[countyCode]
        else -> f.stateName to d.states.firstOrNull { it.state == f.stateAbbr }
    }
    val c = counts ?: return
    InfoCard(title = "Local governments") {
        Text("Governments in $where", style = MaterialTheme.typography.bodyMedium)
        c.county?.let { Stat("County governments", Format.commas(it.toLong())) }
        c.municipal?.let { Stat("Cities, towns and villages", Format.commas(it.toLong())) }
        c.township?.let { Stat("Townships", Format.commas(it.toLong())) }
        c.school?.let { Stat("Independent school districts", Format.commas(it.toLong())) }
        c.special?.let { Stat("Special districts (water, fire, transit, parks…)", Format.commas(it.toLong())) }
        // The per-county counts have no total of their own.
        val total = c.total ?: listOfNotNull(c.county, c.municipal, c.township, c.special, c.school).sum()
        Stat("All local governments", Format.commas(total.toLong()))
        Note("Each one has its own officials, and many have their own taxes or fees.")
        SourceLine(d.source.name, d.source.link)
    }
}

@Composable
fun Stat(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

@Composable
fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

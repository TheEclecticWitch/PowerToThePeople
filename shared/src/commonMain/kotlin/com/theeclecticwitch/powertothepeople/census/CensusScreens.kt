package com.theeclecticwitch.powertothepeople.census

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import com.theeclecticwitch.powertothepeople.location.UserLocation
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

/** "Bowie city, Maryland" -> "Bowie city"; "State Senate District 29 (2024); Maryland" -> "State Senate District 29". */
private fun AreaFigures.shortName(): String =
    name?.substringBefore(";")?.substringBefore(",")?.replace(Regex(""" \(\d{4}\)"""), "")
        ?.replace("State Legislative Subdistrict", "State Legislative District") ?: ""

fun areaLabel(area: Area, location: UserLocation): String = when (area.kind) {
    AreaKind.Congress -> location.congressionalDistrictLabel
    AreaKind.State -> location.stateName
    AreaKind.Country -> "United States"
    else -> area.figures.shortName()
}

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
    LaunchedEffect(location?.enteredAddress, attempt) {
        val loc = location ?: return@LaunchedEffect
        error = null
        try {
            figures = CensusData.community(loc)
        } catch (e: Exception) {
            error = "The Census figures couldn't be loaded. Check your connection and try again."
        }
    }
    Scaffold(topBar = { AppTopBar("Your Community", onBack) }) { padding ->
        PageColumn(Modifier.padding(padding)) {
            val loc = location
            val f = figures
            when {
                loc == null -> CardPage {
                    InfoCard(title = "Your community by the numbers") {
                        Text(
                            "Set your location to see the Census Bureau's figures for your town, county, districts and state.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        OutlinedButton(onClick = onSetLocation) { Text("Set my location") }
                    }
                }
                error != null && f == null -> ErrorBox(error!!) { attempt++ }
                f == null -> LoadingBox("Loading the Census figures…")
                else -> {
                    val area = f.areas.getOrNull(chosen) ?: f.areas.first()
                    CardPage(spacing = 12.dp) {
                        AreaPicker(f.areas, loc, chosen) { chosen = it }
                        CommunityCards(f, area, loc, onSeats)
                    }
                }
            }
        }
    }
}

@Composable
private fun AreaPicker(areas: List<Area>, location: UserLocation, chosen: Int, onPick: (Int) -> Unit) {
    Column(Modifier.fullWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Show the figures for", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            areas.forEachIndexed { i, a ->
                FilterChip(selected = i == chosen, onClick = { onPick(i) }, label = { Text(areaLabel(a, location)) })
            }
        }
    }
}

@Composable
private fun CommunityCards(f: CommunityFigures, area: Area, location: UserLocation, onSeats: () -> Unit) {
    val a = area.figures
    val state = f.state.takeIf { area.kind != AreaKind.State && area.kind != AreaKind.Country }
    val us = f.us.takeIf { area.kind != AreaKind.Country }
    val sourceDetail = "${f.vintage - 4}-${f.vintage} survey"

    /** "State 12.1% · U.S. 14.0%", for any share worked out the same way. */
    @Composable
    fun Compare(share: (AreaFigures) -> Double?) {
        val parts = listOfNotNull(
            state?.let { s -> share(s)?.let { "${location.stateName} ${percent(it)}" } },
            us?.let { u -> share(u)?.let { "U.S. ${percent(it)}" } },
        )
        if (parts.isNotEmpty()) Note(parts.joinToString(" · "))
    }

    InfoCard(title = "People") {
        Text(areaLabel(area, location), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Stat("Population", count(a.pop))
        Stat("Median age", a.age?.let { Format.decimals(it, 1) + " years" } ?: "not available")
        Stat("Veterans", "${count(a.veterans)} (${percent(pct(a.veterans, a.adults18))} of adults)")
        Compare { pct(it.veterans, it.adults18) }
        if (area.kind == AreaKind.State) {
            TextButton(onClick = onSeats) { Text("${location.stateName}'s seats in the House ›") }
        }
        SourceLine("U.S. Census Bureau, American Community Survey", f.source.url, sourceDetail)
    }

    CitizenshipCard(a, area, location, state, us, f)
    HistoryCard(area, location)

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
            state?.income?.let { "${location.stateName} ${wholeDollars(it)}" },
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

    LocalGovernmentsCard(area, location)

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
    area: Area,
    location: UserLocation,
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
            state?.let { "${location.stateName}: ${percent(pct(it.notCitizen, it.citTotal))} not citizens" },
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
            val mine = e.states.firstOrNull { it.state == location.stateAbbr }?.estimate
            val asOf = e.asOf?.let { " as of ${Format.date("$it-01").ifBlank { it }}" } ?: ""
            e.us?.let { Stat("United States$asOf", "about ${Format.commas(it)}") }
            if (e.usHistory.size > 1) {
                Note("Earlier estimates for the country: " + e.usHistory.entries.sortedBy { it.key }
                    .joinToString(", ") { (y, n) -> "$y about ${Format.commas(n)}" } + ". Homeland Security warns these years aren't fully comparable.")
            }
            if (mine != null) {
                Stat(location.stateName, "about ${Format.commas(mine)}")
            } else {
                Note("The estimate lists only the states with the most; ${location.stateName} isn't one of them.")
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
@Composable
private fun HistoryCard(area: Area, location: UserLocation) {
    val codes = location.codes
    val history by produceState<Pair<String, List<Pair<Int, Double>>>?>(null, area.key, location.stateAbbr) {
        val older = CensusData.foreignBornHistory()
        val county = if (area.kind == AreaKind.County || area.kind == AreaKind.Town || area.kind == AreaKind.Township) codes?.county else null
        val countyYears = county?.let { CensusData.citizenshipYears(location.stateAbbr, it) }
        val (place, recent, decades) = when {
            area.kind == AreaKind.Country -> Triple("the United States", CensusData.citizenshipYears(null), older?.us)
            countyYears != null -> Triple(location.county ?: "your county", countyYears, null)
            else -> Triple(location.stateName, CensusData.citizenshipYears(location.stateAbbr), older?.states?.get(location.stateAbbr))
        }
        val points = decades.orEmpty().mapNotNull { y -> y.pct?.let { y.year to it } } +
            recent?.first.orEmpty().map { it.year to it.foreignBorn * 100.0 / it.population }
        value = if (points.size >= 2) place to points.sortedBy { it.first } else null
    }
    val (place, points) = history ?: return
    InfoCard(title = "Immigrants over the years") {
        Text("People born in another country, as a share of everyone in $place", style = MaterialTheme.typography.bodyMedium)
        if (area.kind != AreaKind.Country && !place.equals(areaLabel(area, location), ignoreCase = true)) {
            Note("Shown for $place: district lines change and towns are too small for yearly figures.")
        }
        LineChart(points)
        var showAll by remember(place) { mutableStateOf(false) }
        val rows = if (showAll) points.reversed() else points.reversed().take(6)
        rows.forEach { (year, p) ->
            Stat("$year", "${percent(p)} immigrants · ${percent(100 - p)} born citizens")
        }
        if (points.size > 6) {
            TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Show fewer years" else "Show every year") }
        }
        Note(
            "Immigrants here means everyone born in another country to non-American parents: naturalized citizens and " +
                "non-citizens alike. Every ten years from 1850 to 2000 the figure is from the census; from 2005 on, from " +
                "the yearly survey (none for 2020, when the pandemic cut the survey short).",
        )
        SourceLine("U.S. Census Bureau", "https://www.census.gov/topics/population/foreign-born.html")
    }
}

/** One line, years along the bottom at their true spacing, with the first and last values labeled. */
@Composable
private fun LineChart(points: List<Pair<Int, Double>>) {
    val line = seriesColors()[0]
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val first = points.first()
    val last = points.last()
    val top = (points.maxOf { it.second } * 1.15).coerceAtLeast(5.0)
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text("${percent(first.second)}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text("${percent(last.second)}", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
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
        }
        Row(Modifier.fillMaxWidth()) {
            Text("${first.first}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text("${last.first}", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LocalGovernmentsCard(area: Area, location: UserLocation) {
    val data by produceState<LocalGovernments?>(null) { value = CensusData.table("local-governments.json") }
    val d = data ?: return
    val countyCode = location.codes?.let { c -> c.county?.let { c.state + it } }
    val (where, counts) = when {
        area.kind == AreaKind.Country -> "the United States" to d.us
        area.kind == AreaKind.County && countyCode != null && d.counties[countyCode] != null ->
            (location.county ?: "your county") to d.counties[countyCode]
        else -> location.stateName to d.states.firstOrNull { it.state == location.stateAbbr }
    }
    val c = counts ?: return
    InfoCard(title = "Local governments") {
        Text("Governments in $where", style = MaterialTheme.typography.bodyMedium)
        c.county?.let { Stat("County governments", Format.commas(it.toLong())) }
        c.municipal?.let { Stat("Cities, towns and villages", Format.commas(it.toLong())) }
        c.township?.let { Stat("Townships", Format.commas(it.toLong())) }
        c.school?.let { Stat("Independent school districts", Format.commas(it.toLong())) }
        c.special?.let { Stat("Special districts (water, fire, transit, parks…)", Format.commas(it.toLong())) }
        c.total?.let { Stat("All local governments", Format.commas(it.toLong())) }
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

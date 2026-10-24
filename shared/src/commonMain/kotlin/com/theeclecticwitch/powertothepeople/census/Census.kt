package com.theeclecticwitch.powertothepeople.census

import com.theeclecticwitch.powertothepeople.congress.CongressData
import com.theeclecticwitch.powertothepeople.data.CachedSource
import com.theeclecticwitch.powertothepeople.data.Http
import com.theeclecticwitch.powertothepeople.location.CensusCodes
import com.theeclecticwitch.powertothepeople.location.CensusGeocoder
import com.theeclecticwitch.powertothepeople.location.LocationStore
import com.theeclecticwitch.powertothepeople.location.UserLocation
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/*
 * The Census Bureau's figures, gathered once a month by this project's own gatherer
 * (tools/gather/census_run.py) and published on GitHub Pages. Copied as published; the app only
 * works out percentages.
 */

@Serializable
data class CensusSource(val name: String, val url: String, val tables: String? = null, val table: String? = null)

/** One area in the yearly survey. Every count can be missing where the survey had too few answers. */
@Serializable
data class AreaFigures(
    val name: String? = null,
    val pop: Long? = null,
    val age: Double? = null,
    val income: Long? = null,
    val povertyTotal: Long? = null,
    val poverty: Long? = null,
    val homes: Long? = null,
    val owned: Long? = null,
    val rented: Long? = null,
    val adults25: Long? = null,
    val bachelors: Long? = null,
    val adults18: Long? = null,
    val veterans: Long? = null,
    val insuranceTotal: Long? = null,
    val uninsured: Long? = null,
    val speakers: Long? = null,
    val englishOnly: Long? = null,
    val spanish: Long? = null,
    val citTotal: Long? = null,
    val bornUS: Long? = null,
    val bornTerritories: Long? = null,
    val bornAbroadToCitizens: Long? = null,
    val naturalized: Long? = null,
    val notCitizen: Long? = null,
    /** The ten most common countries of birth, as [country, people]. */
    val bornIn: List<List<JsonElement>> = emptyList(),
) {
    val bornCitizens: Long?
        get() = if (bornUS == null || bornTerritories == null || bornAbroadToCitizens == null) null
        else bornUS + bornTerritories + bornAbroadToCitizens

    val foreignBorn: Long? get() = if (naturalized == null || notCitizen == null) null else naturalized + notCitizen

    /** Share of everyone born in the U.S., Puerto Rico or a U.S. territory. */
    val bornHerePct: Double?
        get() = if (bornUS == null || bornTerritories == null || citTotal == null || citTotal == 0L) null
        else (bornUS + bornTerritories) * 100.0 / citTotal

    val countries: List<Pair<String, Long>>
        get() = bornIn.mapNotNull { pair ->
            val name = (pair.getOrNull(0) as? JsonPrimitive)?.content ?: return@mapNotNull null
            val count = (pair.getOrNull(1) as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            name to count
        }
}

@Serializable
internal class AcsFile(val vintage: Int, val source: CensusSource, val areas: Map<String, AreaFigures>)

/** One year of citizenship counts. */
data class CitizenshipYear(val year: Int, val population: Long, val bornCitizens: Long, val naturalized: Long, val notCitizens: Long) {
    val foreignBorn: Long get() = naturalized + notCitizens
}

@Serializable
private class HistoryFile(
    val source: HistorySource,
    val us: List<List<JsonElement>> = emptyList(),
    val states: Map<String, List<List<JsonElement>>> = emptyMap(),
    val counties: Map<String, List<List<JsonElement>>> = emptyMap(),
    /** Counties whose figures after 2000 come from the 5-year survey. */
    val fiveYear: List<String> = emptyList(),
)

/** Citizenship year by year for one place; [fiveYear] when each figure after 2000 pools five years. */
data class CitizenshipHistory(val years: List<CitizenshipYear>, val source: HistorySource, val fiveYear: Boolean = false)

@Serializable
data class HistorySource(val name: String, val url: String, val notes: List<String> = emptyList())

private fun List<List<JsonElement>>.years(): List<CitizenshipYear> = mapNotNull { row ->
    val n = row.map { (it as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null }
    if (n.size < 5) null else CitizenshipYear(n[0].toInt(), n[1], n[2], n[3], n[4])
}

/** One decennial census year from 1850 to 2000. */
@Serializable
data class CensusYear(val year: Int, val population: Long? = null, val foreignBorn: Long? = null, val pct: Double? = null)

@Serializable
data class ForeignBornHistory(
    val source: CensusSource,
    val definition: String? = null,
    val us: List<CensusYear> = emptyList(),
    val states: Map<String, List<CensusYear>> = emptyMap(),
)

/** The kinds of area a reader lives in, in the order the screen offers them, with what to call a list of them. */
enum class AreaKind(val prefix: String, val plural: String) {
    Town("place", "Cities and towns"), Township("cousub", "Townships"), County("county", "Counties"),
    StateHouse("sldl", "State house districts"), StateSenate("sldu", "State senate districts"),
    Congress("cd", "Congressional districts"), State("state", "The state"), Country("us", "The country"),
}

/**
 * One area, ready to show. [countyCode] is the county it lies in (three digits) when that is known,
 * for the county's figures over the years and its local governments.
 */
data class Area(
    val kind: AreaKind,
    val key: String,
    val figures: AreaFigures,
    val label: String,
    val countyCode: String? = null,
    val countyName: String? = null,
)

data class CommunityFigures(
    val vintage: Int,
    val source: CensusSource,
    val areas: List<Area>,
    val state: AreaFigures?,
    val us: AreaFigures?,
    val stateAbbr: String,
    val stateName: String,
    /** True when these are a place the reader chose, not where they live. */
    val elsewhere: Boolean = false,
)

/** "Bowie city, Maryland" -> "Bowie city"; "State Senate District 29 (2024); Maryland" -> "State Senate District 29". */
fun AreaFigures.shortName(): String =
    name?.substringBefore(";")?.substringBefore(",")?.replace(Regex(""" \([^)]*\)"""), "")
        ?.replace("State Legislative Subdistrict", "State Legislative District")?.trim() ?: ""

/** The Census Bureau's two-digit code for each state, which starts each of its county codes. */
val STATE_FIPS = mapOf(
    "AL" to "01", "AK" to "02", "AZ" to "04", "AR" to "05", "CA" to "06", "CO" to "08", "CT" to "09", "DE" to "10",
    "DC" to "11", "FL" to "12", "GA" to "13", "HI" to "15", "ID" to "16", "IL" to "17", "IN" to "18", "IA" to "19",
    "KS" to "20", "KY" to "21", "LA" to "22", "ME" to "23", "MD" to "24", "MA" to "25", "MI" to "26", "MN" to "27",
    "MS" to "28", "MO" to "29", "MT" to "30", "NE" to "31", "NV" to "32", "NH" to "33", "NJ" to "34", "NM" to "35",
    "NY" to "36", "NC" to "37", "ND" to "38", "OH" to "39", "OK" to "40", "OR" to "41", "PA" to "42", "RI" to "44",
    "SC" to "45", "SD" to "46", "TN" to "47", "TX" to "48", "UT" to "49", "VT" to "50", "VA" to "51", "WA" to "53",
    "WV" to "54", "WI" to "55", "WY" to "56", "PR" to "72",
)

/** "District 2" before "District 10": numbers compared as numbers. */
private fun sortKey(name: String): String = name.lowercase().replace(Regex("""\d+""")) { it.value.padStart(6, '0') }

/** An area another reader might look up, for the list of places to pick from. */
data class AreaChoice(val kind: AreaKind, val key: String, val name: String, val bornHerePct: Double? = null)

@Serializable
private class SummaryFile(val vintage: Int, val states: Map<String, AreaFigures> = emptyMap())

object CensusData {
    private val lock = Mutex()
    private val sources = mutableMapOf<String, CachedSource>()

    private suspend fun text(path: String): String = lock.withLock {
        sources.getOrPut(path) { CachedSource("pd_" + path.replace('/', '_'), 7.days) { Http.getText("${CongressData.BASE}/$path") } }
    }.get().text

    /**
     * The Census codes for the reader's areas. A location saved before the app kept them is looked up
     * again, once, from the address the reader gave; the Census Bureau's geocoder is where it went before.
     */
    suspend fun codesFor(location: UserLocation): Pair<UserLocation, CensusCodes?> {
        location.codes?.let { return location to it }
        val fresh = CensusGeocoder.lookup(location.enteredAddress)
        LocationStore.save(fresh)
        return fresh to fresh.codes
    }

    private suspend fun stateFile(abbr: String): AcsFile = Http.json.decodeFromString(text("census/acs/$abbr.json"))

    private suspend fun country(): AreaFigures? = try {
        Http.json.decodeFromString<AcsFile>(text("census/acs/US.json")).areas["us"]
    } catch (e: Exception) {
        null
    }

    /** Every area the reader lives in, from their town up to the country. */
    suspend fun community(location: UserLocation): CommunityFigures {
        val (loc, codes) = codesFor(location)
        val file = stateFile(loc.stateAbbr)
        val us = country()
        val inCounty = codes?.county
        val wanted = listOfNotNull(
            codes?.place?.let { AreaKind.Town to "place:$it" },
            codes?.cousub?.let { AreaKind.Township to "cousub:$it" },
            codes?.county?.let { AreaKind.County to "county:$it" },
            codes?.sldl?.let { AreaKind.StateHouse to "sldl:$it" },
            codes?.sldu?.let { AreaKind.StateSenate to "sldu:$it" },
            codes?.cd?.let { AreaKind.Congress to "cd:$it" },
            AreaKind.State to "state",
        )
        val areas = wanted.mapNotNull { (kind, key) ->
            val figures = file.areas[key] ?: return@mapNotNull null
            val label = when (kind) {
                AreaKind.Congress -> loc.congressionalDistrictLabel
                AreaKind.State -> loc.stateName
                else -> figures.shortName()
            }
            val local = kind == AreaKind.Town || kind == AreaKind.Township || kind == AreaKind.County
            Area(kind, key, figures, label, inCounty.takeIf { local }, loc.county.takeIf { local })
        } + listOfNotNull(us?.let { Area(AreaKind.Country, "us", it, "United States") })
        // Where a city is its own township (Philadelphia), the two are the same place: show it once.
        val town = areas.firstOrNull { it.kind == AreaKind.Town }?.label
        val distinct = areas.filterNot { it.kind == AreaKind.Township && it.label == town }
        return CommunityFigures(file.vintage, file.source, distinct, file.areas["state"], us, loc.stateAbbr, loc.stateName)
    }

    /** Every area in a state, for picking one, grouped by kind and alphabetical within each. */
    suspend fun choices(stateAbbr: String): Map<AreaKind, List<AreaChoice>> {
        val file = stateFile(stateAbbr)
        val kinds = AreaKind.entries.associateBy { it.prefix }
        return file.areas.mapNotNull { (key, figures) ->
            val kind = kinds[key.substringBefore(":")]?.takeIf { it != AreaKind.State && it != AreaKind.Country }
            kind?.let { AreaChoice(it, key, figures.shortName(), figures.bornHerePct) }
        }.groupBy { it.kind }.mapValues { (_, list) -> list.sortedBy { sortKey(it.name) } }
            .entries.sortedBy { it.key.ordinal }.associate { it.key to it.value }
    }

    /** Each state's share born in the U.S., for the list of states; empty if the summary can't be had. */
    suspend fun stateBornHere(): Map<String, Double> = try {
        Http.json.decodeFromString<SummaryFile>(text("census/acs/summary.json")).states
            .mapNotNull { (abbr, f) -> f.bornHerePct?.let { abbr to it } }.toMap()
    } catch (e: Exception) {
        emptyMap()
    }

    /** A state's own share born in the U.S., from its file (already downloaded for its list of places). */
    suspend fun stateBornHere(abbr: String): Double? = try {
        stateFile(abbr).areas["state"]?.bornHerePct
    } catch (e: Exception) {
        null
    }

    /** One area anywhere in the country, shown beside its state and the country. */
    suspend fun elsewhere(stateAbbr: String, stateName: String, key: String): CommunityFigures {
        val file = stateFile(stateAbbr)
        val us = country()
        val state = file.areas["state"]
        val kinds = AreaKind.entries.associateBy { it.prefix }
        val chosen = file.areas[key]?.takeIf { key != "state" }?.let { figures ->
            val kind = kinds.getValue(key.substringBefore(":"))
            val code = key.substringAfter(":")
            val county = when (kind) {
                AreaKind.County -> code
                AreaKind.Township -> code.take(3)
                else -> null
            }
            val countyName = county?.let { file.areas["county:$it"]?.shortName() }
            Area(kind, key, figures, figures.shortName(), county, countyName)
        }
        val areas = listOfNotNull(
            chosen,
            state?.let { Area(AreaKind.State, "state", it, stateName) },
            us?.let { Area(AreaKind.Country, "us", it, "United States") },
        )
        return CommunityFigures(file.vintage, file.source, areas, state, us, stateAbbr, stateName, elsewhere = true)
    }

    /** Citizenship year by year for the country, a state, or one county ("031"), from 2005 on. */
    suspend fun citizenshipYears(state: String?, county: String? = null): CitizenshipHistory? = try {
        if (county != null && state != null) {
            val f = Http.json.decodeFromString<HistoryFile>(text("census/history-counties/$state.json"))
            f.counties[county]?.years()?.let { CitizenshipHistory(it, f.source, county in f.fiveYear) }
        } else {
            val f = Http.json.decodeFromString<HistoryFile>(text("census/history.json"))
            (if (state == null) f.us else f.states[state].orEmpty()).years().takeIf { it.isNotEmpty() }
                ?.let { CitizenshipHistory(it, f.source) }
        }
    } catch (e: Exception) {
        null
    }

    suspend fun foreignBornHistory(): ForeignBornHistory? = try {
        Http.json.decodeFromString<ForeignBornHistory>(text("census/foreign-born-history.json"))
    } catch (e: Exception) {
        null
    }

    /** A hand-copied table, decoded into [T]; null if it isn't published or can't be read. */
    suspend inline fun <reified T> table(name: String): T? = try {
        Http.json.decodeFromString<T>(tableText(name))
    } catch (e: Exception) {
        null
    }

    suspend fun tableText(name: String): String = text("census/$name")
}

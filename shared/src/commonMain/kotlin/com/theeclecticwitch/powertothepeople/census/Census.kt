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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
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

    val countries: List<Pair<String, Long>>
        get() = bornIn.mapNotNull { pair ->
            val name = (pair.getOrNull(0) as? JsonPrimitive)?.content ?: return@mapNotNull null
            val count = (pair.getOrNull(1) as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            name to count
        }
}

@Serializable
private class AcsFile(val vintage: Int, val source: CensusSource, val areas: Map<String, AreaFigures>)

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
)

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

/** The kinds of area a reader lives in, in the order the screen offers them. */
enum class AreaKind(val prefix: String) {
    Town("place"), Township("cousub"), County("county"), StateHouse("sldl"), StateSenate("sldu"),
    Congress("cd"), State("state"), Country("us"),
}

/** One of the reader's areas, ready to show. */
data class Area(val kind: AreaKind, val key: String, val figures: AreaFigures)

data class CommunityFigures(
    val vintage: Int,
    val source: CensusSource,
    val areas: List<Area>,
    val state: AreaFigures?,
    val us: AreaFigures?,
)

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

    suspend fun community(location: UserLocation): CommunityFigures {
        val (loc, codes) = codesFor(location)
        val file = Http.json.decodeFromString<AcsFile>(text("census/acs/${loc.stateAbbr}.json"))
        val us = try {
            Http.json.decodeFromString<AcsFile>(text("census/acs/US.json")).areas["us"]
        } catch (e: Exception) {
            null
        }
        val wanted = listOfNotNull(
            codes?.place?.let { AreaKind.Town to "place:$it" },
            codes?.cousub?.let { AreaKind.Township to "cousub:$it" },
            codes?.county?.let { AreaKind.County to "county:$it" },
            codes?.sldl?.let { AreaKind.StateHouse to "sldl:$it" },
            codes?.sldu?.let { AreaKind.StateSenate to "sldu:$it" },
            codes?.cd?.let { AreaKind.Congress to "cd:$it" },
            AreaKind.State to "state",
        )
        val areas = wanted.mapNotNull { (kind, key) -> file.areas[key]?.let { Area(kind, key, it) } } +
            listOfNotNull(us?.let { Area(AreaKind.Country, "us", it) })
        return CommunityFigures(file.vintage, file.source, areas, file.areas["state"], us)
    }

    /** Citizenship year by year for the country, a state, or one county ("031"), from 2005 on. */
    suspend fun citizenshipYears(state: String?, county: String? = null): Pair<List<CitizenshipYear>, HistorySource>? = try {
        if (county != null && state != null) {
            val f = Http.json.decodeFromString<HistoryFile>(text("census/history-counties/$state.json"))
            f.counties[county]?.years()?.let { it to f.source }
        } else {
            val f = Http.json.decodeFromString<HistoryFile>(text("census/history.json"))
            (if (state == null) f.us else f.states[state].orEmpty()).years().takeIf { it.isNotEmpty() }?.let { it to f.source }
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

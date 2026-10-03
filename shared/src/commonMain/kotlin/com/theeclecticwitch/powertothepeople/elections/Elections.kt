package com.theeclecticwitch.powertothepeople.elections

import com.theeclecticwitch.powertothepeople.congress.CongressData
import com.theeclecticwitch.powertothepeople.data.CachedSource
import com.theeclecticwitch.powertothepeople.data.Http
import com.theeclecticwitch.powertothepeople.location.UserLocation
import com.theeclecticwitch.powertothepeople.officials.JsonFileState
import com.theeclecticwitch.powertothepeople.officials.StateNames
import com.theeclecticwitch.powertothepeople.voting.Contest
import com.theeclecticwitch.powertothepeople.voting.VoterInfo
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** A candidate as the FEC lists them (candidates/{cycle}.json). */
@Serializable
data class FecCandidate(val fecId: String, val name: String, val party: String? = null, val status: String? = null, val url: String? = null)

@Serializable
private class CandidatesFile(val cycle: Int = 0, val races: Map<String, List<FecCandidate>> = emptyMap(), val checked: String? = null)

/** One person in one race, whichever source named them. Every candidate is shown with exactly these fields. */
data class RaceCandidate(
    /** Stable across sources and visits, for the reader's notes: state, office and name. */
    val key: String,
    val name: String,
    val party: String?,
    /** "incumbent", "challenger" or "open"; null when the source doesn't say, for every candidate in the race. */
    val status: String?,
    val website: String? = null,
    val fecUrl: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val photo: String? = null,
    val channels: List<Pair<String, String>> = emptyList(),
)

data class Race(
    val id: String,
    val title: String,
    val subtitle: String?,
    /** Where the list comes from, said plainly on the race's page. */
    val sourceNote: String,
    val sourceName: String,
    val sourceUrl: String?,
    val candidates: List<RaceCandidate>,
)

/** "DEMOCRATIC PARTY" -> "Democratic Party"; names already in mixed case are left alone. */
fun tidyParty(party: String?): String? = party?.trim()?.takeIf { it.isNotEmpty() }?.let { p ->
    if (p != p.uppercase()) p else p.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}

fun statusLine(status: String?): String? = when (status) {
    "incumbent" -> "Current officeholder"
    "challenger" -> "Challenger"
    "open" -> "Running for an open seat"
    else -> null
}

fun noteKey(state: String, office: String, name: String): String =
    "${state.lowercase()}|${office.lowercase().filter { it.isLetterOrDigit() }}|${name.lowercase().filter { it.isLetter() }}"

object Elections {
    private var cycleSource: Pair<Int, CachedSource>? = null
    /** Races built from the reader's ballot lookup, kept for this visit so a race's page can find them. */
    private val ballotRaces = mutableMapOf<String, Race>()

    /** The year of the next federal general election: this year if even, else next. */
    fun cycle(year: Int): Int = if (year % 2 == 0) year else year + 1

    private suspend fun file(cycle: Int): CandidatesFile? {
        val src = cycleSource?.takeIf { it.first == cycle }?.second
            ?: CachedSource("pd_candidates_$cycle.json", 12.hours) { Http.getText("${CongressData.BASE}/candidates/$cycle.json") }
                .also { cycleSource = cycle to it }
        return try {
            Http.json.decodeFromString<CandidatesFile>(src.get().text)
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) null else throw e
        }
    }

    /** The race key the gatherer uses: "MD-05", "AK-00" (at large), "MD-SEN". */
    fun houseKey(state: String, district: Int) = "${state.uppercase()}-${district.toString().padStart(2, '0')}"
    fun senateKey(state: String) = "${state.uppercase()}-SEN"

    /**
     * The reader's races for Congress this cycle: their House seat (on the map for the coming Congress when the
     * Census Bureau already has it) and a Senate seat if one is up. Null when the list isn't published yet.
     */
    suspend fun congressRaces(location: UserLocation, cycle: Int): List<Race>? {
        val f = file(cycle) ?: return null
        val district = location.nextCongressionalDistrict ?: location.congressionalDistrict
        val keys = listOfNotNull(senateKey(location.stateAbbr), district?.let { houseKey(location.stateAbbr, it) })
        return keys.mapNotNull { k -> f.races[k]?.let { fecRace(k, cycle, it) } }
    }

    private fun fecRace(key: String, cycle: Int, list: List<FecCandidate>): Race {
        val state = key.substringBefore('-')
        val seat = key.substringAfter('-')
        val stateName = StateNames.of(state)
        val title = if (seat == "SEN") "U.S. Senate, $stateName" else
            "U.S. House, $stateName" + if (seat == "00") " (at large)" else " District ${seat.trimStart('0')}"
        val office = if (seat == "SEN") "senate" else "house$seat"
        return Race(
            id = "fec:$cycle:$key",
            title = title,
            subtitle = "$cycle election",
            sourceNote = "Everyone registered with the Federal Election Commission as an active candidate for this seat. The FEC " +
                "doesn't record primaries, so this can include people who lost a primary or stopped campaigning. Your state " +
                "certifies who is on the ballot; your ballot shows here once it's published.",
            sourceName = "Federal Election Commission",
            sourceUrl = "https://www.fec.gov/data/elections/" + if (seat == "SEN") "senate/$state/$cycle/" else "house/$state/$seat/$cycle/",
            candidates = list.map { c ->
                RaceCandidate(
                    key = noteKey(state, office, c.name),
                    name = c.name,
                    party = tidyParty(c.party),
                    status = c.status,
                    fecUrl = c.url,
                )
            },
        )
    }

    /** The races on the reader's actual ballot, from their state by way of Google. Ballot questions stay separate. */
    fun ballotRaces(info: VoterInfo, state: String): List<Race> {
        val races = info.contests.filter { it.measure == null && it.candidates.isNotEmpty() }.mapIndexed { i, c -> ballotRace(i, c, state, info) }
        ballotRaces.clear()
        races.forEach { ballotRaces[it.id] = it }
        return races
    }

    private fun ballotRace(i: Int, c: Contest, state: String, info: VoterInfo): Race {
        val office = c.office ?: "Office"
        return Race(
            id = "ballot:$i",
            title = office,
            subtitle = listOfNotNull(c.district?.takeIf { it != office }, info.election?.name).joinToString(" · ").ifEmpty { null },
            sourceNote = "As your state and local election offices list this race for your address.",
            sourceName = "Google Civic Information, from ${info.office ?: "your election office"}",
            sourceUrl = info.links.ballot ?: info.links.info,
            candidates = c.candidates.map { p ->
                RaceCandidate(
                    key = noteKey(state, office, p.name),
                    name = p.name,
                    party = tidyParty(p.party),
                    status = null,
                    website = p.url,
                    phone = p.phone,
                    email = p.email,
                    photo = p.photo,
                    channels = p.channels.map { it.type to it.id },
                )
            },
        )
    }

    /** A race by its id: the FEC's are always found again; a ballot race only during the visit that looked it up. */
    suspend fun race(id: String): Race? {
        ballotRaces[id]?.let { return it }
        if (!id.startsWith("fec:")) return null
        val (_, cycle, key) = id.split(':')
        val list = file(cycle.toInt())?.races?.get(key) ?: return null
        return fecRace(key, cycle.toInt(), list)
    }
}

/** The reader's own notes on candidates: what they like, dislike or want to remember. Kept on this device only. */
object CandidateNotes {
    private val notes = JsonFileState("candidate_notes.json", MapSerializer(String.serializer(), String.serializer()), emptyMap())
    val flow: StateFlow<Map<String, String>> = notes.flow

    fun set(key: String, text: String) = notes.update { if (text.isBlank()) it - key else it + (key to text) }
}

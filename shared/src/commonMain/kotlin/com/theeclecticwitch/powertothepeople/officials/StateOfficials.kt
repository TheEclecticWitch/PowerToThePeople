package com.theeclecticwitch.powertothepeople.officials

import com.theeclecticwitch.powertothepeople.congress.CongressData
import com.theeclecticwitch.powertothepeople.data.CachedSource
import com.theeclecticwitch.powertothepeople.data.Http
import com.theeclecticwitch.powertothepeople.location.UserLocation
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
private class RawStateOffice(val address: String? = null, val phone: String? = null)

@Serializable
private class RawStatePerson(
    val id: String,
    val name: String,
    val title: String,
    val party: String? = null,
    val chamber: String? = null,
    val district: String? = null,
    val start: String? = null,
    val end: String? = null,
    val email: String? = null,
    val image: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val website: String? = null,
    val contactForm: String? = null,
    val districtOffice: RawStateOffice? = null,
)

@Serializable
private class RawState(
    val state: String,
    val source: String = StateOfficials.SOURCE_URL,
    val executives: List<RawStatePerson> = emptyList(),
    val legislators: List<RawStatePerson> = emptyList(),
)

/** The reader's state government: statewide officers, and their own legislators if their district matched. */
data class StateDelegation(
    val executives: List<Official>,
    val senators: List<Official>,
    val representatives: List<Official>,
    /** False when the Census district names couldn't be matched to the state's own; the reader can browse instead. */
    val matched: Boolean,
)

/**
 * Governors, statewide officers and state legislators, from the Open States project's public data, gathered
 * weekly by this project's gatherer into one file per state (states/md.json).
 */
object StateOfficials {
    const val SOURCE_NAME = "Open States"
    const val SOURCE_URL = "https://github.com/openstates/people"

    private val lock = Mutex()
    private val sources = mutableMapOf<String, CachedSource>()
    private val loaded = mutableMapOf<String, RawState>()

    private suspend fun load(state: String, force: Boolean = false): RawState {
        val st = state.lowercase()
        loaded[st]?.takeIf { !force }?.let { return it }
        val source = lock.withLock {
            sources.getOrPut(st) {
                CachedSource("pd_states_$st.json", 7.days) { Http.getText("${CongressData.BASE}/states/$st.json") }
            }
        }
        val raw = Http.json.decodeFromString<RawState>(source.get(force).text)
        loaded[st] = raw
        return raw
    }

    suspend fun forLocation(location: UserLocation, force: Boolean = false): StateDelegation {
        val raw = load(location.stateAbbr, force)
        val st = location.stateAbbr
        val senators = raw.legislators.filter { it.chamber != "lower" && districtMatches(location.stateSenateDistrict, it.district) }
        val reps = raw.legislators.filter { it.chamber == "lower" && districtMatches(location.stateHouseDistrict, it.district) }
        val hasLegislature = raw.legislators.isNotEmpty()
        return StateDelegation(
            executives = raw.executives.map { it.toOfficial(st) },
            senators = senators.map { it.toOfficial(st) },
            representatives = reps.map { it.toOfficial(st) },
            matched = !hasLegislature || senators.isNotEmpty() || reps.isNotEmpty(),
        )
    }

    /** Everyone in a state's legislature, for browsing when a district couldn't be matched. */
    suspend fun legislators(state: String): List<Official> = load(state).legislators.map { it.toOfficial(state.uppercase()) }

    /** "state:md:ocd-person/…" -> that person, loading their state's file if needed. */
    suspend fun byId(id: String): Official? {
        val st = id.removePrefix("state:").substringBefore(':').takeIf { it.length == 2 } ?: return null
        val raw = load(st)
        return (raw.executives + raw.legislators).firstOrNull { it.id == id }?.toOfficial(st.uppercase())
    }

    private fun RawStatePerson.toOfficial(state: String): Official {
        val stateName = StateNames.of(state)
        val office = when {
            chamber == null -> "$title of $stateName"
            district.isNullOrBlank() -> "$title, $stateName"
            district.first().isDigit() -> "$title, $stateName District $district"
            else -> "$title, $stateName ($district)"
        }
        return Official(
            id = id,
            name = name,
            office = office,
            level = Level.State,
            party = party,
            photoUrl = image,
            phone = phone,
            email = email,
            address = address,
            website = website,
            contactForm = contactForm,
            servingSince = start,
            termEnds = end,
            districtOffices = listOfNotNull(
                districtOffice?.let { o ->
                    o.address?.let { DistrictOffice(city = "District office", address = it, phone = o.phone) }
                },
            ),
            sourceName = SOURCE_NAME,
            sourceUrl = SOURCE_URL,
        )
    }
}

/**
 * Whether the Census Bureau's name for a district ("State Senate District 28", "State Legislative Subdistrict
 * 29A", "10th Bristol District") is the state's own district ("28", "29A", "10th Bristol"). The words around
 * the number differ by state, so both are reduced to their distinctive part.
 */
fun districtMatches(censusName: String?, district: String?): Boolean {
    if (censusName.isNullOrBlank() || district.isNullOrBlank()) return false
    val a = districtKey(censusName)
    val b = districtKey(district)
    if (a.isEmpty() || b.isEmpty()) return false
    val an = a.toIntOrNull()
    val bn = b.toIntOrNull()
    return if (an != null && bn != null) an == bn else a == b
}

private val districtNoise = Regex(
    "\\b(state|senate|senatorial|house|legislative|legislature|assembly|of|delegates|representatives|district|subdistrict|county|general)\\b",
)

internal fun districtKey(name: String): String =
    name.lowercase().replace(districtNoise, " ").filter { it.isLetterOrDigit() }

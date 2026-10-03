package com.theeclecticwitch.powertothepeople.officials

import com.theeclecticwitch.powertothepeople.data.CachedSource
import com.theeclecticwitch.powertothepeople.data.Http
import com.theeclecticwitch.powertothepeople.location.UserLocation
import com.theeclecticwitch.powertothepeople.location.today
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class Level(val label: String) { Federal("Federal"), State("State"), Local("Local") }

@Serializable
data class Link(val label: String, val url: String)

@Serializable
data class DistrictOffice(val city: String, val address: String, val phone: String? = null)

/**
 * Anyone who holds an office - from the public record, or entered by the reader themselves.
 *
 * One shape for both, so a city councilman the reader typed in sits on the same screen, with the
 * same notes and promises, as a U.S. Senator from the public data.
 */
@Serializable
data class Official(
    val id: String,
    val name: String,
    val office: String,
    val level: Level,
    val party: String? = null,
    val photoUrl: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val website: String? = null,
    val contactForm: String? = null,
    val servingSince: String? = null,
    val termEnds: String? = null,
    val districtOffices: List<DistrictOffice> = emptyList(),
    val links: List<Link> = emptyList(),
    /** Their accounts elsewhere, as links out. The app never shows posts; see Social. */
    val social: List<Link> = emptyList(),
    val sourceName: String? = null,
    val sourceUrl: String? = null,
    /** True for officials the reader added; they can edit and delete these. */
    val userEntered: Boolean = false,
)

/** One seat in Congress, as the directory lists it. */
data class Member(val official: Official, val chamber: String, val state: String, val district: Int?)

/** Everyone who represents the reader in Washington. */
data class FederalDelegation(
    val president: Official?,
    val vicePresident: Official?,
    val senators: List<Official>,
    val representative: Official?,
    val fetchedAt: Instant,
    val isStale: Boolean,
)

// --- The raw shape of the @unitedstates project's files. Only the fields the app uses. ---

@Serializable
private class RawPerson(val id: RawIds, val name: RawName, val terms: List<RawTerm>)

@Serializable
private class RawIds(
    val bioguide: String? = null,
    val govtrack: Int? = null,
    val wikipedia: String? = null,
    val ballotpedia: String? = null,
)

@Serializable
private class RawName(
    val first: String,
    val last: String,
    val middle: String? = null,
    val nickname: String? = null,
    val suffix: String? = null,
    @SerialName("official_full") val officialFull: String? = null,
) {
    val display: String
        get() = officialFull ?: listOfNotNull(nickname ?: first, last, suffix).joinToString(" ")
}

@Serializable
private class RawTerm(
    val type: String,
    val start: String,
    val end: String,
    val state: String? = null,
    val district: Int? = null,
    val party: String? = null,
    val url: String? = null,
    val address: String? = null,
    val phone: String? = null,
    @SerialName("contact_form") val contactForm: String? = null,
    @SerialName("state_rank") val stateRank: String? = null,
)

@Serializable
private class RawSocial(val id: RawIds, val social: Map<String, String> = emptyMap())

@Serializable
private class RawOffices(val id: RawIds, val offices: List<RawOffice> = emptyList())

@Serializable
private class RawOffice(
    val address: String? = null,
    val suite: String? = null,
    val building: String? = null,
    val city: String? = null,
    val state: String? = null,
    val zip: String? = null,
    val phone: String? = null,
)

/**
 * Members of Congress, the President and the Vice President, from the @unitedstates project's
 * congress-legislators data: public domain, kept current by volunteers from official sources,
 * and the same data many news organizations use. Free, no key.
 */
object FederalOfficials {
    private const val BASE = "https://unitedstates.github.io/congress-legislators"
    const val SOURCE_NAME = "@unitedstates congress-legislators"
    const val SOURCE_URL = "https://github.com/unitedstates/congress-legislators"

    private val legislatorsSource = CachedSource("cache_legislators_current.json", 24.hours) {
        Http.getText("$BASE/legislators-current.json")
    }
    private val officesSource = CachedSource("cache_district_offices.json", 7.days) {
        Http.getText("$BASE/legislators-district-offices.json")
    }
    private val executiveSource = CachedSource("cache_executive.json", 7.days) {
        Http.getText("$BASE/executive.json")
    }
    private val socialSource = CachedSource("cache_social_media.json", 7.days) {
        Http.getText("$BASE/legislators-social-media.json")
    }

    private val lock = Mutex()
    private var legislators: List<RawPerson>? = null
    private var offices: Map<String, List<RawOffice>> = emptyMap()
    private var executive: List<RawPerson>? = null
    private var social: Map<String, Map<String, String>> = emptyMap()
    private var fetchedAt: Instant? = null
    private var stale = false

    private suspend fun ensureLoaded(force: Boolean) = lock.withLock {
        if (!force && legislators != null) return@withLock
        val leg = legislatorsSource.get(force)
        legislators = Http.json.decodeFromString<List<RawPerson>>(leg.text)
        fetchedAt = leg.fetchedAt
        stale = leg.isStale
        offices = try {
            Http.json.decodeFromString<List<RawOffices>>(officesSource.get(force).text)
                .mapNotNull { o -> o.id.bioguide?.let { it to o.offices } }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
        executive = try {
            Http.json.decodeFromString<List<RawPerson>>(executiveSource.get(force).text)
        } catch (e: Exception) {
            emptyList()
        }
        social = try {
            Http.json.decodeFromString<List<RawSocial>>(socialSource.get(force).text)
                .mapNotNull { s -> s.id.bioguide?.let { it to s.social } }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun forLocation(location: UserLocation, forceRefresh: Boolean = false): FederalDelegation {
        ensureLoaded(forceRefresh)
        val people = legislators.orEmpty()
        val senators = people.filter { it.terms.last().type == "sen" && it.terms.last().state == location.stateAbbr }
            .sortedBy { if (it.terms.last().stateRank == "senior") 0 else 1 }
            .map { it.toOfficial() }
        val rep = location.congressionalDistrict?.let { district ->
            people.firstOrNull {
                val t = it.terms.last()
                t.type == "rep" && t.state == location.stateAbbr && (t.district ?: 0) == district
            }
        }?.toOfficial()
        return FederalDelegation(
            president = currentExecutive("prez"),
            vicePresident = currentExecutive("viceprez"),
            senators = senators,
            representative = rep,
            fetchedAt = fetchedAt!!,
            isStale = stale,
        )
    }

    suspend fun byId(id: String): Official? {
        ensureLoaded(false)
        if (id.startsWith("exec:")) {
            return currentExecutive(id.removePrefix("exec:"))
        }
        return legislators.orEmpty().firstOrNull { it.id.bioguide == id }?.toOfficial()
    }

    /** Every sitting member of both chambers, for the directory. */
    suspend fun allMembers(forceRefresh: Boolean = false): List<Member> {
        ensureLoaded(forceRefresh)
        return legislators.orEmpty().filter { it.id.bioguide != null }.map { p ->
            val t = p.terms.last()
            Member(p.toOfficial(), if (t.type == "sen") "senate" else "house", t.state ?: "", t.district)
        }
    }

    private fun currentExecutive(type: String): Official? {
        val now = today()
        val person = executive.orEmpty().lastOrNull { p ->
            p.terms.any { it.type == type && LocalDate.parse(it.start) <= now && now < LocalDate.parse(it.end) }
        } ?: return null
        val term = person.terms.last { it.type == type && LocalDate.parse(it.start) <= now }
        val firstOfRun = person.terms.filter { it.type == type }.let { terms ->
            // Consecutive terms in the same office count as one run of service.
            var i = terms.indexOf(term)
            while (i > 0 && terms[i - 1].end == terms[i].start) i--
            terms[i]
        }
        val isPresident = type == "prez"
        return Official(
            id = "exec:$type",
            name = person.name.display,
            office = if (isPresident) "President of the United States" else "Vice President of the United States",
            level = Level.Federal,
            party = term.party,
            phone = if (isPresident) "202-456-1111" else null,
            address = "The White House, 1600 Pennsylvania Avenue NW, Washington, DC 20500",
            website = "https://www.whitehouse.gov",
            contactForm = "https://www.whitehouse.gov/contact/",
            servingSince = firstOfRun.start,
            termEnds = term.end,
            links = listOfNotNull(
                person.id.wikipedia?.let { Link("Wikipedia", "https://en.wikipedia.org/wiki/" + it.replace(' ', '_')) },
            ),
            social = Social.executive(isPresident, person.name.display),
            sourceName = SOURCE_NAME,
            sourceUrl = SOURCE_URL,
        )
    }

    private fun RawPerson.toOfficial(): Official {
        val t = terms.last()
        val bioguide = id.bioguide ?: ""
        val stateName = StateNames.of(t.state)
        val office = if (t.type == "sen") {
            val rank = t.stateRank?.replaceFirstChar { it.uppercase() }?.let { "$it " } ?: ""
            "${rank}U.S. Senator for $stateName"
        } else {
            val title = when (t.state) {
                "PR" -> "Resident Commissioner"
                "DC", "GU", "VI", "AS", "MP" -> "Delegate to the U.S. House"
                else -> "U.S. Representative"
            }
            val district = if ((t.district ?: 0) == 0) "at-large" else "District ${t.district}"
            "$title, $stateName $district"
        }
        // Consecutive terms in the same chamber and state count as one run of service.
        var i = terms.lastIndex
        while (i > 0 && terms[i - 1].type == t.type && terms[i - 1].state == t.state) i--
        return Official(
            id = bioguide,
            name = name.display,
            office = office,
            level = Level.Federal,
            party = t.party,
            photoUrl = "https://unitedstates.github.io/images/congress/225x275/$bioguide.jpg",
            phone = t.phone,
            address = t.address,
            website = t.url,
            contactForm = t.contactForm,
            servingSince = terms[i].start,
            termEnds = t.end,
            districtOffices = offices[bioguide].orEmpty().mapNotNull { o ->
                val city = o.city ?: return@mapNotNull null
                val street = listOfNotNull(o.building, o.address, o.suite).joinToString(", ")
                DistrictOffice(
                    city = city,
                    address = listOf(street, "$city, ${o.state ?: ""} ${o.zip ?: ""}".trim()).filter { it.isNotBlank() }.joinToString("\n"),
                    phone = o.phone,
                )
            },
            links = listOfNotNull(
                Link("Official biography", "https://bioguide.congress.gov/search/bio/$bioguide"),
                Link("Bills and votes on Congress.gov", "https://www.congress.gov/member/${name.first.lowercase()}-${name.last.lowercase()}/$bioguide"),
                id.govtrack?.let { Link("Voting record on GovTrack", "https://www.govtrack.us/congress/members/$it") },
                id.ballotpedia?.let { Link("Ballotpedia", "https://ballotpedia.org/" + it.replace(' ', '_')) },
                id.wikipedia?.let { Link("Wikipedia", "https://en.wikipedia.org/wiki/" + it.replace(' ', '_')) },
            ),
            social = Social.links(social[bioguide].orEmpty()),
            sourceName = SOURCE_NAME,
            sourceUrl = SOURCE_URL,
        )
    }
}

object StateNames {
    private val names = mapOf(
        "AL" to "Alabama", "AK" to "Alaska", "AZ" to "Arizona", "AR" to "Arkansas", "CA" to "California",
        "CO" to "Colorado", "CT" to "Connecticut", "DE" to "Delaware", "FL" to "Florida", "GA" to "Georgia",
        "HI" to "Hawaii", "ID" to "Idaho", "IL" to "Illinois", "IN" to "Indiana", "IA" to "Iowa",
        "KS" to "Kansas", "KY" to "Kentucky", "LA" to "Louisiana", "ME" to "Maine", "MD" to "Maryland",
        "MA" to "Massachusetts", "MI" to "Michigan", "MN" to "Minnesota", "MS" to "Mississippi", "MO" to "Missouri",
        "MT" to "Montana", "NE" to "Nebraska", "NV" to "Nevada", "NH" to "New Hampshire", "NJ" to "New Jersey",
        "NM" to "New Mexico", "NY" to "New York", "NC" to "North Carolina", "ND" to "North Dakota", "OH" to "Ohio",
        "OK" to "Oklahoma", "OR" to "Oregon", "PA" to "Pennsylvania", "RI" to "Rhode Island", "SC" to "South Carolina",
        "SD" to "South Dakota", "TN" to "Tennessee", "TX" to "Texas", "UT" to "Utah", "VT" to "Vermont",
        "VA" to "Virginia", "WA" to "Washington", "WV" to "West Virginia", "WI" to "Wisconsin", "WY" to "Wyoming",
        "DC" to "District of Columbia", "PR" to "Puerto Rico", "GU" to "Guam", "VI" to "U.S. Virgin Islands",
        "AS" to "American Samoa", "MP" to "Northern Mariana Islands",
    )

    fun of(abbr: String?): String = names[abbr] ?: abbr ?: ""

    fun isAbbreviation(text: String): Boolean = text.uppercase() in names

    /** Every state and territory, as (abbreviation, name), alphabetical by name. */
    val all: List<Pair<String, String>> get() = names.entries.map { it.key to it.value }.sortedBy { it.second }
}

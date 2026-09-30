package com.theeclecticwitch.powertothepeople.location

import com.theeclecticwitch.powertothepeople.data.AppFiles
import com.theeclecticwitch.powertothepeople.data.Http
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Where the reader lives, as the districts that decide who represents them.
 *
 * The address itself stays on the device. The only place it is ever sent is the U.S. Census
 * Bureau's geocoder, once, to turn it into districts - and the Census Bureau is the government's
 * own source for exactly that question.
 */
@Serializable
data class UserLocation(
    val enteredAddress: String,
    val matchedAddress: String,
    val stateAbbr: String,
    val stateName: String,
    val county: String? = null,
    val place: String? = null,
    val schoolDistrict: String? = null,
    /** The Congress now sitting, e.g. 119, and the reader's district in it. 0 means at-large. */
    val congress: Int,
    val congressionalDistrict: Int? = null,
    val congressionalDistrictName: String? = null,
    /** When the Census Bureau already has the next Congress's map and it differs. */
    val nextCongress: Int? = null,
    val nextCongressionalDistrict: Int? = null,
    val stateSenateDistrict: String? = null,
    val stateHouseDistrict: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val lookedUpOn: String,
) {
    val cityOrCounty: String get() = listOfNotNull(place, county).firstOrNull() ?: stateName

    val districtLabel: String
        get() = when (congressionalDistrict) {
            null -> "District unknown"
            0 -> "At-large district"
            else -> "District $congressionalDistrict"
        }
}

/** Which Congress is sitting on [date]. Each one starts at noon on January 3 of an odd year. */
fun congressOn(date: LocalDate): Int {
    var year = date.year
    if (date.month == Month.JANUARY && date.day < 3) year -= 1
    return (year - 1789) / 2 + 1
}

fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

object LocationStore {
    private const val FILE = "location.json"
    private val state = MutableStateFlow(load())
    val location: StateFlow<UserLocation?> = state.asStateFlow()

    private fun load(): UserLocation? = try {
        AppFiles.store.read(FILE)?.let { Http.json.decodeFromString<UserLocation>(it) }
    } catch (e: Exception) {
        null
    }

    fun save(location: UserLocation) {
        AppFiles.store.write(FILE, Http.json.encodeToString(UserLocation.serializer(), location))
        state.value = location
    }

    fun clear() {
        AppFiles.store.delete(FILE)
        state.value = null
    }
}

class AddressNotFound : Exception("That address wasn't found.")

object CensusGeocoder {
    const val SOURCE_URL = "https://geocoding.geo.census.gov/geocoder/"
    private const val ENDPOINT = "https://geocoding.geo.census.gov/geocoder/geographies/onelineaddress"
    private val districtLayer = Regex("""^(\d+)(st|nd|rd|th) Congressional Districts$""")

    private class Match(val matchedAddress: String, val lat: Double?, val lon: Double?, val layers: JsonObject)

    private suspend fun match(address: String, vintage: String): Match? {
        val text = Http.getText(ENDPOINT) {
            url.parameters.append("address", address)
            url.parameters.append("benchmark", "Public_AR_Current")
            url.parameters.append("vintage", vintage)
            url.parameters.append("layers", "all")
            url.parameters.append("format", "json")
        }
        val root = Http.json.parseToJsonElement(text).jsonObject
        val matches = root["result"]?.jsonObject?.get("addressMatches")?.jsonArray ?: return null
        val first = matches.firstOrNull()?.jsonObject ?: return null
        val coords = first["coordinates"]?.jsonObject
        return Match(
            matchedAddress = first["matchedAddress"]?.jsonPrimitive?.content ?: address,
            lat = coords?.get("y")?.jsonPrimitive?.doubleOrNull,
            lon = coords?.get("x")?.jsonPrimitive?.doubleOrNull,
            layers = first["geographies"]?.jsonObject ?: JsonObject(emptyMap()),
        )
    }

    private fun JsonObject.layer(predicate: (String) -> Boolean): JsonObject? =
        entries.firstOrNull { predicate(it.key) }?.value?.let { (it as? JsonArray)?.firstOrNull()?.jsonObject }

    private fun JsonObject.field(name: String): String? = this[name]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

    /** The Congress a layer's map is for, and the district number in it (0 for at-large or a delegate). */
    private fun Match.congressionalDistrict(): Triple<Int, Int, String?>? {
        val entry = layers.entries.firstOrNull { districtLayer.matches(it.key) } ?: return null
        val congress = districtLayer.find(entry.key)!!.groupValues[1].toInt()
        val obj = (entry.value as? JsonArray)?.firstOrNull()?.jsonObject ?: return null
        val code = obj.field("GEOID")?.takeLast(2)?.toIntOrNull() ?: return null
        // 00 is an at-large state; 98 is a non-voting delegate's district (D.C., the territories).
        val district = if (code == 0 || code == 98) 0 else code
        return Triple(congress, district, obj.field("NAME"))
    }

    suspend fun lookup(address: String): UserLocation {
        val date = today()
        val sitting = congressOn(date)
        val latest = match(address, "Current_Current") ?: throw AddressNotFound()
        val latestDistrict = latest.congressionalDistrict()

        // The Census Bureau publishes the next Congress's map before that Congress sits. The
        // members serving now were elected on the older map, so that is the one that says who
        // represents this address today.
        var current = latestDistrict?.takeIf { it.first == sitting }
        val next = latestDistrict?.takeIf { it.first > sitting }
        if (current == null) {
            for (year in date.year downTo date.year - 2) {
                val older = try {
                    match(address, "ACS${year}_Current")
                } catch (e: Exception) {
                    null
                } ?: continue
                val d = older.congressionalDistrict()
                if (d != null && d.first == sitting) {
                    current = d
                    break
                }
            }
        }

        val state = latest.layers.layer { it == "States" }
        val upper = latest.layers.layer { it.endsWith("State Legislative Districts - Upper") }
        val lower = latest.layers.layer { it.endsWith("State Legislative Districts - Lower") }
        return UserLocation(
            enteredAddress = address.trim(),
            matchedAddress = latest.matchedAddress,
            stateAbbr = state?.field("STUSAB") ?: "",
            stateName = state?.field("NAME") ?: "",
            county = latest.layers.layer { it == "Counties" }?.field("NAME"),
            place = latest.layers.layer { it == "Incorporated Places" }?.field("BASENAME")
                ?: latest.layers.layer { it == "County Subdivisions" }?.field("NAME"),
            schoolDistrict = latest.layers.layer { it.endsWith("School Districts") }?.field("NAME"),
            congress = sitting,
            congressionalDistrict = current?.second,
            congressionalDistrictName = current?.third,
            nextCongress = next?.first?.takeIf { next.second != current?.second },
            nextCongressionalDistrict = next?.second?.takeIf { it != current?.second },
            stateSenateDistrict = upper?.field("NAME"),
            stateHouseDistrict = lower?.field("NAME"),
            latitude = latest.lat,
            longitude = latest.lon,
            lookedUpOn = date.toString(),
        )
    }
}

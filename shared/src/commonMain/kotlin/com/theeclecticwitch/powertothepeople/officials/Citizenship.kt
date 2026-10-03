package com.theeclecticwitch.powertothepeople.officials

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.theeclecticwitch.powertothepeople.congress.CongressData
import com.theeclecticwitch.powertothepeople.data.CachedSource
import com.theeclecticwitch.powertothepeople.data.Http
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.SourceLine
import kotlin.time.Duration.Companion.days
import kotlinx.serialization.Serializable

/** Where a member was born, as Wikidata records it. */
@Serializable
data class Birthplace(val place: String? = null, val state: String? = null, val country: String? = null, val wikidata: String? = null)

@Serializable
private class Birthplaces(val members: Map<String, Birthplace> = emptyMap())

/** "Suitland, Maryland" for a U.S. birth, "Karachi, Pakistan" for one abroad. */
fun birthplaceText(b: Birthplace): String? {
    val place = b.place ?: return null
    val wider = if (b.country == "United States") b.state ?: b.country else b.country
    return if (wider == null || wider == place) place else "$place, $wider"
}

fun bornAbroad(b: Birthplace): Boolean = b.country != null && b.country != "United States"

/**
 * The citizenship the Constitution requires for an office, with where it says so, or null when the
 * Constitution sets none (delegates and the resident commissioner, whose seats are set up by law).
 */
fun citizenshipRequirement(office: String): String? = when {
    office.startsWith("President") ->
        "The President must be a natural-born U.S. citizen. (Constitution, Article II, Section 1)"
    office.startsWith("Vice President") ->
        "The Vice President must meet the same requirements as the President, including being a natural-born " +
            "U.S. citizen. (Constitution, 12th Amendment)"
    office.contains("Delegate") || office.contains("Resident Commissioner") -> null
    office.contains("Senator") ->
        "A senator must have been a U.S. citizen for at least 9 years. (Constitution, Article I, Section 3)"
    office.contains("Representative") ->
        "A representative must have been a U.S. citizen for at least 7 years. (Constitution, Article I, Section 2)"
    else -> null
}

object BirthplaceData {
    private val source = CachedSource("pd_birthplaces.json", 7.days) { Http.getText("${CongressData.BASE}/birthplaces.json") }

    /** A member's birthplace by bioguide id, or null if Wikidata has none or the file can't be had. */
    suspend fun forMember(bioguide: String): Birthplace? = try {
        Http.json.decodeFromString<Birthplaces>(source.get().text).members[bioguide]
    } catch (e: Exception) {
        null
    }
}

/** On an official's page: the citizenship their office requires, and where they were born. */
@Composable
fun CitizenshipCard(official: Official) {
    val member = !official.id.startsWith("exec:")
    val born by produceState<Birthplace?>(null, official.id) {
        if (member) value = BirthplaceData.forMember(official.id)
    }
    val requirement = citizenshipRequirement(official.office)
    InfoCard(title = "Citizenship") {
        requirement?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        born?.let { b ->
            birthplaceText(b)?.let { Text("Born in $it.", style = MaterialTheme.typography.bodyLarge) }
            if (bornAbroad(b)) {
                Text(
                    "Being born outside the United States says nothing by itself about citizenship: a child of U.S. " +
                        "citizens born abroad is usually a citizen from birth, and others become citizens by naturalization.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SourceLine("Wikidata", b.wikidata, "birthplace")
        }
        Text(
            "No law requires members of Congress, the President or the Vice President to report citizenship in " +
                "another country, so there is no official record of it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

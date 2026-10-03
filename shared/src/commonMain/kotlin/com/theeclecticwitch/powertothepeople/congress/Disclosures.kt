package com.theeclecticwitch.powertothepeople.congress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.data.CachedSource
import com.theeclecticwitch.powertothepeople.data.Http
import com.theeclecticwitch.powertothepeople.ui.Format
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.SourceLine
import com.theeclecticwitch.powertothepeople.ui.openSafely
import kotlin.time.Duration.Companion.hours
import kotlinx.serialization.Serializable

/** One financial disclosure filing, as listed by the House Clerk or the Secretary of the Senate. */
@Serializable
data class Disclosure(val kind: String, val title: String, val filed: String, val url: String)

@Serializable
private class DisclosuresFile(val members: Map<String, List<Disclosure>> = emptyMap())

object Disclosures {
    private val source = CachedSource("pd_disclosures.json", 12.hours) { Http.getText("${CongressData.BASE}/disclosures.json") }

    /** A member's filings, newest first; null when the list can't be had (not published yet, or offline). */
    suspend fun of(bioguide: String): List<Disclosure>? = try {
        Http.json.decodeFromString<DisclosuresFile>(source.get().text).members[bioguide].orEmpty()
    } catch (e: Exception) {
        null
    }
}

/**
 * On a member's page: the stock trade and annual reports they filed this Congress, as filed. The app lists them
 * and links the official copies; it doesn't read, total or judge what's in them.
 */
@Composable
fun DisclosuresCard(bioguide: String, chamber: String) {
    var filings by remember(bioguide) { mutableStateOf<List<Disclosure>?>(null) }
    var all by remember(bioguide) { mutableStateOf(false) }
    LaunchedEffect(bioguide) { filings = Disclosures.of(bioguide) }
    val list = filings ?: return
    val uri = LocalUriHandler.current
    InfoCard(title = "Financial disclosures") {
        val trades = list.count { it.kind == "trade" }
        Text(
            if (list.isEmpty()) "No reports listed for this Congress yet."
            else "$trades stock trade report${if (trades == 1) "" else "s"} and ${list.size - trades} annual " +
                "report${if (list.size - trades == 1) "" else "s"} filed since January 2025.",
            style = MaterialTheme.typography.bodyLarge,
        )
        (if (all) list else list.take(5)).forEach { d ->
            Column(Modifier.fillMaxWidth().clickable { openSafely(uri, d.url) }.padding(vertical = 4.dp)) {
                Text(Format.date(d.filed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    d.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                )
            }
        }
        if (list.size > 5) {
            Text(
                if (all) "Show fewer" else "Show all ${list.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.clickable { all = !all }.padding(vertical = 4.dp),
            )
        }
        Text(
            "Under the STOCK Act, members must report any trade of stocks, bonds or similar holdings over \$1,000, by " +
                "themselves, a spouse or a dependent child, within 45 days. Each year they also report their assets, " +
                "income and debts. Amounts are reported in ranges, not exact figures." +
                if (chamber == "senate") " The Senate's site asks you to accept its terms before showing a report." else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SourceLine(
            if (chamber == "senate") "Secretary of the Senate, eFD" else "Office of the Clerk, U.S. House",
            if (chamber == "senate") "https://efdsearch.senate.gov/search/" else "https://disclosures-clerk.house.gov/FinancialDisclosure",
        )
    }
}

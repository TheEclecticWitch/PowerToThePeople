package com.theeclecticwitch.powertothepeople.congress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.ui.Format
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.SourceLine
import com.theeclecticwitch.powertothepeople.ui.openSafely

/**
 * The White House's official business, as it publishes it: the Press Secretary's briefings, the President's
 * actions, and the bills signed into law. Opinion pieces and promotional videos on the same site are left out.
 */
@Composable
fun WhiteHouseCards(nav: CongressNav) {
    WhiteHouseCard(
        section = "briefings",
        title = "White House press briefings",
        note = "Videos on whitehouse.gov. The White House doesn't post written transcripts.",
        sourceUrl = "https://www.whitehouse.gov/videos/",
    )
    WhiteHouseCard(
        section = "actions",
        title = "Presidential actions",
        note = "Executive orders, proclamations, memoranda and nominations. Tap one to read it in full.",
        sourceUrl = "https://www.whitehouse.gov/presidential-actions/",
    )
    WhiteHouseCard(
        section = "signed",
        title = "Signed into law",
        note = "Tap a bill to see its page here, or the date to read the White House's notice.",
        sourceUrl = "https://www.whitehouse.gov/briefings-statements/",
        nav = nav,
    )
}

private const val SHOWN = 3

/** One section's newest items; nothing at all until there is something to show. */
@Composable
private fun WhiteHouseCard(section: String, title: String, note: String, sourceUrl: String, nav: CongressNav? = null) {
    val items by produceState<List<NewsItem>?>(null, section) {
        value = try { OfficeNews.whiteHouse(section) } catch (e: Exception) { null }
    }
    val list = items?.takeIf { it.isNotEmpty() } ?: return
    val uri = LocalUriHandler.current
    var all by remember { mutableStateOf(false) }
    InfoCard(title = title) {
        (if (all) list else list.take(SHOWN)).forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (nav != null && item.bills.isNotEmpty()) {
                SigningItem(item, nav) { openSafely(uri, item.url) }
            } else {
                Column(
                    Modifier.fillMaxWidth().clickable { openSafely(uri, item.url) }.padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        listOfNotNull(item.date?.let(Format::date), item.kind).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (!all && list.size > SHOWN) OutlinedButton(onClick = { all = true }) { Text("Show ${list.size - SHOWN} more") }
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SourceLine("The White House", sourceUrl, "checked every six hours")
    }
}

/** A signing: its date (to the White House's notice), then each bill, which opens that bill's page in the app. */
@Composable
private fun SigningItem(item: NewsItem, nav: CongressNav, onNotice: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            listOfNotNull(item.date?.let(Format::date), "${item.bills.size} " + if (item.bills.size == 1) "bill" else "bills").joinToString(" · ") + " ›",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onNotice),
        )
        item.bills.forEach { b ->
            Column(Modifier.fillMaxWidth().clickable { nav.bill(b.id) }.padding(vertical = 2.dp)) {
                Text(BillNames.label(b.id), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                b.title?.let { Text(it, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

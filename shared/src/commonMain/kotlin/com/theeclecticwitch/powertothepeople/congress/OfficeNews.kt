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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.data.CachedSource
import com.theeclecticwitch.powertothepeople.data.Http
import com.theeclecticwitch.powertothepeople.ui.Format
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.SourceLine
import com.theeclecticwitch.powertothepeople.ui.openSafely
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlin.time.Duration.Companion.hours
import kotlinx.serialization.Serializable

@Serializable
data class NewsItem(val title: String, val url: String, val date: String? = null)

@Serializable
private class NewsFile(val items: List<NewsItem> = emptyList(), val feed: String? = null, val source: String? = null)

/**
 * What offices publish themselves: a member's press releases and office news, and the White House's news,
 * gathered from their own feeds every six hours. Social media posts are deliberately not included.
 */
object OfficeNews {
    private val sources = mutableMapOf<String, CachedSource>()

    private suspend fun load(path: String): List<NewsItem>? {
        val source = sources.getOrPut(path) {
            CachedSource("pd_" + path.replace('/', '_'), 6.hours) { Http.getText("${CongressData.BASE}/$path") }
        }
        return try {
            Http.json.decodeFromString<NewsFile>(source.get().text).items
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) null else throw e
        }
    }

    /** A member's latest items, or null when their office has no working feed. */
    suspend fun forMember(bioguide: String): List<NewsItem>? = load("press/$bioguide.json")

    suspend fun whiteHouse(): List<NewsItem>? = load("press/whitehouse.json")
}

@Composable
private fun NewsCard(title: String, items: List<NewsItem>, sourceName: String, sourceUrl: String?) {
    val uri = LocalUriHandler.current
    var all by remember { mutableStateOf(false) }
    InfoCard(title = title) {
        (if (all) items else items.take(4)).forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                Modifier.fillMaxWidth().clickable { openSafely(uri, item.url) }.padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item.date?.let {
                    Text(Format.date(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!all && items.size > 4) OutlinedButton(onClick = { all = true }) { Text("Show ${items.size - 4} more") }
        Text(
            "As published by the office itself. Tap an item to read it on their site.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SourceLine(sourceName, sourceUrl, "checked every six hours")
    }
}

/** A member's press releases, when their office publishes a working feed; nothing at all otherwise. */
@Composable
fun OfficeNewsCard(bioguide: String, website: String?) {
    var items by remember(bioguide) { mutableStateOf<List<NewsItem>?>(null) }
    LaunchedEffect(bioguide) { items = try { OfficeNews.forMember(bioguide) } catch (e: Exception) { null } }
    val list = items?.takeIf { it.isNotEmpty() } ?: return
    NewsCard("News from their office", list, "Their office's website", website)
}

@Composable
fun WhiteHouseNewsCard() {
    var items by remember { mutableStateOf<List<NewsItem>?>(null) }
    LaunchedEffect(Unit) { items = try { OfficeNews.whiteHouse() } catch (e: Exception) { null } }
    val list = items?.takeIf { it.isNotEmpty() } ?: return
    NewsCard("White House news", list, "The White House", "https://www.whitehouse.gov/news/")
}

package com.theeclecticwitch.powertothepeople.briefing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.congress.CongressData
import com.theeclecticwitch.powertothepeople.data.CachedSource
import com.theeclecticwitch.powertothepeople.data.Http
import com.theeclecticwitch.powertothepeople.ui.Format
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.openSafely
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/** briefing/latest.json: this week's script, paragraph by paragraph, and the reading of it. */
@Serializable
data class WeeklyBriefing(
    val weekStarting: String,
    val weekEnding: String,
    val paragraphs: List<String> = emptyList(),
    val audio: String? = null,
    val seconds: Int? = null,
    val voice: String? = null,
)

object Briefings {
    private val source = CachedSource("pd_briefing_latest.json", 6.hours) { Http.getText("${CongressData.BASE}/briefing/latest.json") }

    /** The latest briefing, or null before the first one is published or when it can't be had. */
    suspend fun latest(): WeeklyBriefing? = try {
        Http.json.decodeFromString<WeeklyBriefing>(source.get().text)
    } catch (e: Exception) {
        null
    }

    fun audioUrl(b: WeeklyBriefing): String? = b.audio?.let { "${CongressData.BASE}/$it" }
}

/** "2 min 15 s" */
fun lengthText(seconds: Int): String = if (seconds < 60) "$seconds s" else "${seconds / 60} min ${seconds % 60} s"

/** Two bars, drawn here because the core icon set has no pause. */
private val PauseIcon: ImageVector = ImageVector.Builder("Pause", 24.dp, 24.dp, 24f, 24f).path(fill = SolidColor(androidx.compose.ui.graphics.Color.Black)) {
    moveTo(6f, 5f); lineTo(10f, 5f); lineTo(10f, 19f); lineTo(6f, 19f); close()
    moveTo(14f, 5f); lineTo(18f, 5f); lineTo(18f, 19f); lineTo(14f, 19f); close()
}.build()

data class PlayerState(val url: String? = null, val playing: Boolean = false, val positionMs: Long = 0, val durationMs: Long = 0)

/**
 * Plays one recording at a time from the web. Android and iPhone play it in the app; the desktop app
 * hands it to the browser, since Java can't play MP3s on its own.
 */
expect object AudioPlayer {
    val playsInApp: Boolean
    val state: StateFlow<PlayerState>
    fun play(url: String)
    fun pause()
}

/** On Overview, above the latest vote: the week's spoken briefing, with the words to read along. */
@Composable
fun ThisWeekCard() {
    val briefing by produceState<WeeklyBriefing?>(null) { value = Briefings.latest() }
    val b = briefing ?: return
    val url = Briefings.audioUrl(b)
    val player by AudioPlayer.state.collectAsState()
    val uri = LocalUriHandler.current
    var reading by remember { mutableStateOf(false) }
    InfoCard(title = "This Week in Congress") {
        Text("The week ending ${Format.date(b.weekEnding)}", style = MaterialTheme.typography.titleMedium)
        Text(
            listOfNotNull(b.seconds?.let { lengthText(it) }, "Read by a computer voice from public records").joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (url != null) {
            val mine = player.url == url
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    when {
                        !AudioPlayer.playsInApp -> openSafely(uri, url)
                        mine && player.playing -> AudioPlayer.pause()
                        else -> AudioPlayer.play(url)
                    }
                }) {
                    val playing = mine && player.playing
                    Icon(if (playing) PauseIcon else Icons.Default.PlayArrow, contentDescription = null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (playing) "Pause" else if (mine && player.positionMs > 0) "Resume" else "Listen")
                }
            }
            if (mine && player.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (player.positionMs.toFloat() / player.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Text(
            if (reading) "Hide the words" else "Read along ›",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.clickable { reading = !reading }.padding(vertical = 4.dp),
        )
        if (reading) {
            b.paragraphs.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

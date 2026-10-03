package com.theeclecticwitch.powertothepeople.doomsday

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.congress.CongressData
import com.theeclecticwitch.powertothepeople.data.CachedSource
import com.theeclecticwitch.powertothepeople.data.Http
import com.theeclecticwitch.powertothepeople.ui.AppTopBar
import com.theeclecticwitch.powertothepeople.ui.Format
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.ReadingColumn
import com.theeclecticwitch.powertothepeople.ui.SourceLine
import com.theeclecticwitch.powertothepeople.ui.openSafely
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.time.Duration.Companion.days
import kotlinx.serialization.Serializable

/** The clock's setting, as the Bulletin of the Atomic Scientists announced it. */
@Serializable
data class DoomsdaySetting(val seconds: Int, val set: String? = null, val source: String = Doomsday.PAGE)

object Doomsday {
    const val PAGE = "https://thebulletin.org/doomsday-clock/"
    const val SOURCE_NAME = "Bulletin of the Atomic Scientists"

    /** The setting when this version of the app was built, for a first launch with no connection. */
    val Known = DoomsdaySetting(seconds = 85, set = "2026-01-27")

    private val source = CachedSource("pd_doomsday.json", 1.days) { Http.getText("${CongressData.BASE}/doomsday.json") }

    suspend fun current(): DoomsdaySetting = try {
        Http.json.decodeFromString(source.get().text)
    } catch (e: Exception) {
        Known
    }

    /** In the Bulletin's own style: "85 seconds to midnight", or "2 minutes to midnight" for whole minutes. */
    fun words(seconds: Int): String =
        if (seconds >= 60 && seconds % 60 == 0) plural(seconds / 60, "minute") + " to midnight"
        else plural(seconds, "second") + " to midnight"

    private fun plural(n: Int, unit: String) = "$n $unit${if (n == 1) "" else "s"}"

    /** The same moment on a clock: 85 seconds to midnight is 11:58:35. */
    fun clockTime(seconds: Int): String {
        val t = 12 * 3600 - seconds
        val h = t / 3600
        val m = (t % 3600) / 60
        val s = t % 60
        return "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    }

    /** "85 seconds" alone is hard to picture, so past a minute it is also said in minutes and seconds. */
    fun spelledOut(seconds: Int): String =
        if (seconds > 60 && seconds % 60 != 0) {
            "$seconds seconds, or ${plural(seconds / 60, "minute")} and ${plural(seconds % 60, "second")} to midnight"
        } else {
            words(seconds)
        }
}

@Composable
private fun rememberDoomsday(): DoomsdaySetting {
    var setting by remember { mutableStateOf(Doomsday.Known) }
    LaunchedEffect(Unit) { setting = Doomsday.current() }
    return setting
}

/**
 * An analog clock face showing the setting, drawn rather than pictured so it is sharp at any size. The
 * hands read the actual time (11:58:35 for 85 seconds), and the words beside it say the same thing for
 * anyone who doesn't read an analog clock.
 */
@Composable
fun DoomsdayClockFace(seconds: Int, sizeDp: Int) {
    val face = MaterialTheme.colorScheme.surfaceContainerHighest
    val ink = MaterialTheme.colorScheme.onSurface
    val faint = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.error
    Canvas(
        Modifier.size(sizeDp.dp).semantics { contentDescription = "Clock reading ${Doomsday.clockTime(seconds)}, ${Doomsday.words(seconds)}" },
    ) {
        val r = min(size.width, size.height) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        fun at(angleDeg: Double, length: Float): Offset {
            val a = angleDeg * PI / 180.0
            return Offset(c.x + (length * sin(a)).toFloat(), c.y - (length * cos(a)).toFloat())
        }
        drawCircle(face, r, c)
        drawCircle(faint, r * 0.98f, c, style = Stroke(width = r * 0.03f))
        for (i in 0 until 60) {
            val hour = i % 5 == 0
            val outer = r * 0.9f
            val inner = if (hour) r * 0.76f else r * 0.84f
            drawLine(
                if (i == 0) accent else if (hour) ink else faint,
                at(i * 6.0, inner), at(i * 6.0, outer),
                strokeWidth = if (hour) r * 0.04f else r * 0.015f,
                cap = StrokeCap.Round,
            )
        }
        val t = 12 * 3600 - seconds
        val hourAngle = t / 43200.0 * 360
        val minuteAngle = (t % 3600) / 3600.0 * 360
        val secondAngle = (t % 60) / 60.0 * 360
        drawLine(ink, c, at(hourAngle, r * 0.5f), strokeWidth = r * 0.07f, cap = StrokeCap.Round)
        drawLine(ink, c, at(minuteAngle, r * 0.78f), strokeWidth = r * 0.045f, cap = StrokeCap.Round)
        drawLine(accent, at(secondAngle + 180, r * 0.15f), at(secondAngle, r * 0.85f), strokeWidth = r * 0.02f, cap = StrokeCap.Round)
        drawCircle(accent, r * 0.05f, c)
    }
}

@Composable
fun DoomsdayCard(onOpen: () -> Unit) {
    val d = rememberDoomsday()
    InfoCard(title = "The Doomsday Clock", onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DoomsdayClockFace(d.seconds, 84)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(Doomsday.words(d.seconds).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
                d.set?.let {
                    Text("Set ${Format.date(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("What it means ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
fun DoomsdayScreen(onBack: () -> Unit) {
    val d = rememberDoomsday()
    val uri = LocalUriHandler.current
    Scaffold(topBar = { AppTopBar("The Doomsday Clock", onBack) }) { padding ->
        ReadingColumn(Modifier.padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                DoomsdayClockFace(d.seconds, 240)
                Text(
                    Doomsday.spelledOut(d.seconds).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "On a clock, that is ${Doomsday.clockTime(d.seconds)} - just before 12 midnight.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                d.set?.let {
                    Text("Set ${Format.date(it)}", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                }
                InfoCard(title = "What it is") {
                    Text(
                        "The Doomsday Clock is a symbol kept by the Bulletin of the Atomic Scientists, a nonprofit founded in " +
                            "1945 by scientists who had worked on the first atomic bombs. Midnight stands for a man-made global " +
                            "catastrophe. Each year, usually in January, the Bulletin's board of scientists and experts decides " +
                            "where to set the hands, weighing threats such as nuclear weapons, climate change and disruptive " +
                            "technologies.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "The setting is the Bulletin's own judgment, shown here as they announce it. Their statement explains " +
                            "their reasons.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Read the Bulletin's statement",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { openSafely(uri, d.source) }.padding(vertical = 4.dp),
                )
                SourceLine(Doomsday.SOURCE_NAME, d.source, "checked every day", Modifier.fillMaxWidth())
            }
        }
    }
}

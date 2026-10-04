package com.theeclecticwitch.powertothepeople.voting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.congress.CongressData
import com.theeclecticwitch.powertothepeople.data.CachedSource
import com.theeclecticwitch.powertothepeople.data.Http
import com.theeclecticwitch.powertothepeople.location.today
import com.theeclecticwitch.powertothepeople.officials.StateNames
import com.theeclecticwitch.powertothepeople.ui.Format
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.LoadingBox
import com.theeclecticwitch.powertothepeople.ui.SourceLine
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.Serializable

@Serializable
data class DateItem(val kind: String = "other", val date: String, val what: String)

@Serializable
data class DateSource(val name: String, val url: String)

/** One state's deadlines for a general election, copied from its election office (elections/dates/{year}.json). */
@Serializable
data class StateDates(
    val allMail: Boolean = false,
    val sameDay: Boolean = false,
    val items: List<DateItem> = emptyList(),
    val notes: List<String> = emptyList(),
    val sources: List<DateSource> = emptyList(),
)

@Serializable
private class DatesFile(val election: String? = null, val checked: String? = null, val states: Map<String, StateDates> = emptyMap())

object ElectionDates {
    private var source: Pair<Int, CachedSource>? = null

    /** The year's file, or null when none is published (a 404 and a dropped connection read the same here). */
    private suspend fun file(year: Int): DatesFile? {
        val src = source?.takeIf { it.first == year }?.second
            ?: CachedSource("pd_election_dates_$year.json", 12.hours) { Http.getText("${CongressData.BASE}/elections/dates/$year.json") }
                .also { source = year to it }
        return try { Http.json.decodeFromString<DatesFile>(src.get().text) } catch (e: Exception) { null }
    }

    /** A state's dates and the day they were last checked against its election office. */
    suspend fun forState(year: Int, state: String): Pair<StateDates, String?>? {
        val f = file(year) ?: return null
        return f.states[state.uppercase()]?.let { it to f.checked }
    }
}

/** How long until Election Day begins where the reader is, ticking each second; "today" on the day itself. */
@Composable
fun ElectionCountdown(day: LocalDate) {
    val start = remember(day) { day.atStartOfDayIn(TimeZone.currentSystemDefault()) }
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(start) {
        while (now < start) {
            delay(1000 - now.toEpochMilliseconds() % 1000)
            now = Clock.System.now()
        }
    }
    if (today() == day) {
        Text("Election Day is today. Polls close at different times in each state.", style = MaterialTheme.typography.titleMedium)
        return
    }
    if (now >= start) return
    val left = start - now
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            left.inWholeDays to "days",
            left.inWholeHours % 24 to "hours",
            left.inWholeMinutes % 60 to "minutes",
            left.inWholeSeconds % 60 to "seconds",
        ).forEach { (n, unit) ->
            Column(
                Modifier.weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(n.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(if (n == 1L) unit.dropLast(1) else unit, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            }
        }
    }
    Text(
        "until Election Day begins",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun countdownWords(days: Long): String = when {
    days < 0 -> "passed"
    days == 0L -> "today"
    days == 1L -> "tomorrow"
    else -> "in $days days"
}

/**
 * The reader's state's deadlines for this election, oldest first: registering, early voting and mail ballots.
 * Past ones stay on the list, dimmed, so the reader can see they've gone by.
 */
@Composable
fun StateDatesCard(state: String, election: LocalDate) {
    var loaded by remember(state, election) { mutableStateOf(false) }
    var dates by remember(state, election) { mutableStateOf<Pair<StateDates, String?>?>(null) }
    LaunchedEffect(state, election) {
        dates = ElectionDates.forState(election.year, state)
        loaded = true
    }
    val name = StateNames.of(state)
    InfoCard(title = "Dates to know in $name") {
        if (!loaded) {
            LoadingBox("Loading…")
            return@InfoCard
        }
        val d = dates
        if (d == null) {
            Text("$name's deadlines aren't listed in the app yet. vote.gov has them.", style = MaterialTheme.typography.bodyMedium)
            SourceLine("vote.gov", voteGovRegister(state))
            return@InfoCard
        }
        val (s, checked) = d
        val now = today()
        if (s.allMail) Text("Every registered voter in $name is mailed a ballot.", style = MaterialTheme.typography.bodyLarge)
        if (s.sameDay) Text("You can register and vote on the same day.", style = MaterialTheme.typography.bodyLarge)
        s.items.mapNotNull { i -> runCatching { LocalDate.parse(i.date) }.getOrNull()?.let { it to i } }
            .sortedBy { it.first }
            .forEach { (date, item) ->
                val days = date.toEpochDays() - now.toEpochDays()
                val color = if (days < 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        "${Format.shortDate(date)} · ${countdownWords(days)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (days < 0) color else MaterialTheme.colorScheme.secondary,
                    )
                    Text(item.what, style = MaterialTheme.typography.bodyMedium, color = color)
                }
            }
        s.notes.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Text(
            "Copied from $name's election office" + (checked?.let { " on ${Format.date(it)}" } ?: "") +
                ". Dates can change; check with them before a deadline.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        s.sources.forEach { SourceLine(it.name, it.url) }
    }
}

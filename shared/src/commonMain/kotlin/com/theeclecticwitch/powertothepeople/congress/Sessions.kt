package com.theeclecticwitch.powertothepeople.congress

import com.theeclecticwitch.powertothepeople.ui.fullWidth
import com.theeclecticwitch.powertothepeople.ui.PageColumn
import com.theeclecticwitch.powertothepeople.ui.CardPage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.location.today
import com.theeclecticwitch.powertothepeople.ui.AppTopBar
import com.theeclecticwitch.powertothepeople.ui.ErrorBox
import com.theeclecticwitch.powertothepeople.ui.Format
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.LoadingBox
import com.theeclecticwitch.powertothepeople.ui.ReadingColumn
import com.theeclecticwitch.powertothepeople.ui.SourceLine
import com.theeclecticwitch.powertothepeople.ui.openSafely
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** What the Record says about the year so far: how many days each chamber met, and when last. */
data class SessionSummary(val year: Int, val house: Int, val senate: Int, val houseLast: String?, val senateLast: String?)

fun summarize(days: List<SessionDay>, year: Int): SessionSummary {
    val inYear = days.filter { it.date.startsWith("$year-") }
    return SessionSummary(
        year = year,
        house = inYear.count { it.house },
        senate = inYear.count { it.senate },
        houseLast = days.lastOrNull { it.house }?.date,
        senateLast = days.lastOrNull { it.senate }?.date,
    )
}

private const val RECORD_NOTE = "A day in session is a day the House or Senate convened on its floor to do business: " +
    "debating, voting, or receiving bills. Much of members' other work, such as committee hearings and meetings with " +
    "people back home, happens on other days and isn't counted here. Some session days are brief \"pro forma\" " +
    "meetings held so a chamber isn't formally in recess. Counted from the daily Congressional Record, published the next day."

@Composable
private fun SessionSource() {
    SourceLine("Congressional Record, via Congress.gov", "https://www.congress.gov/congressional-record", "gathered every six hours")
}

@Composable
fun SessionCard(onOpen: () -> Unit) {
    val (load, _) = rememberSessionDays()
    InfoCard(title = "Days in session", onClick = onOpen) {
        when (val l = load) {
            Load.Loading -> LoadingBox("Loading…")
            is Load.Failed -> Text(l.message, style = MaterialTheme.typography.bodyMedium)
            is Load.Done -> if (l.value.isEmpty()) {
                Text("How many days the House and Senate have been in session will appear after the next data update.", style = MaterialTheme.typography.bodyMedium)
            } else {
                val s = summarize(l.value, today().year)
                // Plain words: a "session day" is a day the chamber convened to do business.
                Text("So far in ${s.year}:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("The House has been in session ${s.house} days.", style = MaterialTheme.typography.titleMedium)
                Text("The Senate has been in session ${s.senate} days.", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Most recently: the House on ${s.houseLast?.let { Format.date(it) } ?: "—"}, the Senate on ${s.senateLast?.let { Format.date(it) } ?: "—"}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("See the calendar ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

/** Days in session for one chamber, or both when [chamber] is null. */
@Composable
fun ChamberSessionCard(chamber: String?, onOpen: () -> Unit) {
    if (chamber == null) {
        SessionCard(onOpen)
        return
    }
    val (load, _) = rememberSessionDays()
    val name = if (chamber == "senate") "Senate" else "House"
    InfoCard(title = "Days in session", onClick = onOpen) {
        when (val l = load) {
            Load.Loading -> LoadingBox("Loading…")
            is Load.Failed -> Text(l.message, style = MaterialTheme.typography.bodyMedium)
            is Load.Done -> if (l.value.isEmpty()) {
                Text("How many days the $name has been in session will appear after the next data update.", style = MaterialTheme.typography.bodyMedium)
            } else {
                val s = summarize(l.value, today().year)
                val (days, last) = if (chamber == "senate") s.senate to s.senateLast else s.house to s.houseLast
                Text("So far in ${s.year}, the $name has been in session $days days.", style = MaterialTheme.typography.titleMedium)
                last?.let {
                    Text("Most recently on ${Format.date(it)}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("See the calendar ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun rememberSessionDays() = rememberLoad(Unit, "Couldn't load the session days. Check your connection.") { force ->
    CongressData.sessionDays(force)
}

/** A month at a time: which days the House and the Senate met, each linked to that day's Record. */
@Composable
fun SessionScreen(onBack: () -> Unit) {
    val (load, retry) = rememberSessionDays()
    val now = today()
    var month by remember { mutableStateOf(LocalDate(now.year, now.month, 1)) }
    val uri = LocalUriHandler.current
    val houseColor = MaterialTheme.colorScheme.primary
    val senateColor = MaterialTheme.colorScheme.tertiary
    Scaffold(topBar = { AppTopBar("Days in session", onBack) }) { padding ->
        PageColumn(Modifier.padding(padding)) {
            when (val l = load) {
                Load.Loading -> LoadingBox()
                is Load.Failed -> ErrorBox(l.message, retry)
                is Load.Done -> {
                    val byDate = l.value.associateBy { it.date }
                    val s = summarize(l.value, month.year)
                    CardPage(spacing = 12.dp) {
                        InfoCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { month = month.minus(DatePeriod(months = 1)) }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month")
                            }
                            Text(
                                "${Format.monthName(month.month.ordinal + 1)} ${month.year}",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            IconButton(onClick = { month = month.plus(DatePeriod(months = 1)) }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
                            }
                        }
                        Row(Modifier.fillMaxWidth()) {
                            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                        // Sunday-first weeks: isoDayNumber is 1 for Monday through 7 for Sunday.
                        val lead = month.dayOfWeek.isoDayNumber % 7
                        val next = month.plus(DatePeriod(months = 1))
                        val length = (next.toEpochDays() - month.toEpochDays()).toInt()
                        val cells = List(lead) { null } + (1..length).map { LocalDate(month.year, month.month, it) }
                        cells.chunked(7).forEach { week ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                (0 until 7).forEach { i ->
                                    val day = week.getOrNull(i)
                                    Box(Modifier.weight(1f).aspectRatio(0.85f)) {
                                        if (day != null) {
                                            val d = byDate[day.toString()]
                                            DayCell(day.day, d, day == now, houseColor, senateColor) {
                                                d?.record?.let { openSafely(uri, it) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Legend(houseColor, "House in session")
                            Legend(senateColor, "Senate in session")
                        }
                        }
                        InfoCard(title = "${month.year} so far") {
                            Text("The House was in session ${s.house} days; the Senate ${s.senate} days.", style = MaterialTheme.typography.bodyLarge)
                            Text(RECORD_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Tap a day to open that day's Record.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SessionSource()
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(number: Int, day: SessionDay?, isToday: Boolean, house: Color, senate: Color, onClick: () -> Unit) {
    val met = day != null && (day.house || day.senate)
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
            .background(if (met) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .clickable(enabled = met, onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            number.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (day?.house == true) Dot(house)
            if (day?.senate == true) Dot(senate)
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

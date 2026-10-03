package com.theeclecticwitch.powertothepeople.civics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.data.Http
import com.theeclecticwitch.powertothepeople.location.LocationStore
import com.theeclecticwitch.powertothepeople.officials.JsonFileState
import com.theeclecticwitch.powertothepeople.officials.rememberDelegation
import com.theeclecticwitch.powertothepeople.officials.rememberStateDelegation
import com.theeclecticwitch.powertothepeople.shared.resources.Res
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.LoadingBox
import com.theeclecticwitch.powertothepeople.ui.SourceLine
import com.theeclecticwitch.powertothepeople.ui.openSafely
import kotlinx.serialization.Serializable

@Serializable
data class TestQuestion(
    val n: Int,
    val section: String,
    val part: String,
    val q: String,
    val answers: List<String>,
    /** One of the 20 questions for applicants 65 or older with 20 years as permanent residents. */
    val senior: Boolean = false,
    /** Answers that depend on the reader's location or on who holds an office now. */
    val dynamic: String? = null,
)

@Serializable
data class CivicsTest(
    val title: String,
    val form: String,
    val source: String,
    val updates: String,
    val asked: Int,
    val toPass: Int,
    val seniorAsked: Int,
    val seniorToPass: Int,
    val questions: List<TestQuestion>,
)

@Serializable
data class TestScores(val best: Int? = null, val last: Int? = null, val taken: Int = 0)

/** The official USCIS civics test (2025 version, form M-1778), bundled with the app so it works offline. */
object CitizenshipTest {
    private var loaded: CivicsTest? = null
    private val scores = JsonFileState("my_civics_scores.json", TestScores.serializer(), TestScores())
    val scoresFlow = scores.flow

    suspend fun load(): CivicsTest =
        loaded ?: Http.json.decodeFromString<CivicsTest>(Res.readBytes("files/civics_test.json").decodeToString()).also { loaded = it }

    fun record(score: Int) = scores.update { TestScores(best = maxOf(it.best ?: 0, score), last = score, taken = it.taken + 1) }

    /** State capitals, for "What is the capital of your state?". */
    val capitals = mapOf(
        "AL" to "Montgomery", "AK" to "Juneau", "AZ" to "Phoenix", "AR" to "Little Rock", "CA" to "Sacramento",
        "CO" to "Denver", "CT" to "Hartford", "DE" to "Dover", "FL" to "Tallahassee", "GA" to "Atlanta",
        "HI" to "Honolulu", "ID" to "Boise", "IL" to "Springfield", "IN" to "Indianapolis", "IA" to "Des Moines",
        "KS" to "Topeka", "KY" to "Frankfort", "LA" to "Baton Rouge", "ME" to "Augusta", "MD" to "Annapolis",
        "MA" to "Boston", "MI" to "Lansing", "MN" to "Saint Paul", "MS" to "Jackson", "MO" to "Jefferson City",
        "MT" to "Helena", "NE" to "Lincoln", "NV" to "Carson City", "NH" to "Concord", "NJ" to "Trenton",
        "NM" to "Santa Fe", "NY" to "Albany", "NC" to "Raleigh", "ND" to "Bismarck", "OH" to "Columbus",
        "OK" to "Oklahoma City", "OR" to "Salem", "PA" to "Harrisburg", "RI" to "Providence", "SC" to "Columbia",
        "SD" to "Pierre", "TN" to "Nashville", "TX" to "Austin", "UT" to "Salt Lake City", "VT" to "Montpelier",
        "VA" to "Richmond", "WA" to "Olympia", "WV" to "Charleston", "WI" to "Madison", "WY" to "Cheyenne",
        "PR" to "San Juan", "GU" to "Hagåtña", "VI" to "Charlotte Amalie", "AS" to "Pago Pago", "MP" to "Saipan",
    )

    /** A practice test as USCIS gives it: 20 questions at random (or 10 of the starred 20 for the 65/20 rule). */
    fun practice(test: CivicsTest, senior: Boolean): List<TestQuestion> =
        (if (senior) test.questions.filter { it.senior } else test.questions).shuffled().take(if (senior) test.seniorAsked else test.asked)
}

/** The answer for this reader, where it depends on them; null where USCIS's own answers stand alone. */
@Composable
private fun personalAnswer(q: TestQuestion): String? {
    val location by LocationStore.location.collectAsState()
    val (federal, _, _) = rememberDelegation(location)
    val (state, _, _) = rememberStateDelegation(location)
    val loc = location
    val noLocation = "Set your location in Settings and the app will show yours here."
    return when (q.dynamic) {
        "senators" -> if (loc == null) noLocation else federal?.senators?.joinToString(" or ") { it.name }?.ifBlank { null }
        "representative" -> if (loc == null) noLocation else federal?.representative?.name
        "governor" -> if (loc == null) noLocation else state?.executives?.firstOrNull { it.office.startsWith("Governor") }?.name
        "capital" -> if (loc == null) noLocation else CitizenshipTest.capitals[loc.stateAbbr]
        "president" -> federal?.president?.name
        "vicePresident" -> federal?.vicePresident?.name
        else -> null
    }
}

@Composable
private fun AnswerBlock(q: TestQuestion, updates: String) {
    val uri = LocalUriHandler.current
    val mine = personalAnswer(q)
    if (mine != null) {
        Text("For you: $mine", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
    q.answers.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
    if (q.dynamic in setOf("speaker", "chiefJustice", "president", "vicePresident")) {
        Text(
            "Check the official answer at USCIS ›",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable { openSafely(uri, updates) }.padding(vertical = 2.dp),
        )
    }
}

/** The Civics tab's third part: practice the real citizenship test, or study all 128 questions. */
@Composable
fun CitizenshipTestTab() {
    val test = produceState<CivicsTest?>(null) { value = CitizenshipTest.load() }.value
    var mode by rememberSaveable { mutableStateOf<String?>(null) }
    if (test == null) {
        LoadingBox()
        return
    }
    when (mode) {
        "practice" -> PracticeTest(test, senior = false) { mode = null; PracticeSession.clear() }
        "senior" -> PracticeTest(test, senior = true) { mode = null; PracticeSession.clear() }
        "study" -> StudyAll(test) { mode = null }
        else -> TestHome(test) { mode = it }
    }
}

@Composable
private fun TestHome(test: CivicsTest, onStart: (String) -> Unit) {
    val scores by CitizenshipTest.scoresFlow.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Could you pass the citizenship test?", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Everyone who becomes a U.S. citizen by naturalization takes this civics test. An officer asks up to " +
                "${test.asked} of these ${test.questions.size} official questions out loud, and ${test.toPass} correct answers pass. " +
                "Try it yourself: answer each question in your head, then check.",
            style = MaterialTheme.typography.bodyLarge,
        )
        scores.best?.let {
            Text(
                "Your best: $it of ${test.asked}" + (scores.last?.let { l -> " · last time: $l" } ?: "") + " · taken ${scores.taken} time${if (scores.taken == 1) "" else "s"}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Button(onClick = { onStart("practice") }, modifier = Modifier.fillMaxWidth()) { Text("Take a practice test (${test.asked} questions)") }
        OutlinedButton(onClick = { onStart("study") }, modifier = Modifier.fillMaxWidth()) { Text("Study all ${test.questions.size} questions") }
        OutlinedButton(onClick = { onStart("senior") }, modifier = Modifier.fillMaxWidth()) { Text("The 20 questions for ages 65 and up") }
        Text(
            "Applicants 65 or older who have been permanent residents for 20 years or more study only ${test.seniorAsked * 2} " +
                "starred questions, may take the test in their own language, and need ${test.seniorToPass} of ${test.seniorAsked}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SourceLine("U.S. Citizenship and Immigration Services, ${test.title}, form ${test.form}", test.source)
    }
}

/**
 * A practice test in progress. Kept outside the screen so swiping to another Civics tab and back, or
 * scrolling the tabs round, doesn't lose your place.
 */
private object PracticeSession {
    var senior by mutableStateOf(false)
    var questions by mutableStateOf<List<TestQuestion>>(emptyList())
    var index by mutableStateOf(0)
    var shown by mutableStateOf(false)
    var right by mutableStateOf(0)
    var missed by mutableStateOf(listOf<TestQuestion>())
    var finished by mutableStateOf(false)

    fun ensure(test: CivicsTest, forSeniors: Boolean) {
        if (questions.isEmpty() || senior != forSeniors) {
            clear()
            senior = forSeniors
            questions = CitizenshipTest.practice(test, forSeniors)
        }
    }

    fun clear() {
        questions = emptyList(); index = 0; shown = false; right = 0; missed = emptyList(); finished = false
    }
}

@Composable
private fun PracticeTest(test: CivicsTest, senior: Boolean, onDone: () -> Unit) {
    PracticeSession.ensure(test, senior)
    val session = PracticeSession
    val questions = session.questions
    val needed = if (senior) test.seniorToPass else test.toPass
    var index by session::index
    var shown by session::shown
    var right by session::right
    var missed by session::missed
    var finished by session::finished

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (finished) {
            val passed = right >= needed
            Text(if (passed) "You passed." else "Not quite this time.", style = MaterialTheme.typography.headlineSmall)
            Text("You answered $right of ${questions.size} correctly. $needed are needed to pass.", style = MaterialTheme.typography.titleMedium)
            if (missed.isNotEmpty()) {
                Text("Worth another look", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                missed.forEach { q ->
                    InfoCard {
                        Text("${q.n}. ${q.q}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        AnswerBlock(q, test.updates)
                    }
                }
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            return@Column
        }
        val q = questions[index]
        Text("Question ${index + 1} of ${questions.size} · ${q.part}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(q.q, style = MaterialTheme.typography.headlineSmall)
        if (!shown) {
            Text("Answer it in your head, then check.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { shown = true }, modifier = Modifier.fillMaxWidth()) { Text("Show the answer") }
        } else {
            InfoCard(title = if (q.answers.size > 1) "Any of these is correct" else "The answer") { AnswerBlock(q, test.updates) }
            Text("Did you get it?", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fun next(correct: Boolean) {
                    if (correct) right++ else missed = missed + q
                    shown = false
                    if (index == questions.lastIndex) {
                        finished = true
                        if (!senior) CitizenshipTest.record(right)
                    } else {
                        index++
                    }
                }
                Button(onClick = { next(true) }, modifier = Modifier.weight(1f)) { Text("I got it") }
                OutlinedButton(onClick = { next(false) }, modifier = Modifier.weight(1f)) { Text("I missed it") }
            }
        }
        TextButton(onClick = onDone) { Text("Stop the test") }
    }
}

@Composable
private fun StudyAll(test: CivicsTest, onDone: () -> Unit) {
    var open by remember { mutableStateOf(setOf<Int>()) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row {
                TextButton(onClick = onDone) { Text("‹ Back") }
            }
            Text("Tap a question to see its answer.", style = MaterialTheme.typography.bodyMedium)
        }
        test.questions.groupBy { "${it.section}: ${it.part}" }.forEach { (heading, qs) ->
            item(key = heading) {
                Text(heading, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            }
            items(qs, key = { it.n }) { q ->
                val expanded = q.n in open
                InfoCard(onClick = { open = if (expanded) open - q.n else open + q.n }) {
                    Text(
                        "${q.n}. ${q.q}" + if (q.senior) "  ★" else "",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (expanded) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        AnswerBlock(q, test.updates)
                    }
                }
            }
        }
        item {
            Text("★ One of the 20 questions for applicants 65 and older.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            SourceLine("U.S. Citizenship and Immigration Services, form ${test.form}", test.source)
        }
    }
}

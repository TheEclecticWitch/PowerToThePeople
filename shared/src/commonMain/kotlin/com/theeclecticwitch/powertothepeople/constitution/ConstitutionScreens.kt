package com.theeclecticwitch.powertothepeople.constitution

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theeclecticwitch.powertothepeople.ui.AppTopBar
import com.theeclecticwitch.powertothepeople.ui.CenteredMessage
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.LoadingBox
import com.theeclecticwitch.powertothepeople.ui.ReadingColumn
import com.theeclecticwitch.powertothepeople.ui.ReadingWidth
import com.theeclecticwitch.powertothepeople.ui.SourceLine
import com.theeclecticwitch.powertothepeople.ui.Tag
import com.theeclecticwitch.powertothepeople.ui.theme.LocalAppFonts
import com.theeclecticwitch.powertothepeople.ui.theme.foundingTextStyle

@Composable
private fun rememberConstitution(): Constitution? =
    produceState<Constitution?>(null) { value = ConstitutionRepository.load() }.value

/** The table of contents: the Preamble, the seven Articles, and the twenty-seven Amendments. */
@Composable
fun ConstitutionScreen(
    onArticle: (Int) -> Unit,
    onAmendment: (Int) -> Unit,
    onSignatures: () -> Unit,
    onOriginal: (Int) -> Unit,
    onHowGovernment: () -> Unit = {},
    /** Opened from the Bill of Rights card on Today: start at the Bill of Rights. */
    startAtBillOfRights: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    val doc = rememberConstitution()
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(doc, startAtBillOfRights) {
        // Rows before it: search, the documents card, the government card, the Preamble, the Articles heading, each Article, the signatures.
        if (doc != null && startAtBillOfRights) listState.scrollToItem(6 + doc.articles.size)
    }
    Scaffold(topBar = { AppTopBar(if (startAtBillOfRights) "The Bill of Rights" else "The Constitution", onBack) }) { padding ->
        if (doc == null) {
            LoadingBox()
            return@Scaffold
        }
        val hits = remember(doc, query) { doc.search(query) }
        ReadingColumn(Modifier.padding(padding)) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Search the Constitution") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "Clear search") }
                            }
                        },
                    )
                }
                if (query.trim().length >= 2) {
                    item {
                        Text(
                            if (hits.isEmpty()) "No matches for \"${query.trim()}\"" else "${hits.size} matches",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(hits) { hit ->
                        InfoCard(onClick = {
                            hit.article?.let(onArticle)
                            hit.amendment?.let(onAmendment)
                        }) {
                            Text(hit.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(hit.snippet, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    return@LazyColumn
                }
                item { OriginalsCard(onOriginal) }
                item {
                    InfoCard(title = "How our government works", onClick = onHowGovernment) {
                        Text(
                            "The government the Constitution creates: the three branches, what each does, and how they check one another.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                item { PreambleCard(doc) }
                item { GroupHeading("The Articles", "The original Constitution, signed ${doc.signed}") }
                items(doc.articles) { a ->
                    ContentsRow("Article ${a.roman}", a.title, a.summary) { onArticle(a.number) }
                }
                item { ContentsRow("Signatures", "The signers", "The thirty-nine delegates who signed, by state.", onSignatures) }
                item { GroupHeading("The Bill of Rights", "Amendments I–X, ratified December 15, 1791") }
                items(doc.amendments.filter { it.isBillOfRights }) { a ->
                    ContentsRow("Amendment ${a.roman}", a.title, a.summary) { onAmendment(a.number) }
                }
                item { GroupHeading("Later Amendments", "Amendments XI–XXVII, 1795–1992") }
                items(doc.amendments.filter { !it.isBillOfRights }) { a ->
                    ContentsRow("Amendment ${a.roman}", a.title, a.summary) { onAmendment(a.number) }
                }
                item {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(doc.spellingNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SourceLine("National Archives", doc.sourceUrl, "Constitution transcription")
                    }
                }
            }
        }
    }
}

@Composable
private fun PreambleCard(doc: Constitution) {
    InfoCard(title = "The Preamble") {
        SelectionContainer {
            Text(
                doc.preamble,
                style = foundingTextStyle().copy(fontSize = 20.sp, lineHeight = 32.sp, fontStyle = FontStyle.Italic),
            )
        }
    }
}

@Composable
private fun GroupHeading(title: String, subtitle: String) {
    Column(Modifier.padding(top = 14.dp, bottom = 2.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ContentsRow(label: String, title: String, summary: String, onClick: () -> Unit) {
    InfoCard(onClick = onClick) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One Article, section by section, with a note wherever a later amendment changed the text. */
@Composable
fun ArticleScreen(
    number: Int,
    onBack: () -> Unit,
    onArticle: (Int) -> Unit,
    onAmendment: (Int) -> Unit,
) {
    val doc = rememberConstitution()
    val article = doc?.articles?.firstOrNull { it.number == number }
    Scaffold(topBar = { AppTopBar(if (article != null) "Article ${article.roman}" else "Article", onBack) }) { padding ->
        if (doc == null) {
            LoadingBox(); return@Scaffold
        }
        if (article == null) {
            CenteredMessage("There is no Article $number."); return@Scaffold
        }
        val scroll = rememberScrollState()
        LaunchedEffect(number) { scroll.scrollTo(0) }
        ReadingColumn(Modifier.padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                DocumentHeading("Article ${article.roman}", article.title, article.summary)
                for (section in article.sections) {
                    if (section.number > 0) {
                        Column {
                            Text(
                                "SECTION ${section.number}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Text(section.title, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    for (clause in section.clauses) {
                        SelectionContainer { Text(clause.text, style = foundingTextStyle()) }
                        if (clause.note != null) {
                            ClauseNote(clause.note, clause.changedBy, onAmendment)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                PrevNext(
                    prevLabel = doc.articles.firstOrNull { it.number == number - 1 }?.let { "Article ${it.roman}" },
                    onPrev = { onArticle(number - 1) },
                    nextLabel = doc.articles.firstOrNull { it.number == number + 1 }?.let { "Article ${it.roman}" }
                        ?: "Amendment I",
                    onNext = { if (number < doc.articles.size) onArticle(number + 1) else onAmendment(1) },
                )
                SourceLine("National Archives", doc.sourceUrl)
            }
        }
    }
}

@Composable
private fun ClauseNote(note: String, changedBy: Int?, onAmendment: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            note,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (changedBy != null) {
            TextButton(onClick = { onAmendment(changedBy) }) { Text("Read it") }
        }
    }
}

/** One Amendment: when it was proposed and ratified, its text, and what it changed. */
@Composable
fun AmendmentScreen(number: Int, onBack: () -> Unit, onAmendment: (Int) -> Unit, onArticle: (Int) -> Unit) {
    val doc = rememberConstitution()
    val amendment = doc?.amendments?.firstOrNull { it.number == number }
    Scaffold(topBar = { AppTopBar(if (amendment != null) "Amendment ${amendment.roman}" else "Amendment", onBack) }) { padding ->
        if (doc == null) {
            LoadingBox(); return@Scaffold
        }
        if (amendment == null) {
            CenteredMessage("There is no Amendment $number."); return@Scaffold
        }
        val scroll = rememberScrollState()
        LaunchedEffect(number) { scroll.scrollTo(0) }
        ReadingColumn(Modifier.padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                DocumentHeading(
                    if (amendment.isBillOfRights) "Amendment ${amendment.roman} · Bill of Rights" else "Amendment ${amendment.roman}",
                    amendment.title,
                    amendment.summary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag(amendment.proposed.removeSuffix("."))
                }
                Tag(amendment.ratified.removeSuffix("."))
                for (section in amendment.sections) {
                    if (section.number > 0) {
                        Text("SECTION ${section.number}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    }
                    for (p in section.paragraphs) {
                        SelectionContainer { Text(p, style = foundingTextStyle()) }
                    }
                }
                if (amendment.notes.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    for (n in amendment.notes) {
                        Text(n, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PrevNext(
                    prevLabel = if (number > 1) "Amendment ${doc.amendments[number - 2].roman}" else "Article VII",
                    onPrev = { if (number > 1) onAmendment(number - 1) else onArticle(7) },
                    nextLabel = doc.amendments.firstOrNull { it.number == number + 1 }?.let { "Amendment ${it.roman}" },
                    onNext = { onAmendment(number + 1) },
                )
                SourceLine(
                    "National Archives",
                    if (amendment.isBillOfRights) doc.billOfRightsSourceUrl else doc.amendmentsSourceUrl,
                )
            }
        }
    }
}

/** The attestation and the signatures, as the Archives set them out. */
@Composable
fun SignaturesScreen(onBack: () -> Unit) {
    val doc = rememberConstitution()
    Scaffold(topBar = { AppTopBar("Signatures", onBack) }) { padding ->
        if (doc == null) {
            LoadingBox(); return@Scaffold
        }
        ReadingColumn(Modifier.padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DocumentHeading("Signed ${doc.signed}", "The Signers", "Delegates to the Constitutional Convention in Philadelphia, listed by state.")
                // The first three lines are the corrections note, the attestation and "done in Convention".
                val prose = doc.closing.take(3)
                val names = doc.closing.drop(3)
                for (p in prose) SelectionContainer { Text(p, style = foundingTextStyle().copy(fontSize = 16.sp)) }
                Spacer(Modifier.height(8.dp))
                val states = setOf(
                    "Delaware", "Maryland", "Virginia", "North Carolina", "South Carolina", "Georgia",
                    "New Hampshire", "Massachusetts", "Connecticut", "New York", "New Jersey", "Pennsylvania",
                )
                for (line in names) {
                    if (line in states) {
                        Text(
                            line.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    } else {
                        Text(line, style = foundingTextStyle().copy(fontStyle = FontStyle.Italic))
                    }
                }
                Spacer(Modifier.height(12.dp))
                SourceLine("National Archives", doc.sourceUrl)
            }
        }
    }
}

@Composable
private fun DocumentHeading(label: String, title: String, summary: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = LocalAppFonts.current.caslonDisplay),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun PrevNext(prevLabel: String?, onPrev: () -> Unit, nextLabel: String?, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().widthIn(max = ReadingWidth), verticalAlignment = Alignment.CenterVertically) {
        if (prevLabel != null) TextButton(onClick = onPrev) { Text("‹ $prevLabel") }
        Spacer(Modifier.weight(1f).width(8.dp))
        if (nextLabel != null) TextButton(onClick = onNext) { Text("$nextLabel ›", textAlign = TextAlign.End) }
    }
}

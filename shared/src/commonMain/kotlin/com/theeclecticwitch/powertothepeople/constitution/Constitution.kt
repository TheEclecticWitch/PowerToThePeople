package com.theeclecticwitch.powertothepeople.constitution

import com.theeclecticwitch.powertothepeople.data.Http
import com.theeclecticwitch.powertothepeople.shared.resources.Res
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/*
 * The Constitution, bundled with the app so it works with no connection at all.
 *
 * Built by tools/constitution/build_constitution.py from the National Archives transcriptions.
 * The founding text is word for word, original spelling included; the titles, one-line summaries
 * and "changed by" notes are ours and describe what the text says, not how anyone reads it.
 */

@Serializable
data class Constitution(
    val source: String,
    val sourceUrl: String,
    val amendmentsSourceUrl: String,
    val billOfRightsSourceUrl: String,
    val spellingNote: String,
    val signed: String,
    val preamble: String,
    val articles: List<Article>,
    val closing: List<String>,
    val amendments: List<Amendment>,
)

@Serializable
data class Article(
    val number: Int,
    val roman: String,
    val title: String,
    val summary: String,
    val sections: List<ArticleSection>,
)

@Serializable
data class ArticleSection(
    /** 0 for the articles that have no numbered sections (V, VI and VII). */
    val number: Int,
    val title: String,
    val clauses: List<Clause>,
)

@Serializable
data class Clause(
    val text: String,
    val note: String? = null,
    /** The amendment that changed this clause, when there is one. */
    val changedBy: Int? = null,
)

@Serializable
data class Amendment(
    val number: Int,
    val roman: String,
    val title: String,
    val summary: String,
    val proposed: String,
    val ratified: String,
    val sections: List<AmendmentSection>,
    val notes: List<String> = emptyList(),
) {
    val isBillOfRights: Boolean get() = number <= 10
}

@Serializable
data class AmendmentSection(val number: Int, val paragraphs: List<String>)

object ConstitutionRepository {
    private val lock = Mutex()
    private var loaded: Constitution? = null

    suspend fun load(): Constitution = lock.withLock {
        loaded ?: Http.json.decodeFromString<Constitution>(
            Res.readBytes("files/constitution.json").decodeToString(),
        ).also { loaded = it }
    }
}

/** One place a search term was found. */
data class SearchHit(val label: String, val snippet: String, val article: Int? = null, val amendment: Int? = null)

fun Constitution.search(query: String): List<SearchHit> {
    val q = query.trim()
    if (q.length < 2) return emptyList()
    val hits = mutableListOf<SearchHit>()
    if (preamble.contains(q, ignoreCase = true)) hits += SearchHit("Preamble", snippet(preamble, q))
    for (a in articles) {
        if (a.title.contains(q, true) || a.summary.contains(q, true)) {
            hits += SearchHit("Article ${a.roman} · ${a.title}", a.summary, article = a.number)
        }
        for (s in a.sections) for (c in s.clauses) {
            if (c.text.contains(q, ignoreCase = true)) {
                val where = if (s.number > 0) "Article ${a.roman}, Section ${s.number}" else "Article ${a.roman}"
                hits += SearchHit(where, snippet(c.text, q), article = a.number)
            }
        }
    }
    for (am in amendments) {
        val text = am.sections.flatMap { it.paragraphs }
        val found = text.firstOrNull { it.contains(q, ignoreCase = true) }
        if (found != null || am.title.contains(q, true) || am.summary.contains(q, true)) {
            hits += SearchHit(
                "Amendment ${am.roman} · ${am.title}",
                if (found != null) snippet(found, q) else am.summary,
                amendment = am.number,
            )
        }
    }
    return hits
}

/** About a hundred characters around the first match, so a result shows why it matched. */
private fun snippet(text: String, q: String): String {
    val i = text.indexOf(q, ignoreCase = true)
    if (i < 0) return text.take(140)
    val start = (i - 60).coerceAtLeast(0)
    val end = (i + q.length + 80).coerceAtMost(text.length)
    return (if (start > 0) "…" else "") + text.substring(start, end).trim() + (if (end < text.length) "…" else "")
}

package com.theeclecticwitch.powertothepeople

import com.theeclecticwitch.powertothepeople.civics.Civics
import com.theeclecticwitch.powertothepeople.civics.CommonQuestions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The civics pages point people at the Constitution; every pointer has to land on something real. */
class CivicsTest {
    private fun checkCite(article: Int?, amendment: Int?, where: String) {
        article?.let { assertTrue(it in 1..7, "$where cites Article $it") }
        amendment?.let { assertTrue(it in 1..27, "$where cites Amendment $it") }
    }

    @Test
    fun everyQuestionHasATopicAndARealCitation() {
        CommonQuestions.all.forEach { q ->
            assertTrue(q.topic in CommonQuestions.topics, "\"${q.question}\" has topic ${q.topic}")
            assertTrue(q.cite != null || q.source != null, "\"${q.question}\" has no source")
            checkCite(q.cite?.article, q.cite?.amendment, q.question)
        }
        assertEquals(CommonQuestions.all.size, CommonQuestions.all.map { it.question }.toSet().size, "a question is repeated")
    }

    @Test
    fun everyPowerCitesARealPartOfTheConstitution() {
        Civics.parts.forEach { part -> part.powers.forEach { p -> checkCite(p.cite?.article, p.cite?.amendment, part.title) } }
    }

    @Test
    fun searchFindsByWordAndTopic() {
        assertTrue(CommonQuestions.search("filibuster", null).any { it.question.contains("filibuster") })
        assertTrue(CommonQuestions.search("", "The courts").all { it.topic == "The courts" })
        assertEquals(CommonQuestions.all.size, CommonQuestions.search("", null).size)
    }
}

package com.theeclecticwitch.powertothepeople

import com.theeclecticwitch.powertothepeople.elections.Elections
import com.theeclecticwitch.powertothepeople.elections.noteKey
import com.theeclecticwitch.powertothepeople.elections.statusLine
import com.theeclecticwitch.powertothepeople.elections.tidyParty
import com.theeclecticwitch.powertothepeople.voting.Candidate
import com.theeclecticwitch.powertothepeople.voting.Contest
import com.theeclecticwitch.powertothepeople.voting.VoterInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ElectionsTest {
    @Test
    fun racesAreKeyedTheWayTheGathererWritesThem() {
        assertEquals("MD-05", Elections.houseKey("md", 5))
        assertEquals("AK-00", Elections.houseKey("AK", 0))
        assertEquals("MD-SEN", Elections.senateKey("md"))
        assertEquals(2026, Elections.cycle(2026))
        assertEquals(2028, Elections.cycle(2027))
    }

    @Test
    fun partiesAndStatusReadPlainly() {
        assertEquals("Democratic Party", tidyParty("DEMOCRATIC PARTY"))
        assertEquals("Republican", tidyParty("Republican"))
        assertNull(tidyParty(" "))
        assertEquals("Current officeholder", statusLine("incumbent"))
        assertNull(statusLine(null))
    }

    @Test
    fun notesFollowAPersonAcrossSources() {
        // The FEC writes "Steny H. Hoyer"; a ballot may write "Steny H Hoyer". Both keep the same notes.
        assertEquals(noteKey("MD", "house05", "Steny H. Hoyer"), noteKey("md", "house05", "Steny H Hoyer"))
    }

    @Test
    fun aBallotsRacesLeaveOutQuestionsAndKeepEveryCandidateAlike() {
        val info = VoterInfo(contests = listOf(
            Contest(office = "Governor", candidates = listOf(Candidate("A Person", "Party One"), Candidate("B Person", "Party Two"))),
            Contest(office = "Question 1", measure = com.theeclecticwitch.powertothepeople.voting.Measure("Question 1")),
        ))
        val races = Elections.ballotRaces(info, "MD")
        assertEquals(1, races.size)
        assertEquals(listOf("A Person", "B Person"), races.single().candidates.map { it.name })
        // The ballot doesn't say who holds the office, so no one gets a status line.
        assertEquals(true, races.single().candidates.all { it.status == null })
    }
}

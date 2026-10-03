package com.theeclecticwitch.powertothepeople

import com.theeclecticwitch.powertothepeople.congress.Action
import com.theeclecticwitch.powertothepeople.congress.Bill
import com.theeclecticwitch.powertothepeople.congress.BillVote
import com.theeclecticwitch.powertothepeople.congress.Law
import com.theeclecticwitch.powertothepeople.congress.VoteSummary
import com.theeclecticwitch.powertothepeople.congress.billProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The tracker marks a step only when the record shows it. */
class BillProgressTest {
    private fun labels(b: Bill, votes: List<BillVote> = emptyList()) =
        billProgress(b, votes).steps.map { "${it.label}:${if (it.done) "yes" else "no"}" }

    @Test
    fun aLawHasDoneEveryStep() {
        val b = Bill("119/hr/1", origin = "House", laws = listOf(Law("119-21", "Public Law")),
            latestAction = Action("2025-07-04", "Became Public Law No: 119-21."))
        assertTrue(billProgress(b, emptyList()).steps.all { it.done })
    }

    @Test
    fun aSenateBillPassedByRecordedVoteListsTheSenateFirst() {
        val b = Bill("119/s/9", origin = "Senate", latestAction = Action("2025-03-04", "Received in the House."))
        val vote = BillVote("senate", 1, VoteSummary(roll = 101, date = "2025-03-04", question = "On Passage of the Bill", result = "Bill Passed"))
        assertEquals(
            listOf("Introduced:yes", "In committee:yes", "Passed the Senate:yes", "Passed the House:no", "Sent to the President:no", "Became law:no"),
            labels(b, listOf(vote)),
        )
    }

    @Test
    fun aBillInCommitteeStopsThere() {
        val b = Bill("119/hr/1697", origin = "House", latestAction = Action("2025-02-27", "Referred to the House Committee on Ways and Means."))
        val p = billProgress(b, emptyList())
        assertEquals(1, p.current)
        assertEquals("In committee", p.steps[p.current].label)
    }

    @Test
    fun aVetoIsShownAndSentToThePresidentIsMarked() {
        val b = Bill("119/hr/5", origin = "House", stages = listOf("Introduced in House", "Passed House", "Passed Senate"),
            latestAction = Action("2026-05-01", "Vetoed by President."))
        val p = billProgress(b, emptyList())
        assertTrue(p.vetoed)
        assertEquals("Sent to the President", p.steps[p.current].label)
    }

    @Test
    fun aSimpleResolutionOnlyNeedsItsOwnChamber() {
        val b = Bill("119/hres/50", origin = "House", latestAction = Action("2025-01-10", "Agreed to in House"))
        assertEquals(listOf("Introduced:yes", "In committee:yes", "Agreed to by the House:yes"), labels(b))
    }

    @Test
    fun theHousePassingUnderSuspensionCounts() {
        // S. 2403 as published: the Senate passed it without a roll call, the House under suspension of the rules.
        val b = Bill("119/s/2403", origin = "Senate", stages = listOf("Passed Senate"),
            latestAction = Action("2026-09-16", "Motion to reconsider laid on the table Agreed to without objection."))
        val vote = BillVote("house", 2, VoteSummary(roll = 314, date = "2026-09-16", question = "On Motion to Suspend the Rules and Pass", result = "Passed"))
        assertEquals(
            listOf("Introduced:yes", "In committee:yes", "Passed the Senate:yes", "Passed the House:yes", "Sent to the President:no", "Became law:no"),
            labels(b, listOf(vote)),
        )
    }
}

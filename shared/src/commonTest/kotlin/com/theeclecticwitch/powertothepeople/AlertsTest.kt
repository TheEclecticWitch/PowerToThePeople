package com.theeclecticwitch.powertothepeople

import com.theeclecticwitch.powertothepeople.alerts.Seen
import com.theeclecticwitch.powertothepeople.alerts.actionKey
import com.theeclecticwitch.powertothepeople.alerts.latelyInTopics
import com.theeclecticwitch.powertothepeople.alerts.memberBillSteps
import com.theeclecticwitch.powertothepeople.alerts.topicAlerts
import com.theeclecticwitch.powertothepeople.alerts.newVotes
import com.theeclecticwitch.powertothepeople.alerts.voteKey
import com.theeclecticwitch.powertothepeople.congress.Action
import com.theeclecticwitch.powertothepeople.congress.MemberVote
import com.theeclecticwitch.powertothepeople.congress.SponsoredStep
import com.theeclecticwitch.powertothepeople.congress.TopicMove
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlertsTest {
    private val votes = listOf(
        MemberVote("senate", 2, 256, "Yea"),
        MemberVote("senate", 2, 255, "Nay"),
        MemberVote("senate", 2, 254, "Yea"),
    )

    @Test
    fun reportsOnlyVotesNewerThanTheLastSeen() {
        assertEquals(listOf(256, 255), newVotes(votes, "senate/2/254").map { it.roll })
        assertEquals(emptyList(), newVotes(votes, voteKey(votes.first())))
    }

    @Test
    fun saysNothingWhenTheLastSeenVoteIsGone() {
        // A rebuilt record shouldn't replay a member's whole term as news.
        assertEquals(emptyList(), newVotes(votes, "house/1/9"))
    }

    @Test
    fun aBillMovesWhenItsLatestActionChanges() {
        assertNull(actionKey(null))
        val before = actionKey(Action("2026-09-30", "Referred to the Committee on Finance."))
        val after = actionKey(Action("2026-10-02", "Passed Senate without amendment by Voice Vote."))
        assertEquals(false, before == after)
    }

    private fun move(bill: String, area: String, date: String, text: String, early: Boolean = false) =
        TopicMove(bill, "A bill", area, Action(date, text), early)

    private val moves = listOf(
        move("119/hr/1", "Health", "2026-10-02", "Passed House by Yea-Nay Vote."),
        move("119/hr/2", "Health", "2026-10-02", "Referred to the Committee on Energy and Commerce.", early = true),
        move("119/s/3", "Taxation", "2026-10-02", "Passed Senate by Voice Vote."),
        move("119/s/4", "Health", "2026-09-20", "Placed on Senate Legislative Calendar."),
    )
    private val seenHealth = Seen(topicsBaselined = setOf("Health"))

    @Test
    fun topicAlertsTellOfStepsInFollowedSubjectsOnly() {
        val (found, keys) = topicAlerts(moves, listOf("Health"), emptyList(), includeNew = false, seen = seenHealth, notBefore = "2026-09-15")
        assertEquals(listOf("119/hr/1", "119/s/4"), found.map { it.bill })
        // Every bill in the file is remembered, followed subject or not.
        assertEquals(4, keys.size)
    }

    @Test
    fun topicAlertsLeaveOutNewBillsUnlessAsked() {
        val (found, _) = topicAlerts(moves, listOf("Health"), emptyList(), includeNew = true, seen = seenHealth, notBefore = null)
        assertEquals(listOf("119/hr/1", "119/hr/2", "119/s/4"), found.map { it.bill })
    }

    @Test
    fun aSubjectsFirstLookIsSilent() {
        val (found, _) = topicAlerts(moves, listOf("Health"), emptyList(), includeNew = true, seen = Seen(), notBefore = null)
        assertEquals(emptyList(), found)
    }

    @Test
    fun topicAlertsSkipWhatWasSeenFollowedOrLongAgo() {
        val (_, keys) = topicAlerts(moves, listOf("Health"), emptyList(), false, seenHealth, null)
        val seen = seenHealth.copy(topicMoves = keys)
        // Nothing new since the last look.
        assertEquals(emptyList(), topicAlerts(moves, listOf("Health"), emptyList(), false, seen, null).first)
        // A bill followed on its own has its own alerts; a step dated before the last check is old news.
        assertEquals(emptyList(), topicAlerts(moves, listOf("Health"), listOf("119/hr/1"), false, seenHealth, "2026-09-30").first)
    }

    @Test
    fun latelyInTopicsShowsEachSubjectsLatestNewestFirst() {
        val shown = latelyInTopics(moves, listOf("Health", "Taxation"), includeNew = false, limit = 2)
        // Each subject gets its latest, even past the limit's share; new bills stay out.
        assertEquals(listOf("119/s/3", "119/hr/1"), shown.map { it.bill })
        assertEquals(listOf("119/s/3", "119/hr/1", "119/s/4"), latelyInTopics(moves, listOf("Health", "Taxation"), false, limit = 5).map { it.bill })
    }

    @Test
    fun aQuietSubjectStillShowsItsNewestBill() {
        val onlyNew = listOf(move("119/hr/9", "Animals", "2026-10-01", "Referred to the Committee on Agriculture.", early = true))
        assertEquals(listOf("119/hr/9"), latelyInTopics(onlyNew, listOf("Animals"), includeNew = false).map { it.bill })
        assertEquals(emptyList(), latelyInTopics(onlyNew, listOf("Health"), includeNew = false))
    }

    @Test
    fun aFollowedMembersBillsReportNewOnesAndStepsOnly() {
        val recent = listOf(
            SponsoredStep("119/hr/10", "New bill", Action("2026-10-02", "Referred to the Committee on Oversight.")),
            SponsoredStep("119/hr/9", "Older bill", Action("2026-10-01", "Passed House by voice vote.")),
            SponsoredStep("119/hr/8", "Quiet bill", Action("2026-09-01", "Referred to the Committee on Rules.")),
        )
        // The first look only notes where things stand.
        assertEquals(emptyList(), memberBillSteps(recent, null))
        val seen = mapOf(
            "119/hr/9" to actionKey(Action("2026-09-20", "Referred to the Committee on Oversight."))!!,
            "119/hr/8" to actionKey(recent[2].action)!!,
        )
        assertEquals(listOf("119/hr/10", "119/hr/9"), memberBillSteps(recent, seen).map { it.bill })
    }
}

package com.theeclecticwitch.powertothepeople

import com.theeclecticwitch.powertothepeople.alerts.actionKey
import com.theeclecticwitch.powertothepeople.alerts.newVotes
import com.theeclecticwitch.powertothepeople.alerts.voteKey
import com.theeclecticwitch.powertothepeople.congress.Action
import com.theeclecticwitch.powertothepeople.congress.MemberVote
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
}

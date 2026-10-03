package com.theeclecticwitch.powertothepeople

import com.theeclecticwitch.powertothepeople.congress.BillNames
import com.theeclecticwitch.powertothepeople.congress.CongressData
import com.theeclecticwitch.powertothepeople.congress.MemberRecord
import com.theeclecticwitch.powertothepeople.congress.MemberVote
import com.theeclecticwitch.powertothepeople.congress.VoteDetail
import com.theeclecticwitch.powertothepeople.congress.align
import com.theeclecticwitch.powertothepeople.congress.voteLabel
import com.theeclecticwitch.powertothepeople.data.Http
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The published files' shapes, trimmed from real ones on GitHub Pages. */
class CongressDataTest {
    @Test
    fun readsAMembersRecordIncludingTheSpeakersElection() {
        val record = Http.json.decodeFromString<MemberRecord>(
            """{"congress":119,"id":"A000375","name":"Jodey Arrington","party":"R","state":"TX",
               "sponsored":["119/hr/1","119/hres/50"],
               "votes":[{"chamber":"house","roll":314,"session":2,"vote":"Yea"},
                        {"chamber":"house","roll":313,"session":2,"vote":"Nay"},
                        {"chamber":"house","roll":20,"session":1,"vote":"Not Voting"},
                        {"chamber":"house","roll":2,"session":1,"vote":"Johnson (LA)"}]}""",
        )
        assertEquals(2, record.sponsored.size)
        val t = CongressData.tally(record.votes)
        assertEquals(listOf(4, 1, 1, 0, 1, 1), listOf(t.total, t.yea, t.nay, t.present, t.notVoting, t.other))
        assertEquals("Voted for Johnson (LA)", voteLabel(record.votes.last().vote))
        assertEquals("Did not vote", voteLabel("Not Voting"))
    }

    @Test
    fun readsASenateRollCall() {
        val v = Http.json.decodeFromString<VoteDetail>(
            """{"bill":"119/s/9","chamber":"senate","congress":119,"date":"2025-03-03","document":"S. 9",
               "positions":[{"id":"A000382","name":"Angela Alsobrooks","party":"D","senateId":"S428","state":"MD","vote":"Nay"}],
               "question":"On Cloture on the Motion to Proceed S. 9","roll":100,"session":1,
               "result":"Cloture on the Motion to Proceed Rejected (51-45, 3/5 majority required)",
               "title":"Motion to Invoke Cloture: Motion to Proceed to S. 9",
               "totals":{"Nay":45,"Not Voting":4,"Yea":51},"type":"3/5","updated":"March 6, 2025, 03:13 PM"}""",
        )
        assertEquals(51, v.totals["Yea"])
        assertEquals("A000382", v.positions.single().id)
    }

    @Test
    fun billsAreNamedTheWayCongressNamesThem() {
        assertEquals("H.R. 1", BillNames.label("119/hr/1"))
        assertEquals("S.J.Res. 12", BillNames.label("119/sjres/12"))
        assertEquals("H.Con.Res. 14", BillNames.label("119/hconres/14"))
    }

    @Test
    fun alignmentCountsOnlyVotesTheReaderAnswered() {
        val votes = listOf(
            MemberVote("house", 2, 314, "Yea"),
            MemberVote("house", 2, 313, "Nay"),
            MemberVote("house", 2, 312, "Not Voting"),
            MemberVote("house", 2, 311, "Yea"),
        )
        val mine = mapOf(
            "119/house/2/314" to "Yea",
            "119/house/2/313" to "Yea",
            "119/house/2/312" to "Nay",
            "119/senate/2/10" to "Nay",
        )
        val a = align(votes, mine, 119)
        assertEquals(3, a.compared.size)
        assertEquals(listOf(1, 1, 1), listOf(a.same, a.different, a.memberDidNotTakeSide))
        assertEquals(0.5, a.sameShare)
        assertNull(align(votes, emptyMap(), 119).sameShare)
    }
}

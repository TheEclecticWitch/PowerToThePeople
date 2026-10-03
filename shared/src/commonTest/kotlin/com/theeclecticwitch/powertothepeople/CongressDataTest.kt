package com.theeclecticwitch.powertothepeople

import com.theeclecticwitch.powertothepeople.congress.BillNames
import com.theeclecticwitch.powertothepeople.congress.CongressData
import com.theeclecticwitch.powertothepeople.congress.MemberRecord
import com.theeclecticwitch.powertothepeople.congress.Upcoming
import com.theeclecticwitch.powertothepeople.congress.Action
import com.theeclecticwitch.powertothepeople.congress.BillFilter
import com.theeclecticwitch.powertothepeople.congress.BillSummary
import com.theeclecticwitch.powertothepeople.congress.ExecutiveOrder
import com.theeclecticwitch.powertothepeople.congress.ExecutiveOrders
import com.theeclecticwitch.powertothepeople.congress.MemberVote
import com.theeclecticwitch.powertothepeople.congress.billKeyFromQuery
import com.theeclecticwitch.powertothepeople.congress.searchBills
import com.theeclecticwitch.powertothepeople.congress.SessionDay
import com.theeclecticwitch.powertothepeople.congress.summarize
import com.theeclecticwitch.powertothepeople.congress.VoteDetail
import com.theeclecticwitch.powertothepeople.congress.align
import com.theeclecticwitch.powertothepeople.congress.constituentMessage
import com.theeclecticwitch.powertothepeople.officials.Level
import com.theeclecticwitch.powertothepeople.officials.Official
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

    @Test
    fun constituentMessageSaysWhereTheyLiveAndWhatTheyWant() {
        val senator = Official("X1", "Jane Doe", "Senior U.S. Senator for Ohio", Level.Federal)
        val rep = Official("X2", "John Roe", "U.S. Representative, Ohio District 3", Level.Federal)
        assertEquals(
            "Dear Senator Jane Doe,\n\nAs your constituent in Columbus, OH, I support H.R. 1, An act to provide for " +
                "reconciliation. I ask you to vote yes on it.\n\nThank you.",
            constituentMessage(senator, "119/hr/1", "An act to provide for reconciliation", "Columbus, OH", supports = true),
        )
        assertEquals(
            "Dear Representative John Roe,\n\nAs your constituent in Columbus, OH, I oppose S. 9. I ask you to vote no on it.\n\nThank you.",
            constituentMessage(rep, "119/s/9", null, "Columbus, OH", supports = false),
        )
    }

    @Test
    fun sessionDaysCountEachChamberSeparately() {
        val days = listOf(
            SessionDay("2025-12-18", house = true, senate = true),
            SessionDay("2026-09-29", house = true, senate = true),
            SessionDay("2026-09-30", house = false, senate = true),
            SessionDay("2026-10-01", house = true, senate = false),
        )
        val s = summarize(days, 2026)
        assertEquals(listOf(2, 2), listOf(s.house, s.senate))
        assertEquals("2026-10-01", s.houseLast)
        assertEquals("2026-09-30", s.senateLast)
    }

    @Test
    fun billNumbersAreFoundHoweverTheyAreTyped() {
        assertEquals("119/hr/1", billKeyFromQuery("HR 1"))
        assertEquals("119/hr/1", billKeyFromQuery("H.R.1"))
        assertEquals("119/sjres/12", billKeyFromQuery("s.j.res. 12"))
        assertEquals("119/s/9", billKeyFromQuery("S 9"))
        assertNull(billKeyFromQuery("farm bill"))
    }

    @Test
    fun billSearchNeedsEveryWordAndFiltersLaws() {
        val index = mapOf(
            "119/hr/1" to BillSummary(title = "One Big Beautiful Bill Act", latestAction = Action("2025-07-04", "Became Public Law No: 119-21.")),
            "119/hr/2" to BillSummary(title = "Child Tax Credit Act", latestAction = Action("2026-01-01", "Referred to committee.")),
            "119/s/3" to BillSummary(title = "Child Care Act", latestAction = Action("2026-02-01", "Read twice.")),
        )
        assertEquals(listOf("119/hr/2"), searchBills(index, "child tax").map { it.first })
        assertEquals(listOf("119/s/3", "119/hr/2"), searchBills(index, "child").map { it.first })
        assertEquals(listOf("119/hr/1"), searchBills(index, "", BillFilter.Law).map { it.first })
        assertEquals(listOf("119/s/3"), searchBills(index, "child", BillFilter.Senate).map { it.first })
        assertEquals(listOf("119/hr/1"), searchBills(index, "h.r. 1").map { it.first })
    }

    @Test
    fun executiveOrdersByNumberOrWords() {
        val all = listOf(
            ExecutiveOrder(number = "14434", title = "Inaugurating the Era of Super Intelligence", president = "Donald Trump"),
            ExecutiveOrder(number = "14036", title = "Promoting Competition in the American Economy", president = "Joseph R. Biden Jr."),
        )
        assertEquals("14434", ExecutiveOrders.search(all, "EO 14434", null).single().number)
        assertEquals("14036", ExecutiveOrders.search(all, "Executive Order 14036", null).single().number)
        assertEquals("14036", ExecutiveOrders.search(all, "competition", null).single().number)
        assertEquals(0, ExecutiveOrders.search(all, "competition", "Donald Trump").size)
    }

    @Test
    fun readsWhatsComingUp() {
        val u = Http.json.decodeFromString<Upcoming>(
            """{"house": [{"week": "2026-09-14", "items": [{"number": "S. 283", "bill": "119/s/283",
            "title": "To amend title 38", "how": "Items that may be considered under suspension of the rules"}],
            "url": "https://docs.house.gov/floor/Default.aspx?date=2026-09-14"}],
            "senate": {"next": "Monday, Oct 05, 2026", "plan": "Convene for a pro forma session at 4:00 p.m.", "previous": null},
            "hearings": [{"id": "house/118000", "chamber": "house", "date": "2026-10-06T14:00:00Z", "title": "Markup",
            "committees": ["Committee on Rules"], "bills": [], "updated": "2026-10-01T00:00:00Z"}],
            "comments": [{"id": "EPA-HQ-OAR-2026-0001-0001", "title": "A rule", "agency": "EPA", "closes": "2026-10-10"}],
            "checked": "2026-10-03T12:00:00Z"}""",
        )
        assertEquals("119/s/283", u.house.single().items.single().bill)
        assertEquals("Monday, Oct 05, 2026", u.senate?.next)
        assertEquals("Committee on Rules", u.hearings.single().committees.single())
        assertEquals("2026-10-10", u.comments.single().closes)
    }
}

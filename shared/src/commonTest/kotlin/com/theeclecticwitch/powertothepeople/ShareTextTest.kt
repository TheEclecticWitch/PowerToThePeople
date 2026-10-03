package com.theeclecticwitch.powertothepeople

import com.theeclecticwitch.powertothepeople.congress.Action
import com.theeclecticwitch.powertothepeople.congress.Bill
import com.theeclecticwitch.powertothepeople.congress.ShareText
import com.theeclecticwitch.powertothepeople.congress.VoteDetail
import kotlin.test.Test
import kotlin.test.assertEquals

/** Shared text is plain facts and an official link, nothing more. */
class ShareTextTest {
    @Test
    fun aBillGoesOutWithItsLatestActionAndCongressGovLink() {
        val b = Bill(
            "119/s/2403", title = "Retire through Ownership Act",
            latestAction = Action("2026-09-16", "Motion to reconsider laid on the table Agreed to without objection."),
            url = "https://www.congress.gov/bill/119th-congress/senate-bill/2403",
        )
        val (subject, text) = ShareText.bill(b)
        assertEquals("S. 2403: Retire through Ownership Act", subject)
        assertEquals(
            "S. 2403: Retire through Ownership Act\n" +
                "Latest action (September 16, 2026): Motion to reconsider laid on the table Agreed to without objection.\n" +
                "https://www.congress.gov/bill/119th-congress/senate-bill/2403\n" +
                "Shared from Power to the People",
            text,
        )
    }

    @Test
    fun aHouseVoteLinksTheClerksPageNotItsXml() {
        val v = VoteDetail(
            chamber = "house", congress = 119, session = 2, roll = 314, date = "2026-09-16",
            question = "On Motion to Suspend the Rules and Pass", result = "Passed", bill = "119/s/2403",
            totals = mapOf("Yea" to 401, "Nay" to 14, "Not Voting" to 18), source = "https://clerk.house.gov/evs/2026/roll314.xml",
        )
        val (subject, text) = ShareText.vote(v)
        assertEquals("House roll call 314, September 16, 2026", subject)
        assertEquals(
            "House roll call 314, September 16, 2026\n" +
                "On Motion to Suspend the Rules and Pass\n" +
                "On S. 2403\n" +
                "Passed · Yea 401, Nay 14, Not Voting 18\n" +
                "https://clerk.house.gov/Votes/2026314\n" +
                "Shared from Power to the People",
            text,
        )
    }
}

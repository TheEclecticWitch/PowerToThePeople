package com.theeclecticwitch.powertothepeople.congress

import com.theeclecticwitch.powertothepeople.ui.Format

/**
 * What goes out when a reader shares a bill or a vote: plain facts and a link to the official record, so
 * whoever receives it can check it at the source without the app. No opinion, no "I support".
 */
object ShareText {
    private const val FOOTER = "Shared from Power to the People"

    fun bill(b: Bill): Pair<String, String> {
        val label = BillNames.label(b.bill)
        val subject = listOfNotNull(label, b.title).joinToString(": ")
        val lines = listOfNotNull(
            subject,
            b.latestAction?.let { a -> "Latest action" + (a.date?.let { " (${Format.date(it)})" } ?: "") + ": " + a.text.orEmpty() },
            b.url,
            FOOTER,
        )
        return subject to lines.joinToString("\n")
    }

    /** The House Clerk's page for a roll call; its record file is XML, which no one wants in a message. */
    fun houseVotePage(year: String, roll: Int) = "https://clerk.house.gov/Votes/$year$roll"

    fun vote(v: VoteDetail): Pair<String, String> {
        val subject = "${chamberName(v.chamber)} roll call ${v.roll}, ${Format.date(v.date)}"
        val totals = voteTotals(v.totals)
        val link = if (v.chamber == "house") houseVotePage(v.date.take(4), v.roll) else v.source
        val lines = listOfNotNull(
            subject,
            listOfNotNull(v.question, v.title?.takeIf { it != v.question }).joinToString(": "),
            v.bill?.let { "On ${BillNames.label(it)}" },
            listOfNotNull(v.result, totals.takeIf { it.isNotEmpty() }).joinToString(" · "),
            link,
            FOOTER,
        )
        return subject to lines.joinToString("\n")
    }

    private fun voteTotals(totals: Map<String, Int>): String =
        listOf("Yea", "Nay", "Present", "Not Voting").mapNotNull { k -> totals[k]?.let { "$k $it" } }.joinToString(", ")
}

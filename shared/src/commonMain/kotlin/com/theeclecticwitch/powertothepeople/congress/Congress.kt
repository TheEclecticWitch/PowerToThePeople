package com.theeclecticwitch.powertothepeople.congress

import com.theeclecticwitch.powertothepeople.data.CachedSource
import com.theeclecticwitch.powertothepeople.data.Http
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

// --- The published files' shapes. Only the fields the app uses; the gatherer may add more. ---

@Serializable
data class Action(val date: String? = null, val text: String? = null)

/** One roll call as it appears in a chamber's list for a session. */
@Serializable
data class VoteSummary(
    val roll: Int,
    val date: String,
    val question: String? = null,
    val result: String? = null,
    val bill: String? = null,
    val title: String? = null,
    val totals: Map<String, Int> = emptyMap(),
)

@Serializable
private class VoteList(val chamber: String, val session: Int, val votes: List<VoteSummary>)

@Serializable
data class Position(
    val id: String? = null,
    val name: String,
    val party: String? = null,
    val state: String? = null,
    val vote: String,
)

/** A whole roll call: every member's vote, and where the record came from. */
@Serializable
data class VoteDetail(
    val chamber: String,
    val congress: Int,
    val session: Int,
    val roll: Int,
    val date: String,
    val question: String? = null,
    val result: String? = null,
    val type: String? = null,
    val bill: String? = null,
    val billUrl: String? = null,
    val document: String? = null,
    val title: String? = null,
    val totals: Map<String, Int> = emptyMap(),
    val positions: List<Position> = emptyList(),
    val source: String? = null,
)

@Serializable
data class MemberVote(val chamber: String, val session: Int, val roll: Int, val vote: String)

/** One member's whole record in the current Congress, newest vote first. */
@Serializable
data class MemberRecord(
    val id: String,
    val name: String,
    val congress: Int,
    val sponsored: List<String> = emptyList(),
    val votes: List<MemberVote> = emptyList(),
)

@Serializable
data class BillSummary(
    val title: String? = null,
    val introduced: String? = null,
    val origin: String? = null,
    val latestAction: Action? = null,
)

/** A member's campaign committee totals for their current race, as reported to the FEC. Whole dollars. */
@Serializable
data class CampaignMoney(
    val fecId: String? = null,
    val electionYear: Int? = null,
    val from: String? = null,
    val through: String? = null,
    val lastReport: String? = null,
    val receipts: Long? = null,
    val individuals: Long? = null,
    val individualsItemized: Long? = null,
    val individualsUnitemized: Long? = null,
    val pacs: Long? = null,
    val parties: Long? = null,
    val candidate: Long? = null,
    val candidateLoans: Long? = null,
    val transfers: Long? = null,
    val disbursements: Long? = null,
    val cashOnHand: Long? = null,
    val debts: Long? = null,
    val source: String? = null,
)

@Serializable
private class CampaignMoneyFile(val members: Map<String, CampaignMoney> = emptyMap())

/** A day the House, the Senate or both met, and that day's Congressional Record. */
@Serializable
data class SessionDay(val date: String, val house: Boolean = false, val senate: Boolean = false, val record: String? = null)

@Serializable
private class SessionDays(val days: List<SessionDay> = emptyList())

@Serializable
private class ContactForms(val forms: Map<String, String> = emptyMap())

@Serializable
private class BillList(val bills: Map<String, BillSummary>)

@Serializable
data class Sponsor(val id: String? = null, val name: String, val party: String? = null, val state: String? = null)

@Serializable
data class Law(val number: String, val type: String? = null)

@Serializable
data class Bill(
    val bill: String,
    val title: String? = null,
    val introduced: String? = null,
    val origin: String? = null,
    val policyArea: String? = null,
    val cosponsorCount: Int? = null,
    val sponsors: List<Sponsor> = emptyList(),
    val latestAction: Action? = null,
    val laws: List<Law> = emptyList(),
    val url: String? = null,
    /** The Congressional Research Service's latest plain-English summary, if it has written one. */
    val summary: CrsSummary? = null,
    /** Every stage CRS has summarized: "Introduced in House", "Passed Senate", "Public Law"… */
    val stages: List<String> = emptyList(),
)

@Serializable
data class CrsSummary(val text: String, val stage: String? = null, val date: String? = null)

/** A vote joined to its roll call, so a member's record reads as sentences rather than numbers. */
data class CastVote(val vote: MemberVote, val summary: VoteSummary?)

data class BillVote(val chamber: String, val session: Int, val summary: VoteSummary)

/** How one member's votes add up. */
data class VoteTally(val total: Int, val yea: Int, val nay: Int, val present: Int, val notVoting: Int, val other: Int)

/**
 * Votes, bills and members' records, gathered from Congress.gov, the House Clerk and the Senate every
 * six hours by this project's own gatherer (tools/gather) and published on GitHub Pages. Copied from
 * the sources as published; nothing is scored or ranked.
 */
object CongressData {
    const val BASE = "https://theeclecticwitch.github.io/PowerToThePeople"
    const val SOURCE_NAME = "Congress.gov, the House Clerk and the Senate"
    const val SOURCE_URL = "https://www.congress.gov/"
    const val CONGRESS = 119
    private val sessions = listOf(1, 2)

    private val lock = Mutex()
    private val sources = mutableMapOf<String, CachedSource>()
    private var voteLists: Map<String, List<VoteSummary>>? = null
    private var billList: Map<String, BillSummary>? = null

    /** A published file, kept on the device and refreshed after [maxAge]. */
    private fun source(path: String, maxAge: Duration): CachedSource =
        sources.getOrPut(path) {
            CachedSource("pd_" + path.replace('/', '_'), maxAge) { Http.getText("$BASE/$path") }
        }

    private suspend fun text(path: String, maxAge: Duration, force: Boolean = false): String =
        lock.withLock { source(path, maxAge) }.get(force).text

    /** Every roll call in both chambers this Congress, keyed "house/1/100". About 380 KB in all. */
    suspend fun voteLists(force: Boolean = false): Map<String, List<VoteSummary>> {
        voteLists?.takeIf { !force }?.let { return it }
        val lists = coroutineScope {
            listOf("house", "senate").flatMap { chamber -> sessions.map { chamber to it } }.map { (chamber, session) ->
                async {
                    val path = "votes/$chamber/$CONGRESS-$session.json"
                    val list = try {
                        Http.json.decodeFromString<VoteList>(text(path, 6.hours, force)).votes
                    } catch (e: ClientRequestException) {
                        // A session that hasn't started yet has no file.
                        if (e.response.status == HttpStatusCode.NotFound) emptyList() else throw e
                    }
                    "$chamber/$session" to list
                }
            }.awaitAll().toMap()
        }
        voteLists = lists
        return lists
    }

    suspend fun voteSummary(chamber: String, session: Int, roll: Int): VoteSummary? =
        voteLists()["$chamber/$session"]?.firstOrNull { it.roll == roll }

    /** Every roll call on one bill, newest first. */
    suspend fun votesOnBill(bill: String): List<BillVote> =
        voteLists().flatMap { (key, votes) ->
            val (chamber, session) = key.split('/')
            votes.filter { it.bill == bill }.map { BillVote(chamber, session.toInt(), it) }
        }.sortedWith(compareByDescending<BillVote> { it.summary.date }.thenByDescending { it.summary.roll })

    /** A roll call's details. Old votes don't change, so these are kept for a month. */
    suspend fun vote(chamber: String, session: Int, roll: Int): VoteDetail =
        Http.json.decodeFromString(text("votes/$chamber/$CONGRESS-$session/$roll.json", 30.days))

    /** A member's record, or null when the gatherer has none (a member sworn in since its last run). */
    suspend fun member(id: String, force: Boolean = false): MemberRecord? = try {
        Http.json.decodeFromString<MemberRecord>(text("members/$id.json", 6.hours, force))
    } catch (e: ClientRequestException) {
        if (e.response.status == HttpStatusCode.NotFound) null else throw e
    }

    /** One member's votes joined to the roll calls they were cast on. */
    suspend fun castVotes(record: MemberRecord): List<CastVote> {
        val lists = voteLists()
        val byKey = lists.mapValues { (_, votes) -> votes.associateBy { it.roll } }
        return record.votes.map { CastVote(it, byKey["${it.chamber}/${it.session}"]?.get(it.roll)) }
    }

    /**
     * The title, dates and latest action of every bill this Congress. 5.7 MB as JSON (under 800 KB as
     * it travels), so it is fetched only when a screen needs bill titles, and at most twice a day.
     */
    suspend fun billList(force: Boolean = false): Map<String, BillSummary> {
        billList?.takeIf { !force }?.let { return it }
        val bills = Http.json.decodeFromString<BillList>(text("bills/$CONGRESS/index.json", 12.hours, force)).bills
        billList = bills
        return bills
    }

    suspend fun bill(id: String): Bill = Http.json.decodeFromString(text("bills/$id.json", 1.days))

    /**
     * Each member's contact form, from the gatherer's weekly check that the page loads (contacts.json).
     * Members missing here have only their main website. Empty if the file can't be had.
     */
    suspend fun contactForms(): Map<String, String> = try {
        Http.json.decodeFromString<ContactForms>(text("contacts.json", 7.days)).forms
    } catch (e: Exception) {
        emptyMap()
    }

    /** A member's campaign money, or null when the FEC has nothing for them yet. */
    suspend fun campaignMoney(bioguide: String): CampaignMoney? = try {
        Http.json.decodeFromString<CampaignMoneyFile>(text("finance/$CONGRESS.json", 1.days)).members[bioguide]
            ?.takeIf { it.receipts != null }
    } catch (e: ClientRequestException) {
        if (e.response.status == HttpStatusCode.NotFound) null else throw e
    }

    /** Every day either chamber met this Congress, oldest first. Empty until the gatherer has published it. */
    suspend fun sessionDays(force: Boolean = false): List<SessionDay> = try {
        Http.json.decodeFromString<SessionDays>(text("sessions/$CONGRESS.json", 6.hours, force)).days
    } catch (e: ClientRequestException) {
        if (e.response.status == HttpStatusCode.NotFound) emptyList() else throw e
    }

    fun tally(votes: List<MemberVote>): VoteTally {
        var yea = 0; var nay = 0; var present = 0; var notVoting = 0; var other = 0
        votes.forEach {
            when (it.vote) {
                "Yea" -> yea++
                "Nay" -> nay++
                "Present" -> present++
                "Not Voting" -> notVoting++
                // The Speaker's election, where each member names a candidate.
                else -> other++
            }
        }
        return VoteTally(votes.size, yea, nay, present, notVoting, other)
    }
}

/** "119/hr/1" -> "H.R. 1", in the style the Congress itself uses. */
object BillNames {
    private val types = mapOf(
        "hr" to "H.R.", "s" to "S.", "hres" to "H.Res.", "sres" to "S.Res.",
        "hjres" to "H.J.Res.", "sjres" to "S.J.Res.", "hconres" to "H.Con.Res.", "sconres" to "S.Con.Res.",
    )

    fun label(id: String): String {
        val parts = id.split('/')
        if (parts.size < 3) return id
        return "${types[parts[1]] ?: parts[1].uppercase()} ${parts[2]}"
    }
}

/** How a single vote reads: "Yea", "Did not vote", or a candidate's name in the Speaker's election. */
fun voteLabel(vote: String): String = when (vote) {
    "Not Voting" -> "Did not vote"
    "Yea", "Nay", "Present" -> vote
    else -> "Voted for $vote"
}

fun chamberName(chamber: String): String = if (chamber == "senate") "Senate" else "House"

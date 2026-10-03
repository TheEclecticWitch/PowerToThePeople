package com.theeclecticwitch.powertothepeople.alerts

import com.theeclecticwitch.powertothepeople.congress.Action
import com.theeclecticwitch.powertothepeople.congress.BillNames
import com.theeclecticwitch.powertothepeople.congress.ComingUp
import com.theeclecticwitch.powertothepeople.congress.CongressData
import com.theeclecticwitch.powertothepeople.congress.MemberVote
import com.theeclecticwitch.powertothepeople.congress.SponsoredStep
import com.theeclecticwitch.powertothepeople.congress.TopicMove
import com.theeclecticwitch.powertothepeople.congress.chamberName
import com.theeclecticwitch.powertothepeople.congress.voteLabel
import com.theeclecticwitch.powertothepeople.location.LocationStore
import com.theeclecticwitch.powertothepeople.officials.FederalOfficials
import com.theeclecticwitch.powertothepeople.officials.JsonFileState
import com.theeclecticwitch.powertothepeople.ui.Format
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** What the reader asked to hear about. Kept on this device; nothing about it is sent anywhere. */
@Serializable
data class AlertPrefs(
    /** Background checks and notifications. The in-app list fills whenever the app is opened either way. */
    val notify: Boolean = false,
    /** Every roll call the reader's two senators and representative vote on. */
    val myMembers: Boolean = true,
    /** Bills the reader follows, as "119/hr/1". */
    val bills: List<String> = emptyList(),
    /** Subjects the reader follows, by their CRS policy area name ("Health"). */
    val topics: List<String> = emptyList(),
    /** Also tell about bills in those subjects that were only introduced or sent to committee. Most go no further. */
    val topicsIncludeNew: Boolean = false,
    /** Members of Congress the reader follows: their votes, and the bills they sponsor. */
    val members: List<FollowedMember> = emptyList(),
)

/** A member of Congress the reader follows, by bioguide id, with the name to show. */
@Serializable
data class FollowedMember(val id: String, val name: String)

/** One thing that happened. [bill] or [vote] ("senate/2/256") says where tapping it leads. */
@Serializable
data class AlertItem(
    val at: String,
    val title: String,
    val text: String,
    val bill: String? = null,
    val vote: String? = null,
)

/**
 * What the reader has already been told about, so each check reports only what is new. The first time
 * something is checked it is only noted, never announced: following a bill shouldn't bring its whole past.
 */
@Serializable
data class Seen(
    /** Member id -> their newest vote seen, "chamber/session/roll". */
    val memberVotes: Map<String, String> = emptyMap(),
    /** Bill -> its latest action seen, "date|text". */
    val billActions: Map<String, String> = emptyMap(),
    /** Roll calls already reported on followed bills, "chamber/session/roll". */
    val billVotes: Set<String> = emptySet(),
    /** Schedules already reported: "floor|bill|week" and "hearing|bill|id". */
    val scheduled: Set<String> = emptySet(),
    /** Bills whose roll calls and schedules have had their first, silent look. */
    val billsBaselined: Set<String> = emptySet(),
    /** Bill -> its latest action seen in topics.json, "date|text". Only bills still in that file are kept. */
    val topicMoves: Map<String, String> = emptyMap(),
    /** Subjects that have had their first, silent look. */
    val topicsBaselined: Set<String> = emptySet(),
    /** The day of the last topic check, "2026-10-03". */
    val topicsChecked: String? = null,
    /** A followed member's sponsored bill -> its latest action seen, "date|text". */
    val memberBills: Map<String, String> = emptyMap(),
    /** Followed members whose sponsored bills have had their first, silent look. */
    val membersBaselined: Set<String> = emptySet(),
)

fun voteKey(v: MemberVote) = "${v.chamber}/${v.session}/${v.roll}"

/** A member's votes newer than [seen], newest first; none if [seen] is gone from the record (it was rebuilt). */
fun newVotes(votesNewestFirst: List<MemberVote>, seen: String): List<MemberVote> {
    val i = votesNewestFirst.indexOfFirst { voteKey(it) == seen }
    return if (i < 0) emptyList() else votesNewestFirst.take(i)
}

fun actionKey(a: Action?): String? = a?.let { "${it.date}|${it.text}" }

/**
 * A followed member's sponsored bills that are new or took a step since the last look. [seen] is null on the
 * first look at this member, which only notes where things stand.
 */
fun memberBillSteps(recent: List<SponsoredStep>, seen: Map<String, String>?): List<SponsoredStep> =
    if (seen == null) emptyList() else recent.filter { s -> actionKey(s.action)?.let { it != seen[s.bill] } == true }

/**
 * The bills in followed [topics] that took a new step since the last look, and what to remember next time.
 * Skipped: bills the reader follows one by one (they have their own alerts), steps that only introduce a bill
 * or send it to committee unless [includeNew], subjects having their first look, and steps dated well before
 * the last check ([notBefore]): a bill can reach topics.json late, when CRS assigns its subject.
 */
fun topicAlerts(
    moves: List<TopicMove>,
    topics: List<String>,
    followedBills: List<String>,
    includeNew: Boolean,
    seen: Seen,
    notBefore: String?,
): Pair<List<TopicMove>, Map<String, String>> {
    val found = mutableListOf<TopicMove>()
    val keys = mutableMapOf<String, String>()
    for (m in moves) {
        val key = actionKey(m.action) ?: continue
        keys[m.bill] = key
        if (m.policyArea !in topics || m.bill in followedBills) continue
        if (m.policyArea !in seen.topicsBaselined || seen.topicMoves[m.bill] == key) continue
        if (m.early && !includeNew) continue
        if (notBefore != null && (m.action?.date ?: "") < notBefore) continue
        found += m
    }
    return found to keys
}

/**
 * What to show under "Lately in your topics": each followed subject's latest step, so a subject just picked
 * shows something at once, then the newest steps across them all, up to [limit] in all (more if there are more
 * subjects). Steps that only introduce a bill or send it to committee count only with [includeNew], or for a
 * subject that has had no other step lately.
 */
fun latelyInTopics(moves: List<TopicMove>, topics: List<String>, includeNew: Boolean, limit: Int = 5): List<TopicMove> {
    val newest = compareByDescending<TopicMove> { it.action?.date ?: "" }.thenByDescending { it.bill }
    val mine = moves.filter { it.policyArea in topics }.sortedWith(newest)
    val shown = mine.filter { includeNew || !it.early }
    val latestEach = topics.mapNotNull { t -> shown.firstOrNull { it.policyArea == t } ?: mine.firstOrNull { it.policyArea == t } }
    val rest = shown.filter { it !in latestEach }
    return (latestEach + rest.take((limit - latestEach.size).coerceAtLeast(0))).sortedWith(newest)
}

object Alerts {
    private const val KEEP = 100
    private const val MAX_NOTIFICATIONS = 4

    private val prefsState = JsonFileState("alert_prefs.json", AlertPrefs.serializer(), AlertPrefs())
    private val seenState = JsonFileState("alert_seen.json", Seen.serializer(), Seen())
    private val historyState = JsonFileState("alert_history.json", ListSerializer(AlertItem.serializer()), emptyList())
    private val lock = Mutex()

    val prefs: StateFlow<AlertPrefs> = prefsState.flow
    val history: StateFlow<List<AlertItem>> = historyState.flow

    fun setNotify(on: Boolean) {
        prefsState.update { it.copy(notify = on) }
        Notifications.schedule(on)
    }

    fun setMyMembers(on: Boolean) = prefsState.update { it.copy(myMembers = on) }

    fun follow(bill: String) = prefsState.update { if (bill in it.bills) it else it.copy(bills = it.bills + bill) }

    fun unfollow(bill: String) = prefsState.update { it.copy(bills = it.bills - bill) }

    fun followTopic(topic: String) {
        // A fresh, silent first look, so following again doesn't bring what happened in between.
        seenState.update { it.copy(topicsBaselined = it.topicsBaselined - topic) }
        prefsState.update { if (topic in it.topics) it else it.copy(topics = it.topics + topic) }
    }

    fun unfollowTopic(topic: String) = prefsState.update { it.copy(topics = it.topics - topic) }

    fun setTopicsIncludeNew(on: Boolean) = prefsState.update { it.copy(topicsIncludeNew = on) }

    fun followMember(id: String, name: String) {
        // A fresh, silent first look, so following again doesn't bring what happened in between.
        seenState.update { it.copy(membersBaselined = it.membersBaselined - id) }
        prefsState.update { p -> if (p.members.any { it.id == id }) p else p.copy(members = p.members + FollowedMember(id, name)) }
    }

    fun unfollowMember(id: String) = prefsState.update { p -> p.copy(members = p.members.filterNot { it.id == id }) }

    fun clearHistory() = historyState.update { emptyList() }

    /**
     * Looks for anything new and adds it to the list. With [notify] (the background check) each new
     * thing also becomes a notification, a few at most. Returns what was new. Never throws: a source
     * that can't be reached is simply tried again next time.
     */
    suspend fun check(notify: Boolean): List<AlertItem> = lock.withLock {
        val p = prefs.value
        var seen = seenState.flow.value
        val found = mutableListOf<AlertItem>()
        val now = Clock.System.now().toString()

        // The reader's own members (when that's on) and the members they follow, each once.
        val watched = buildMap<String, String> {
            if (p.myMembers) runCatching {
                val location = LocationStore.location.value ?: return@runCatching
                val d = FederalOfficials.forLocation(location)
                (d.senators + listOfNotNull(d.representative)).forEach { put(it.id, it.name) }
            }
            p.members.forEach { if (it.id !in this) put(it.id, it.name) }
        }
        // Only followed members' recent bills are kept, so the list doesn't grow without end.
        val memberBills = mutableMapOf<String, String>()
        if (watched.isNotEmpty()) {
            runCatching {
                for ((id, name) in watched) {
                    val record = CongressData.member(id, force = true) ?: continue
                    if (p.members.any { it.id == id }) {
                        // Their sponsored bills: new ones, and steps by older ones. The first look is silent.
                        val firstLook = id !in seen.membersBaselined
                        for (s in memberBillSteps(record.recentSponsored, if (firstLook) null else seen.memberBills)) {
                            found += AlertItem(
                                at = now,
                                title = "$name: ${BillNames.label(s.bill)}",
                                text = listOfNotNull(
                                    s.title,
                                    listOfNotNull(s.action?.date?.let { Format.date(it) }, s.action?.text).joinToString(": ").ifEmpty { null },
                                ).joinToString(" · "),
                                bill = s.bill,
                            )
                        }
                        memberBills += record.recentSponsored.mapNotNull { s -> actionKey(s.action)?.let { s.bill to it } }
                        seen = seen.copy(membersBaselined = seen.membersBaselined + id)
                    }
                    val newest = record.votes.firstOrNull() ?: continue
                    val last = seen.memberVotes[id]
                    if (last != null) {
                        for (v in newVotes(record.votes, last).take(20).reversed()) {
                            val s = CongressData.voteSummary(v.chamber, v.session, v.roll)
                            // House roll calls carry no title of their own; name the bill instead.
                            val about = s?.title ?: s?.bill?.let { b ->
                                val title = runCatching { CongressData.bill(b).title }.getOrNull()
                                listOfNotNull(BillNames.label(b), title).joinToString(": ")
                            }
                            found += AlertItem(
                                at = now,
                                title = "$name voted ${voteLabel(v.vote)}",
                                text = listOfNotNull(s?.question, about?.takeIf { it != s?.question }, s?.result)
                                    .joinToString(" · ").ifEmpty { "${chamberName(v.chamber)} roll call ${v.roll}" },
                                vote = voteKey(v),
                            )
                        }
                    }
                    seen = seen.copy(memberVotes = seen.memberVotes + (id to voteKey(newest)))
                }
                // Replaced only after every member was read, so a failed download doesn't forget what was seen.
                seen = seen.copy(memberBills = memberBills)
            }
        }

        if (p.bills.isNotEmpty()) {
            // Fresh copies of the shared files once, rather than once per bill.
            runCatching { CongressData.voteLists(force = true) }
            val upcoming = runCatching { ComingUp.load(force = true) }.getOrNull()
            for (id in p.bills) {
                val label = BillNames.label(id)
                val quiet = id !in seen.billsBaselined
                runCatching {
                    val bill = CongressData.bill(id, force = true)
                    val key = actionKey(bill.latestAction)
                    val last = seen.billActions[id]
                    if (key != null && last != null && key != last) {
                        found += AlertItem(
                            at = now,
                            title = "$label moved",
                            text = listOfNotNull(bill.latestAction?.date?.let { Format.date(it) }, bill.latestAction?.text).joinToString(": "),
                            bill = id,
                        )
                    }
                    if (key != null) seen = seen.copy(billActions = seen.billActions + (id to key))
                }
                runCatching {
                    for (v in CongressData.votesOnBill(id).reversed()) {
                        val k = "${v.chamber}/${v.session}/${v.summary.roll}"
                        if (k in seen.billVotes) continue
                        if (!quiet) {
                            found += AlertItem(
                                at = now,
                                title = "${chamberName(v.chamber)} vote on $label",
                                text = listOfNotNull(v.summary.question, v.summary.result).joinToString(": "),
                                vote = k,
                            )
                        }
                        seen = seen.copy(billVotes = seen.billVotes + k)
                    }
                }
                upcoming?.let { u ->
                    u.house.forEach { week ->
                        if (week.items.none { it.bill == id }) return@forEach
                        val k = "floor|$id|${week.week}"
                        if (k !in seen.scheduled) {
                            if (!quiet) found += AlertItem(now, "$label is on the House schedule", "For the week of ${Format.date(week.week)}. Now is the time to tell your representative what you think.", bill = id)
                            seen = seen.copy(scheduled = seen.scheduled + k)
                        }
                    }
                    u.hearings.filter { id in it.bills }.forEach { h ->
                        val k = "hearing|$id|${h.id}"
                        if (k !in seen.scheduled) {
                            if (!quiet) {
                                found += AlertItem(
                                    now,
                                    "A committee meeting covers $label",
                                    listOfNotNull(h.date?.let { Format.date(it) }, h.committees.firstOrNull(), h.title).joinToString(" · "),
                                    bill = id,
                                )
                            }
                            seen = seen.copy(scheduled = seen.scheduled + k)
                        }
                    }
                }
                seen = seen.copy(billsBaselined = seen.billsBaselined + id)
            }
        }

        if (p.topics.isNotEmpty()) {
            runCatching {
                val t = CongressData.topics(force = true) ?: return@runCatching
                val today = now.take(10)
                // Two days' grace before the last check, for steps Congress.gov posts late.
                val notBefore = seen.topicsChecked?.let { runCatching { LocalDate.parse(it).minus(2, DateTimeUnit.DAY).toString() }.getOrNull() }
                val (moved, keys) = topicAlerts(t.moves, p.topics, p.bills, p.topicsIncludeNew, seen, notBefore)
                for (m in moved) {
                    found += AlertItem(
                        at = now,
                        title = "${m.policyArea}: ${BillNames.label(m.bill)}",
                        text = listOfNotNull(
                            m.title,
                            listOfNotNull(m.action?.date?.let { Format.date(it) }, m.action?.text).joinToString(": ").ifEmpty { null },
                        ).joinToString(" · "),
                        bill = m.bill,
                    )
                }
                seen = seen.copy(topicMoves = keys, topicsBaselined = seen.topicsBaselined + p.topics, topicsChecked = today)
            }
        }

        seenState.update { seen }
        if (found.isNotEmpty()) {
            historyState.update { (found.reversed() + it).take(KEEP) }
            if (notify) {
                found.take(MAX_NOTIFICATIONS).forEach { Notifications.post(it.title, it.text) }
                if (found.size > MAX_NOTIFICATIONS) {
                    Notifications.post("${found.size - MAX_NOTIFICATIONS} more updates", "Open Power to the People to see them all under Alerts.")
                }
            }
        }
        found
    }
}

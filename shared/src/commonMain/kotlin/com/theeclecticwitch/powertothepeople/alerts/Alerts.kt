package com.theeclecticwitch.powertothepeople.alerts

import com.theeclecticwitch.powertothepeople.congress.Action
import com.theeclecticwitch.powertothepeople.congress.BillNames
import com.theeclecticwitch.powertothepeople.congress.ComingUp
import com.theeclecticwitch.powertothepeople.congress.CongressData
import com.theeclecticwitch.powertothepeople.congress.MemberVote
import com.theeclecticwitch.powertothepeople.congress.chamberName
import com.theeclecticwitch.powertothepeople.congress.voteLabel
import com.theeclecticwitch.powertothepeople.location.LocationStore
import com.theeclecticwitch.powertothepeople.officials.FederalOfficials
import com.theeclecticwitch.powertothepeople.officials.JsonFileState
import com.theeclecticwitch.powertothepeople.ui.Format
import kotlin.time.Clock
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
)

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
)

fun voteKey(v: MemberVote) = "${v.chamber}/${v.session}/${v.roll}"

/** A member's votes newer than [seen], newest first; none if [seen] is gone from the record (it was rebuilt). */
fun newVotes(votesNewestFirst: List<MemberVote>, seen: String): List<MemberVote> {
    val i = votesNewestFirst.indexOfFirst { voteKey(it) == seen }
    return if (i < 0) emptyList() else votesNewestFirst.take(i)
}

fun actionKey(a: Action?): String? = a?.let { "${it.date}|${it.text}" }

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

        if (p.myMembers) {
            runCatching {
                val location = LocationStore.location.value ?: return@runCatching
                val d = FederalOfficials.forLocation(location)
                for (m in d.senators + listOfNotNull(d.representative)) {
                    val record = CongressData.member(m.id, force = true) ?: continue
                    val newest = record.votes.firstOrNull() ?: continue
                    val last = seen.memberVotes[m.id]
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
                                title = "${m.name} voted ${voteLabel(v.vote)}",
                                text = listOfNotNull(s?.question, about?.takeIf { it != s?.question }, s?.result)
                                    .joinToString(" · ").ifEmpty { "${chamberName(v.chamber)} roll call ${v.roll}" },
                                vote = voteKey(v),
                            )
                        }
                    }
                    seen = seen.copy(memberVotes = seen.memberVotes + (m.id to voteKey(newest)))
                }
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

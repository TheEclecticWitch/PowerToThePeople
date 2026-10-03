package com.theeclecticwitch.powertothepeople.officials

import com.theeclecticwitch.powertothepeople.data.CachedSource
import com.theeclecticwitch.powertothepeople.data.Http
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A seat on a committee or subcommittee, with any title that comes with it ("Chair", "Ranking Member"). */
data class CommitteeSeat(
    val committee: String,
    val subcommittee: String?,
    val title: String?,
    val url: String?,
)

@Serializable
private class RawCommittee(
    val name: String,
    val url: String? = null,
    @SerialName("thomas_id") val id: String,
    val subcommittees: List<RawSubcommittee> = emptyList(),
)

@Serializable
private class RawSubcommittee(val name: String, @SerialName("thomas_id") val id: String)

@Serializable
private class RawSeat(val bioguide: String? = null, val title: String? = null, val rank: Int? = null)

/**
 * Committee assignments, from the same @unitedstates project as the members themselves. A subcommittee's
 * id is its committee's id with its own number added: HSAG is House Agriculture, HSAG15 its forestry panel.
 */
object Committees {
    private val committeesSource = CachedSource("cache_committees.json", 7.days) {
        Http.getText("https://unitedstates.github.io/congress-legislators/committees-current.json")
    }
    private val membershipSource = CachedSource("cache_committee_membership.json", 7.days) {
        Http.getText("https://unitedstates.github.io/congress-legislators/committee-membership-current.json")
    }

    private val lock = Mutex()
    private var byMember: Map<String, List<CommitteeSeat>>? = null

    suspend fun forMember(bioguide: String): List<CommitteeSeat> = lock.withLock {
        val map = byMember ?: load().also { byMember = it }
        map[bioguide].orEmpty()
    }

    private suspend fun load(): Map<String, List<CommitteeSeat>> {
        val committees = Http.json.decodeFromString<List<RawCommittee>>(committeesSource.get().text)
        val membership = Http.json.decodeFromString<Map<String, List<RawSeat>>>(membershipSource.get().text)
        val names = mutableMapOf<String, Triple<String, String?, String?>>()
        committees.forEach { c ->
            names[c.id] = Triple(c.name, null, c.url)
            c.subcommittees.forEach { sub -> names[c.id + sub.id] = Triple(c.name, sub.name, c.url) }
        }
        val seats = mutableMapOf<String, MutableList<CommitteeSeat>>()
        membership.forEach { (id, members) ->
            val (committee, subcommittee, url) = names[id] ?: return@forEach
            members.forEach { m ->
                val who = m.bioguide ?: return@forEach
                seats.getOrPut(who) { mutableListOf() }.add(CommitteeSeat(committee, subcommittee, m.title, url))
            }
        }
        // Full committees first, then their subcommittees beneath them.
        return seats.mapValues { (_, list) -> list.sortedWith(compareBy({ it.committee }, { it.subcommittee ?: "" })) }
    }
}

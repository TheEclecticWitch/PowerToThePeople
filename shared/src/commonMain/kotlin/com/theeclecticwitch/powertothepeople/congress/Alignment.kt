package com.theeclecticwitch.powertothepeople.congress

import com.theeclecticwitch.powertothepeople.officials.JsonFileState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * How the reader says they would have voted on roll calls they looked at. Kept on this device only and
 * never sent anywhere: it exists so a member's page can show how often that member voted the same way.
 */
object MyPositions {
    private val positions = JsonFileState(
        "my_vote_positions.json",
        MapSerializer(String.serializer(), String.serializer()),
        emptyMap(),
    )

    /** "Yea" or "Nay", keyed by [key]. */
    val flow: StateFlow<Map<String, String>> = positions.flow

    fun key(chamber: String, session: Int, roll: Int, congress: Int = CongressData.CONGRESS) = "$congress/$chamber/$session/$roll"

    /** The reader's view of a bill itself: "Yea" here means they support it, "Nay" that they oppose it. */
    fun billKey(bill: String) = "bill/$bill"

    fun set(key: String, vote: String?) = positions.update { if (vote == null) it - key else it + (key to vote) }
}

/** One roll call the reader answered that this member also faced. */
data class Compared(val vote: MemberVote, val mine: String) {
    val same: Boolean get() = vote.vote == mine
    val memberTookSide: Boolean get() = vote.vote == "Yea" || vote.vote == "Nay"
}

/** The reader's answers set beside one member's votes. Counts only; what they mean is the reader's call. */
data class Alignment(val compared: List<Compared>) {
    val same: Int get() = compared.count { it.same }
    val different: Int get() = compared.count { it.memberTookSide && !it.same }
    val memberDidNotTakeSide: Int get() = compared.count { !it.memberTookSide }
    /** Of the votes where both took a side, the share they voted the same way; null with none. */
    val sameShare: Double? get() = (same + different).takeIf { it > 0 }?.let { same.toDouble() / it }
}

fun align(memberVotes: List<MemberVote>, mine: Map<String, String>, congress: Int = CongressData.CONGRESS): Alignment =
    Alignment(memberVotes.mapNotNull { v -> mine[MyPositions.key(v.chamber, v.session, v.roll, congress)]?.let { Compared(v, it) } })

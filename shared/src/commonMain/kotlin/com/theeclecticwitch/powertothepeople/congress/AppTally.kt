package com.theeclecticwitch.powertothepeople.congress

import com.theeclecticwitch.powertothepeople.data.Http
import com.theeclecticwitch.powertothepeople.officials.JsonFileState
import com.theeclecticwitch.powertothepeople.officials.newId
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** How app users answered one roll call. [yea] and [nay] are null until enough people have answered. */
@Serializable
data class Tally(val vote: String, val total: Int = 0, val yea: Int? = null, val nay: Int? = null, val minShown: Int = 10)

@Serializable
private class Tallies(val tallies: List<Tally> = emptyList())

/**
 * The reader's choice to add their answers to the app-wide count, a random code for this install (so one
 * phone counts once per vote and can change its answer), and which answers the server has already been sent.
 */
@Serializable
data class TallyPrefs(val share: Boolean = false, val install: String = "", val sent: Map<String, String> = emptyMap())

/**
 * The anonymous app-wide count of "How would you have voted?" answers, kept by this project's own small
 * service on Cloudflare (server/tally). Nothing is sent unless the reader turns sharing on; what is sent
 * is the roll call, the answer and the install's random code - never a name, address or location.
 */
object AppTally {
    const val BASE = "https://ptp-tally.powertothepeople.workers.dev"

    private val prefs = JsonFileState("my_tally_prefs.json", TallyPrefs.serializer(), TallyPrefs())
    val prefsFlow: StateFlow<TallyPrefs> = prefs.flow
    private val syncLock = Mutex()

    suspend fun get(key: String): Tally = Http.json.decodeFromString(Http.getText("$BASE/tally?vote=$key"))

    /** Up to 50 roll calls in one request, for the bill page. */
    suspend fun getMany(keys: List<String>): Map<String, Tally> {
        if (keys.isEmpty()) return emptyMap()
        val text = Http.getText("$BASE/tally?votes=${keys.take(50).joinToString(",")}")
        return Http.json.decodeFromString<Tallies>(text).tallies.associateBy { it.vote }
    }

    fun setSharing(on: Boolean) = prefs.update { it.copy(share = on) }

    /**
     * Brings the server in line with the reader's answers: sends new and changed ones, withdraws cleared
     * ones, and withdraws everything once sharing is turned off. Safe to call often; a failure just
     * leaves the rest for next time.
     */
    suspend fun sync(): Unit = syncLock.withLock {
        val p = prefs.flow.value
        val wanted = if (p.share) MyPositions.flow.value else emptyMap()
        val changes = (wanted.keys + p.sent.keys).filter { wanted[it] != p.sent[it] }
        if (changes.isEmpty()) return@withLock
        val install = p.install.ifBlank { newId().also { id -> prefs.update { it.copy(install = id) } } }
        for (key in changes) {
            try {
                send(key, install, wanted[key])
                prefs.update { cur -> cur.copy(sent = wanted[key]?.let { cur.sent + (key to it) } ?: (cur.sent - key)) }
            } catch (e: Exception) {
                return@withLock
            }
        }
    }

    private suspend fun send(key: String, install: String, answer: String?) {
        val body = buildJsonObject {
            put("vote", JsonPrimitive(key))
            put("install", JsonPrimitive(install))
            put("answer", answer?.let { JsonPrimitive(it) } ?: JsonNull)
        }
        Http.client.post("$BASE/answer") {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }.bodyAsText()
    }
}

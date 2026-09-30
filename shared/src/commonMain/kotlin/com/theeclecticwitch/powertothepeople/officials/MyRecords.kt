package com.theeclecticwitch.powertothepeople.officials

import com.theeclecticwitch.powertothepeople.data.AppFiles
import com.theeclecticwitch.powertothepeople.data.Http
import com.theeclecticwitch.powertothepeople.location.today
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/*
 * What the reader records for themselves: the local and state officials no free public source
 * covers, their own notes on anyone, and the campaign promises they are keeping track of.
 *
 * All of it lives on this device only. It is the reader's own record, in their own words.
 */

enum class PromiseStatus(val label: String) {
    Waiting("Not yet"),
    InProgress("In progress"),
    Kept("Kept"),
    NotKept("Not kept"),
}

@Serializable
data class Promise(
    val id: String,
    val text: String,
    val status: PromiseStatus = PromiseStatus.Waiting,
    /** Where the promise was made: a link, or words like "Town hall, March 2026". */
    val source: String = "",
    val added: String,
)

@Serializable
data class OfficialRecord(
    val notes: String = "",
    val promises: List<Promise> = emptyList(),
)

@OptIn(ExperimentalUuidApi::class)
fun newId(): String = Uuid.random().toString()

/** A small JSON file that is read once, kept in memory, and written back on every change. */
private class JsonFileState<T>(private val file: String, private val serializer: KSerializer<T>, empty: T) {
    private val state = MutableStateFlow(
        try {
            AppFiles.store.read(file)?.let { Http.json.decodeFromString(serializer, it) } ?: empty
        } catch (e: Exception) {
            empty
        },
    )
    val flow: StateFlow<T> = state.asStateFlow()

    fun update(transform: (T) -> T) {
        val next = transform(state.value)
        AppFiles.store.write(file, Http.json.encodeToString(serializer, next))
        state.value = next
    }
}

object MyRecords {
    private val records = JsonFileState(
        "my_official_records.json",
        MapSerializer(String.serializer(), OfficialRecord.serializer()),
        emptyMap(),
    )
    private val officials = JsonFileState("my_officials.json", ListSerializer(Official.serializer()), emptyList())

    /** Notes and promises, keyed by official id. */
    val recordsFlow: StateFlow<Map<String, OfficialRecord>> = records.flow

    /** Officials the reader added themselves. */
    val officialsFlow: StateFlow<List<Official>> = officials.flow

    private fun change(officialId: String, transform: (OfficialRecord) -> OfficialRecord) =
        records.update { all -> all + (officialId to transform(all[officialId] ?: OfficialRecord())) }

    fun setNotes(officialId: String, notes: String) = change(officialId) { it.copy(notes = notes) }

    fun addPromise(officialId: String, text: String, source: String) = change(officialId) {
        it.copy(promises = it.promises + Promise(newId(), text.trim(), source = source.trim(), added = today().toString()))
    }

    fun setPromiseStatus(officialId: String, promiseId: String, status: PromiseStatus) = change(officialId) { r ->
        r.copy(promises = r.promises.map { if (it.id == promiseId) it.copy(status = status) else it })
    }

    fun deletePromise(officialId: String, promiseId: String) = change(officialId) { r ->
        r.copy(promises = r.promises.filterNot { it.id == promiseId })
    }

    fun saveOfficial(official: Official) = officials.update { list ->
        if (list.any { it.id == official.id }) list.map { if (it.id == official.id) official else it } else list + official
    }

    fun deleteOfficial(id: String) {
        officials.update { list -> list.filterNot { it.id == id } }
        records.update { it - id }
    }
}

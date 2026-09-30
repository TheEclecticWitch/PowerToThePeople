package com.theeclecticwitch.powertothepeople.data

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import okio.FileSystem
import okio.Path

/**
 * Small text files in the app's own folder. Writes go to a neighbour first and are then moved into
 * place, so a write that fails half way can never leave a truncated file behind.
 */
class FileStore(
    private val directory: Path = appDataDirectory(),
    private val fs: FileSystem = appFileSystem,
) {
    fun read(name: String): String? {
        val path = directory / name
        return if (fs.exists(path)) fs.read(path) { readUtf8() } else null
    }

    fun write(name: String, text: String) {
        fs.createDirectories(directory)
        val target = directory / name
        val temp = directory / "$name.tmp"
        fs.write(temp) { writeUtf8(text) }
        fs.atomicMove(temp, target)
    }

    fun delete(name: String) {
        fs.delete(directory / name, mustExist = false)
    }

    fun modifiedAt(name: String): Instant? =
        fs.metadataOrNull(directory / name)?.lastModifiedAtMillis?.let { Instant.fromEpochMilliseconds(it) }
}

/** What came back from a cached fetch: the text, when it was fetched, and whether it is stale. */
data class Fetched(val text: String, val fetchedAt: Instant, val isStale: Boolean)

/**
 * Public data, fetched at most once per [maxAge] and kept on the device between times.
 *
 * If the network fails, the last copy is used and marked stale, so the app still works offline
 * and simply says how old its information is. Only when there is no copy at all does it fail.
 */
class CachedSource(
    private val cacheName: String,
    private val maxAge: Duration,
    private val store: FileStore = AppFiles.store,
    private val fetch: suspend () -> String,
) {
    suspend fun get(forceRefresh: Boolean = false): Fetched {
        val cached = store.read(cacheName)
        val cachedAt = store.modifiedAt(cacheName)
        val now = Clock.System.now()
        if (!forceRefresh && cached != null && cachedAt != null && now - cachedAt < maxAge) {
            return Fetched(cached, cachedAt, isStale = false)
        }
        return try {
            val fresh = fetch()
            store.write(cacheName, fresh)
            Fetched(fresh, now, isStale = false)
        } catch (e: Exception) {
            if (cached != null && cachedAt != null) Fetched(cached, cachedAt, isStale = true) else throw e
        }
    }
}

object AppFiles {
    val store by lazy { FileStore() }
}

package com.theeclecticwitch.powertothepeople.data

import okio.FileSystem
import okio.Path

/**
 * Where this app keeps its own files - the reader's location, their notes on officials, and cached
 * copies of public data. Everything stays on the device; nothing here is ever uploaded.
 */
expect fun appDataDirectory(): Path

/** okio's real disc. Declared per platform because okio's common code has none. */
expect val appFileSystem: FileSystem

/** The system and its version, such as "Android 16", noted at the foot of a suggestion email. */
expect val systemName: String

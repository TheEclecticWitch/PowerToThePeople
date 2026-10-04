package com.theeclecticwitch.powertothepeople.data

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/** Each desktop system's own place for application data, so it is covered by the user's backups. */
actual fun appDataDirectory(): Path {
    val os = System.getProperty("os.name").lowercase()
    val home = System.getProperty("user.home")
    val base = when {
        os.contains("win") -> System.getenv("APPDATA") ?: "$home/AppData/Roaming"
        os.contains("mac") -> "$home/Library/Application Support"
        else -> System.getenv("XDG_DATA_HOME") ?: "$home/.local/share"
    }
    return base.toPath() / "PowerToThePeople"
}

actual val appFileSystem: FileSystem = FileSystem.SYSTEM

actual val systemName: String = System.getProperty("os.name") ?: "Desktop"

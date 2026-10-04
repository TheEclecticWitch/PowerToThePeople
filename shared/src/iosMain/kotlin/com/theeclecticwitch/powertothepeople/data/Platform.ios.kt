package com.theeclecticwitch.powertothepeople.data

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/** Application Support: backed up with the phone, never shown in the Files app. */
actual fun appDataDirectory(): Path {
    val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
        .first() as String
    return base.toPath() / "PowerToThePeople"
}

actual val appFileSystem: FileSystem = FileSystem.SYSTEM

actual val systemName: String =
    platform.UIKit.UIDevice.currentDevice.let { "${it.systemName} ${it.systemVersion}" }

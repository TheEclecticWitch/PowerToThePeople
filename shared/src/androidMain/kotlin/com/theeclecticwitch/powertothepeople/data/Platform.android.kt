package com.theeclecticwitch.powertothepeople.data

import android.content.Context
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

private lateinit var appContext: Context

/** Called first thing by MainActivity, before anything asks where its files live. */
fun initialiseAndroidContext(context: Context) {
    appContext = context.applicationContext
}

actual fun appDataDirectory(): Path = appContext.filesDir.absolutePath.toPath()

actual val appFileSystem: FileSystem = FileSystem.SYSTEM

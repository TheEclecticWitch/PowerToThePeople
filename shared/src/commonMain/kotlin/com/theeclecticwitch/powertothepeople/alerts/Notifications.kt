package com.theeclecticwitch.powertothepeople.alerts

import androidx.compose.runtime.Composable

/**
 * The device's side of alerts: a check every few hours while the app is closed, and the notifications
 * it posts. Android runs the check through WorkManager; on an iPhone iOS decides when (if at all) to
 * run it, so alerts there can come hours late. The desktop app checks only while it is open.
 */
expect object Notifications {
    /** Whether this platform can check in the background and notify at all. */
    val supported: Boolean

    /** Starts or stops the background checks. Safe to call again with the same value. */
    fun schedule(on: Boolean)

    fun post(title: String, text: String)
}

/** Returns a function that asks for permission to notify, then reports the answer. */
@Composable
expect fun rememberNotificationPermission(onResult: (granted: Boolean) -> Unit): () -> Unit

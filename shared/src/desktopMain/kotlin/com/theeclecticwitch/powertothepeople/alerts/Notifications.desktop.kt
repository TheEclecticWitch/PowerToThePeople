package com.theeclecticwitch.powertothepeople.alerts

import androidx.compose.runtime.Composable

/** On a computer the app checks when it opens, and what's new waits in the Alerts list. */
actual object Notifications {
    actual val supported: Boolean = false

    actual fun schedule(on: Boolean) {}

    actual fun post(title: String, text: String) {}
}

@Composable
actual fun rememberNotificationPermission(onResult: (granted: Boolean) -> Unit): () -> Unit = { onResult(true) }

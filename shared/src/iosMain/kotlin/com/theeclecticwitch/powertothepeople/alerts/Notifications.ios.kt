package com.theeclecticwitch.powertothepeople.alerts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSUUID
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

/** Must match BGTaskSchedulerPermittedIdentifiers in iosApp/project.yml. */
private const val TASK = "com.theeclecticwitch.powertothepeople.alerts"
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Called by the Swift app as it starts: iOS insists background tasks are registered before launch
 * finishes. iOS then runs the check when it judges best, often hours apart.
 */
fun registerBackgroundChecks() {
    BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(TASK, usingQueue = null) { task ->
        val refresh = task as? BGAppRefreshTask ?: return@registerForTaskWithIdentifier
        submitNext()
        val job = scope.launch {
            Alerts.check(notify = true)
            refresh.setTaskCompletedWithSuccess(true)
        }
        refresh.expirationHandler = {
            job.cancel()
            refresh.setTaskCompletedWithSuccess(false)
        }
    }
    if (Alerts.prefs.value.notify) submitNext()
}

@OptIn(ExperimentalForeignApi::class)
private fun submitNext() {
    val request = BGAppRefreshTaskRequest(TASK)
    request.earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(4.0 * 60 * 60)
    BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)
}

actual object Notifications {
    actual val supported: Boolean = true

    actual fun schedule(on: Boolean) {
        if (on) submitNext() else BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(TASK)
    }

    actual fun post(title: String, text: String) {
        val content = UNMutableNotificationContent()
        content.setTitle(title)
        content.setBody(text)
        val request = UNNotificationRequest.requestWithIdentifier(NSUUID().UUIDString, content, null)
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request, null)
    }
}

@Composable
actual fun rememberNotificationPermission(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    return {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { granted, _ ->
            scope.launch(Dispatchers.Main) { callback.value(granted) }
        }
    }
}

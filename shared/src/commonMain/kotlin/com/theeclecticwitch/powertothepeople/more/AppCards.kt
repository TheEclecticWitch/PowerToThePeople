package com.theeclecticwitch.powertothepeople.more

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import com.theeclecticwitch.powertothepeople.share.rateAppLink
import com.theeclecticwitch.powertothepeople.share.shareCopies
import com.theeclecticwitch.powertothepeople.share.shareText
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.openSafely
import kotlinx.coroutines.delay

/**
 * The three cards every one of Rod's store apps carries, together and in this order: Share this App, Rate this
 * App, More Apps by The Eclectic Witch. They live in Settings (moved from More, Rod, 2026-10-24).
 */
@Composable
fun AppCards() {
    ShareAppCard()
    val uri = LocalUriHandler.current
    // Shown only where the app has a live store listing to review.
    rateAppLink?.let { link ->
        InfoCard(title = "Rate this App", onClick = { openSafely(uri, link) }) {
            Text("Leave a rating or review in the store. It helps other people find the app.", style = MaterialTheme.typography.bodyLarge)
        }
    }
    InfoCard(title = "More Apps by The Eclectic Witch", onClick = { openSafely(uri, DEVELOPER_URL) }) {
        Text("Other apps from the maker of this one, on the developer's website.", style = MaterialTheme.typography.bodyLarge)
    }
}

const val DEVELOPER_URL = "https://00theeclecticwitch00.com/"

private const val SHARE_APP_TEXT = "P2P: Power to the People - your government in plain words, neutral, sourced and free. " +
    "Get it on Android: https://play.google.com/store/apps/details?id=com.theeclecticwitch.powertothepeople - " +
    "on iPhone and iPad: https://apps.apple.com/app/id6818878317 - " +
    "on Windows: https://apps.microsoft.com/detail/9N1XKML0JKQJ - and for every platform: " +
    "https://00theeclecticwitch00.com/power-to-the-people/"

/** Tells a friend about the app. A computer has no share sheet, so there it copies and says so for a moment. */
@Composable
private fun ShareAppCard() {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2500)
            copied = false
        }
    }
    InfoCard(title = "Share this App", onClick = {
        shareText("P2P: Power to the People", SHARE_APP_TEXT)
        if (shareCopies) copied = true
    }) {
        Text(
            when {
                copied -> "Copied. Paste it into a message or email to share."
                shareCopies -> "Copy a note about this app, with links, to send to a friend."
                else -> "Send a friend a note about this app, with links."
            },
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

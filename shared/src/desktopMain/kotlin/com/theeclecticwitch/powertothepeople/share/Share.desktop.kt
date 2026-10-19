package com.theeclecticwitch.powertothepeople.share

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

actual fun shareText(subject: String, text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

actual val shareCopies: Boolean = true

// The computer build runs on both Windows and the Mac, so the store is picked by the system it is on.
actual val rateAppLink: String? = System.getProperty("os.name").orEmpty().lowercase().let { os ->
    when {
        os.contains("win") -> StoreReviewLinks.MICROSOFT_STORE
        os.contains("mac") -> StoreReviewLinks.APP_STORE
        else -> null
    }
}

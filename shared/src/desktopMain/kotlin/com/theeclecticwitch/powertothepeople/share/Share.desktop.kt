package com.theeclecticwitch.powertothepeople.share

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

actual fun shareText(subject: String, text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

actual val shareCopies: Boolean = true

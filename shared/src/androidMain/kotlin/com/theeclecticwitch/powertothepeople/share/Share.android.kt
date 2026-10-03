package com.theeclecticwitch.powertothepeople.share

import android.content.Intent
import com.theeclecticwitch.powertothepeople.data.androidContext

actual fun shareText(subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    androidContext().startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

actual val shareCopies: Boolean = false

package com.theeclecticwitch.powertothepeople.share

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** The share icon for a page's top bar. [content] is null until the page has loaded what it would share. */
@Composable
fun ShareButton(content: Pair<String, String>?) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
    IconButton(enabled = content != null, onClick = {
        val (subject, text) = content ?: return@IconButton
        shareText(subject, text)
        if (shareCopies) copied = true
    }) {
        Icon(
            if (copied) Icons.Filled.Check else Icons.Filled.Share,
            contentDescription = if (shareCopies) (if (copied) "Copied" else "Copy to share") else "Share",
        )
    }
}

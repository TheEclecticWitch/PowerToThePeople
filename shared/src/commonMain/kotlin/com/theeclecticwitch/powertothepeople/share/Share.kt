package com.theeclecticwitch.powertothepeople.share

/**
 * Hands plain text to the device's own share sheet (Messages, mail, anything the reader has). Outbound
 * only: nothing comes back into the app. On a computer the text goes to the clipboard instead.
 */
expect fun shareText(subject: String, text: String)

/** Whether [shareText] copies rather than opens a share sheet, so the button can say what happened. */
expect val shareCopies: Boolean

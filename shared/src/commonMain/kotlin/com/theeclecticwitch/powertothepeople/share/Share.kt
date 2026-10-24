package com.theeclecticwitch.powertothepeople.share

/**
 * Hands plain text to the device's own share sheet (Messages, mail, anything the reader has). Outbound
 * only: nothing comes back into the app. On a computer the text goes to the clipboard instead.
 */
expect fun shareText(subject: String, text: String)

/** Whether [shareText] copies rather than opens a share sheet, so the button can say what happened. */
expect val shareCopies: Boolean

/**
 * Each store's "write a review" page for this app. Fill in Play and the App Store once each listing is live;
 * until then the Rate card stays hidden there, so nothing broken shows.
 */
object StoreReviewLinks {
    const val MICROSOFT_STORE = "ms-windows-store://review/?ProductId=9N1XKML0JKQJ"
    // Live on Google Play since 2026-10-22. The web address opens the Play Store app on a phone that has it.
    val GOOGLE_PLAY: String? = "https://play.google.com/store/apps/details?id=com.theeclecticwitch.powertothepeople"
    val APP_STORE: String? = null // iPhone, iPad and Mac: "https://apps.apple.com/app/id<number>?action=write-review"
}

/** The review page for the store this copy came from, or null where the app isn't in a store yet. */
expect val rateAppLink: String?

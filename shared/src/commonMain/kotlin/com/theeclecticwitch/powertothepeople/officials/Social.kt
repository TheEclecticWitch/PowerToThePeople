package com.theeclecticwitch.powertothepeople.officials

/**
 * Officials' accounts on other services, as plain links out. Power to the People doesn't show anyone's posts;
 * it only tells people where an official can be followed (Rod's decision, October 2026).
 */
object Social {
    /** The @unitedstates and Open States handles -> links, in a fixed order. */
    fun links(handles: Map<String, String>): List<Link> = listOfNotNull(
        handles["twitter"]?.let { Link("X (Twitter): @$it", "https://x.com/$it") },
        handles["facebook"]?.let { Link("Facebook", "https://www.facebook.com/$it") },
        handles["instagram"]?.let { Link("Instagram: @$it", "https://www.instagram.com/$it") },
        (handles["youtube_id"]?.let { "https://www.youtube.com/channel/$it" }
            ?: handles["youtube"]?.let { "https://www.youtube.com/$it" })?.let { Link("YouTube", it) },
        handles["mastodon"]?.let { mastodonLink(it) },
    )

    /** "@user@host" -> https://host/@user */
    private fun mastodonLink(handle: String): Link? {
        val parts = handle.trimStart('@').split('@')
        return if (parts.size == 2) Link("Mastodon: $handle", "https://${parts[1]}/@${parts[0]}") else null
    }

    /**
     * The White House's own accounts belong to the office and pass to each new President. A President's
     * personal account is listed only for the person who holds it.
     */
    fun executive(isPresident: Boolean, name: String): List<Link> =
        if (isPresident) {
            listOfNotNull(
                Link("X (Twitter): @POTUS", "https://x.com/POTUS"),
                Link("X (Twitter): @WhiteHouse", "https://x.com/WhiteHouse"),
                if (name.contains("Trump")) Link("Truth Social: @realDonaldTrump", "https://truthsocial.com/@realDonaldTrump") else null,
                Link("Facebook: The White House", "https://www.facebook.com/WhiteHouse"),
                Link("Instagram: @whitehouse", "https://www.instagram.com/whitehouse"),
                Link("YouTube: The White House", "https://www.youtube.com/@WhiteHouse"),
            )
        } else {
            listOf(Link("X (Twitter): @VP", "https://x.com/VP"))
        }
}

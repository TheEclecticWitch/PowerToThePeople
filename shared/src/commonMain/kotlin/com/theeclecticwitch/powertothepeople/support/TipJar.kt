package com.theeclecticwitch.powertothepeople.support

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Tip the Creator (Rod, 2026-10-22, for all his apps; built here 2026-10-24, copied from Consciousness Theories), in
 * Settings and About only and never a pop-up. It replaced the three fixed donations ($1.99 / $4.99 / $9.99,
 * tip_small / medium / large, and on Windows three Microsoft Store add-ons).
 *
 * The person types the amount. Apple and Google take a tip to an app's maker only through their own in-app purchase,
 * which sells only fixed-price items made in advance, so there is one consumable per whole dollar, `p2p_tip_1` to
 * `p2p_tip_100`, in Play Console and App Store Connect alike (made by StoreTools/tips_play.py and tips_apple.py with
 * the prefix p2p_tip_: Apple wants product IDs unique across the whole account, and the Book holds tip_1..., Consciousness
 * Theories ct_tip_1... and tip_small...). Windows sends the person to Ko-fi instead, where any amount can be given - the
 * Microsoft Store allows a voluntary donation that unlocks nothing through an outside payment (policy 10.8.2).
 *
 * A tip unlocks nothing: the app stays the same for everyone.
 */
object TipJar {
    const val LOWEST = 1
    const val HIGHEST = 100
    const val KO_FI = "https://ko-fi.com/00theeclecticwitch00"

    fun productId(dollars: Int): String = "p2p_tip_$dollars"
    val productIds: List<String> = (LOWEST..HIGHEST).map(::productId)

    /** The three donations sold before Tip the Creator (Play 1.1). Still finished if one turns up unconsumed. */
    val oldProductIds: List<String> = listOf("tip_small", "tip_medium", "tip_large")

    /** What the person typed as a tip, or null when it is not a whole number of dollars in range. */
    fun dollars(typed: String): Int? = typed.trim().removePrefix("$").trim().toIntOrNull()?.takeIf { it in LOWEST..HIGHEST }

    private val _prices = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _message = MutableStateFlow<String?>(null)
    private val _thanked = MutableStateFlow(false)

    /** Product ID to the price as the store shows it: "$7.00", or "6,99 €" abroad. */
    val prices: StateFlow<Map<String, String>> = _prices.asStateFlow()

    /** A note about the last attempt, such as a payment still pending. */
    val message: StateFlow<String?> = _message.asStateFlow()

    /** True once a tip has gone through in this session, to say thank you. */
    val thanked: StateFlow<Boolean> = _thanked.asStateFlow()

    fun setPrice(productId: String, price: String?) {
        _prices.update { if (price == null) it - productId else it + (productId to price) }
    }

    fun setMessage(message: String?) {
        _message.value = message
    }

    /** Called by the store code when a tip has been paid. */
    fun thank() {
        _message.value = null
        _thanked.value = true
    }
}

/** How this device takes a tip. */
enum class TipWay {
    /** The platform's store, with an amount typed in the app. */
    STORE,
    /** Ko-fi in the browser, any amount (Windows). */
    KO_FI,
    /** Nowhere: the card is not shown (a Mac copy from outside the Mac App Store). */
    NONE,
}

/** The platform's store, reporting back through [TipJar]. */
expect object Store {
    val way: TipWay

    /** "Google Play" or "the App Store", for the line under the amount. */
    val name: String

    fun connect()
    fun tip(dollars: Int)
}

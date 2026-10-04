package com.theeclecticwitch.powertothepeople.support

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The three donation sizes (Rod, 2026-10-04). Each is a consumable in-app product with this ID in
 * both the Play Console and App Store Connect, so people can give more than once. Apple and Google
 * both require their own payment for gifts to an app's maker; a web link isn't allowed in the app.
 * [usualPrice] is shown only until the store answers with the price in the person's own currency.
 */
enum class Tip(val productId: String, val usualPrice: String) {
    SMALL("tip_small", "$1.99"),
    MEDIUM("tip_medium", "$4.99"),
    LARGE("tip_large", "$9.99"),
}

/**
 * Where donations stand, as the platform's store reports it. Each platform's store code fills
 * this in - Play Billing on Android, StoreKit in Swift on iOS - and the Support card reads it.
 * A donation unlocks nothing: the app stays the same for everyone.
 */
object Donations {
    private val _prices = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _message = MutableStateFlow<String?>(null)
    private val _thanked = MutableStateFlow(false)

    /** Product ID to the price as the store shows it: "$4.99", or "4,99 €" abroad. */
    val prices: StateFlow<Map<String, String>> = _prices.asStateFlow()

    /** A note about the last attempt, such as a payment still pending. */
    val message: StateFlow<String?> = _message.asStateFlow()

    /** True once a donation has gone through in this session, to say thank you. */
    val thanked: StateFlow<Boolean> = _thanked.asStateFlow()

    fun setPrice(productId: String, price: String?) {
        _prices.update { if (price == null) it - productId else it + (productId to price) }
    }

    fun setMessage(message: String?) {
        _message.value = message
    }

    /** Called by the store code when a donation has been paid. */
    fun thank() {
        _message.value = null
        _thanked.value = true
    }
}

/** The platform's store. [available] is false where there is none - the desktop builds - and the card hides. */
expect object Store {
    val available: Boolean

    /** "Google Play" or "the App Store", for the line under the buttons. */
    val name: String

    fun connect()
    fun donate(tip: Tip)
}

package com.theeclecticwitch.powertothepeople.support

/**
 * StoreKit 2 is a Swift-only API, so the Swift host fills these in at start-up (see
 * Donations.swift), and reports back through [Donations].
 */
object StoreBridge {
    var connect: () -> Unit = {}
    var donate: (String) -> Unit = {}
}

actual object Store {
    actual val available: Boolean = true
    actual val name: String = "the App Store"

    actual fun connect() = StoreBridge.connect()
    actual fun donate(tip: Tip) = StoreBridge.donate(tip.productId)
}

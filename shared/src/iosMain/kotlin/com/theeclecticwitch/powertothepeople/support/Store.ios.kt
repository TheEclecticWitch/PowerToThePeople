package com.theeclecticwitch.powertothepeople.support

/**
 * StoreKit 2 is a Swift-only API, so the Swift host fills these in at start-up (see
 * TipStore.swift), and reports back through [TipJar].
 */
object StoreBridge {
    var connect: () -> Unit = {}
    var tip: (String) -> Unit = {}
}

actual object Store {
    actual val way: TipWay = TipWay.STORE
    actual val name: String = "the App Store"

    actual fun connect() = StoreBridge.connect()
    actual fun tip(dollars: Int) = StoreBridge.tip(TipJar.productId(dollars))
}

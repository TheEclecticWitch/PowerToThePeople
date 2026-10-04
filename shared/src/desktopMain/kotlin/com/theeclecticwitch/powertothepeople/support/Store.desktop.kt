package com.theeclecticwitch.powertothepeople.support

/** The desktop builds aren't sold through a store, so they don't offer donations. */
actual object Store {
    actual val available: Boolean = false
    actual val name: String = ""

    actual fun connect() = Unit
    actual fun donate(tip: Tip) = Unit
}

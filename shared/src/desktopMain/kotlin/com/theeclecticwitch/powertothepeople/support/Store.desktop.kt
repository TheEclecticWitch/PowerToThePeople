package com.theeclecticwitch.powertothepeople.support

import com.sun.jna.NativeLibrary
import java.io.File
import kotlin.concurrent.thread

/**
 * One desktop build serves both computers, and each takes a tip its own way (as in Consciousness Theories and the
 * Book of Shadows):
 *
 * - **Windows** sends the person to Ko-fi, any amount (Rod, 2026-10-24: Ko-fi on every Microsoft Store app without a
 *   purchase to unlock; it replaced three Microsoft Store add-ons and StoreHelper.exe. Store policy 10.8.2 allows a
 *   voluntary donation that unlocks nothing through an outside payment - say so in the Partner Center notes).
 * - **The Mac App Store copy** takes the typed amount through StoreKit, as the iPhone does, by way of the small Swift
 *   library shipped inside it (desktopApp/macos/TipStoreKit.swift). A Mac copy from anywhere else has nowhere to pay,
 *   so the card hides there.
 */
actual object Store {
    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    /** The Swift library, found only in a sandboxed (Mac App Store) copy that carries it. */
    private val storeKit: NativeLibrary? by lazy {
        val sandboxed = System.getProperty("os.name").orEmpty().startsWith("Mac") && System.getenv("APP_SANDBOX_CONTAINER_ID") != null
        if (!sandboxed) return@lazy null
        val dir = System.getProperty("compose.application.resources.dir") ?: return@lazy null
        val file = File(dir, "libp2pstorekit.dylib").takeIf { it.isFile } ?: return@lazy null
        runCatching { NativeLibrary.getInstance(file.absolutePath) }.getOrNull()
    }

    actual val way: TipWay = when {
        windows -> TipWay.KO_FI
        storeKit != null -> TipWay.STORE
        else -> TipWay.NONE
    }
    actual val name: String = if (windows) "Ko-fi" else "the App Store"

    actual fun connect() {
        val kit = storeKit ?: return
        // StoreKit waits on the network: never on the window's thread.
        thread(isDaemon = true, name = "Tips") {
            runCatching {
                if (kit.getFunction("p2p_tip_finish_waiting").invokeInt(arrayOf()) > 0) TipJar.thank()
                val answer = kit.getFunction("p2p_tip_prices").invokePointer(arrayOf(TipJar.productIds.joinToString("\n")))
                    ?: return@runCatching
                val lines = answer.getString(0, "UTF-8")
                kit.getFunction("p2p_tip_free").invokeVoid(arrayOf(answer))
                lines.lineSequence().filter { '\t' in it }.forEach { TipJar.setPrice(it.substringBefore('\t'), it.substringAfter('\t')) }
            }
        }
    }

    actual fun tip(dollars: Int) {
        val kit = storeKit ?: return
        TipJar.setMessage(null)
        thread(isDaemon = true, name = "Tip") {
            val answer = runCatching { kit.getFunction("p2p_tip_buy").invokeInt(arrayOf(TipJar.productId(dollars))) }.getOrDefault(4)
            when (answer) {
                0 -> TipJar.thank()
                1 -> Unit
                2 -> TipJar.setMessage("Your tip is waiting for approval. It will go through on its own; thank you!")
                3 -> TipJar.setMessage("The App Store couldn't confirm the tip. Please check your App Store purchase history before trying again.")
                5 -> TipJar.setMessage("The App Store isn't available right now. Check your connection and try again.")
                else -> TipJar.setMessage("The tip didn't go through. Please try again.")
            }
        }
    }
}

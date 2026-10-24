package com.theeclecticwitch.powertothepeople.support

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * ===== FILL IN HERE =====
 * Each donation's Store ID from Partner Center (P2P > Add-ons > the add-on: a 12-character code
 * like 9NBLGGH4R315). Make each one a "Developer-managed consumable" with the product ID shown in
 * the comment. Until all three are filled in, the Windows app doesn't show the Support card.
 */
private val WINDOWS_STORE_IDS: Map<Tip, String?> = mapOf(
    Tip.SMALL to null,   // tip_small, $1.99
    Tip.MEDIUM to null,  // tip_medium, $4.99
    Tip.LARGE to null,   // tip_large, $9.99
)

private val onWindows = System.getProperty("os.name").startsWith("Windows")

/**
 * The Microsoft Store on Windows, through StoreHelper.exe packed beside the app (the JVM cannot
 * reach the Store's Windows Runtime API itself; see desktopApp/windows-store). The helper runs with
 * the app's package identity, so it only works in the copy installed from the Store (or a sideloaded
 * MSIX). The Mac (Mac App Store) offers no donations for now, and neither does `gradlew :desktopApp:run`
 * or the plain .msi installer: the Support card stays hidden there.
 */
actual object Store {
    actual val available: Boolean by lazy {
        onWindows && WINDOWS_STORE_IDS.values.all { !it.isNullOrBlank() } && helper != null && inMsixPackage()
    }
    actual val name: String = "the Microsoft Store"

    actual fun connect() {
        if (!available) return
        val ids = Tip.entries.associateBy { WINDOWS_STORE_IDS[it]!! }
        ask(listOf("prices") + ids.keys) { answer ->
            ids.forEach { (storeId, tip) -> answer["price.$storeId"]?.let { Donations.setPrice(tip.productId, it) } }
        }
    }

    actual fun donate(tip: Tip) {
        val storeId = WINDOWS_STORE_IDS[tip]
        if (!available || storeId == null) return
        Donations.setMessage(null)
        ask(listOf("buy", storeId)) { answer ->
            when (answer["result"]) {
                "bought" -> Donations.thank()
                "cancelled" -> Unit
                null -> Donations.setMessage(UNAVAILABLE)
                else -> Donations.setMessage("The donation didn't go through. Please try again.")
            }
        }
    }

    private const val UNAVAILABLE = "The Microsoft Store isn't available right now. Check your connection and try again."

    private val helper: File?
        get() = System.getProperty("compose.application.resources.dir")
            ?.let { File(it, "StoreHelper.exe") }
            ?.takeIf { it.isFile }

    /**
     * True when this copy was installed as an MSIX (from the Store, or sideloaded): its folder holds
     * the package's AppxManifest.xml. jpackage sets `jpackage.app-path` (the launcher) in every packaged copy.
     */
    private fun inMsixPackage(): Boolean =
        System.getProperty("jpackage.app-path")?.let { File(it).parentFile?.resolve("AppxManifest.xml")?.isFile } == true

    /** Runs the helper off the screen's thread and hands back its key=value lines. */
    private fun ask(args: List<String>, then: (Map<String, String>) -> Unit) {
        val exe = helper ?: return Donations.setMessage(UNAVAILABLE)
        thread(isDaemon = true, name = "microsoft-store") {
            val answer = runCatching {
                val process = ProcessBuilder(listOf(exe.absolutePath) + args).redirectErrorStream(true).start()
                // A donation waits on the person, so it gets as long as they need; a price check does not.
                val lines = process.inputStream.bufferedReader().readLines()
                process.waitFor(if (args.first() == "buy") 30L else 1L, TimeUnit.MINUTES)
                lines.mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
                    .toMap()
            }.getOrElse { mapOf("error" to (it.message ?: "")) }
            then(answer)
        }
    }
}

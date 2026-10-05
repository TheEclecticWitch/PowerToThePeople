package com.theeclecticwitch.powertothepeople

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import javax.imageio.ImageIO
import com.theeclecticwitch.powertothepeople.ui.TextSize
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

/**
 * The Mac App Store screenshots. macOS won't let a picture of the screen be taken over SSH, so the
 * desktop app is run off-screen here instead, at the size of a 1280 by 760 point window on a Retina
 * screen, and tools/mac_frames.py puts each picture in a Mac window at 2880 by 1800.
 *
 * Runs only when asked for (see the desktopTest settings in build.gradle.kts), with its own data folder.
 */
@OptIn(ExperimentalTestApi::class)
class MacStoreShots {
    private val out = System.getProperty("storeShots")?.let { File(it) }

    private object Resumed : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun shots() {
        val out = out ?: return  // an ordinary test run skips this
        out.mkdirs()
        File(out, "appdata").deleteRecursively()  // start fresh, as a new reader would
        // Navigation insists on the "main" thread, which on a desktop is Swing's: the whole run happens there.
        TextSize.setTheme("dark")  // like the phone and iPad pictures
        javax.swing.SwingUtilities.invokeAndWait {
            // The app keeps a few timers running (the debt counter, the countdown), which the test runner
            // reports as unfinished work once the tour is over. Only a missing picture is a failure.
            runCatching { tour(out) }.onFailure { if (!File(out, "8-constitution.png").exists()) throw it }
        }
    }

    private fun tour(out: File) {
        File(out, "8-constitution.png").delete()
        runDesktopComposeUiTest(width = 2560, height = 1520, testTimeout = 15.minutes) {
            setContent {
                // A real window is "resumed"; the test host isn't, and leaving a page then fails.
                CompositionLocalProvider(LocalDensity provides Density(2f), LocalLifecycleOwner provides Resumed) {
                    Box(Modifier.size(1280.dp, 760.dp)) { App() }
                }
            }
            fun settle(seconds: Int) {
                repeat(seconds * 4) { Thread.sleep(250); mainClock.advanceTimeBy(250) }
                waitForIdle()
            }
            fun node(text: String, substring: Boolean = true): SemanticsNodeInteraction {
                println("tour: looking for $text")
                waitUntil("\"$text\" on screen", timeoutMillis = 30_000) { onAllNodes(hasText(text, substring = substring, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty() }
                return onAllNodes(hasText(text, substring = substring, ignoreCase = true))[0]
            }
            fun shot(name: String) {
                settle(2)
                val image = onRoot().captureToImage().toAwtImage()
                ImageIO.write(image, "png", File(out, "$name.png"))
            }
            settle(3)

            // The address used in every store picture: Independence Hall, so nobody's home shows.
            node("Set my location").performClick()
            settle(1)
            onAllNodes(hasSetTextAction())[0].performTextInput("520 Chestnut St, Philadelphia, PA 19106")
            node("Find my districts").performClick()
            node("See who represents me").performClick()
            node("Fetterman")
            settle(6)  // the portraits
            shot("1-overview")

            node("Representative").performClick()
            settle(5)
            shot("2-member")
            node("Overview", substring = false).performClick()
            settle(2)

            node("roll call").performScrollTo().performClick()
            settle(5)
            shot("3-vote")
            node("Overview", substring = false).performClick()
            settle(2)

            node("More", substring = false).performClick()
            settle(2)
            node("Legislation", substring = false).performClick()
            settle(3)
            onAllNodes(hasSetTextAction())[0].run { performTextInput("S. 2403"); performImeAction() }
            node("Retire through Ownership Act").performClick()
            settle(6)
            shot("4-bill")

            node("Overview", substring = false).performClick()
            settle(2)
            node("See who's running").performScrollTo().performClick()
            settle(5)
            node("See the candidates").performScrollTo().performClick()
            settle(4)
            shot("5-elections")

            node("Civics", substring = false).performClick()
            settle(3)
            shot("7-civics")
            node("Citizenship test", substring = false).performClick()
            settle(3)
            shot("6-citizenship-test")

            node("Constitution", substring = false).performClick()
            settle(4)
            // The articles are further down a lazy list, so they aren't there to find until scrolled to.
            val articles = hasText("The Legislative Branch", substring = true)
            val lists = onAllNodes(hasScrollToNodeAction())
            for (i in lists.fetchSemanticsNodes().indices) {
                if (runCatching { lists[i].performScrollToNode(articles) }.isSuccess) break
            }
            settle(1)
            node("The Legislative Branch").performClick()
            settle(3)
            shot("8-constitution")
        }
    }
}

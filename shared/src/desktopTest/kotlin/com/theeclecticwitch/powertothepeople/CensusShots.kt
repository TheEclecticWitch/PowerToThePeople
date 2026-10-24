package com.theeclecticwitch.powertothepeople

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
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

/**
 * Pictures of the Census screens at phone size, to check them by eye:
 * `gradlew :shared:desktopTest --tests "*CensusShots*" -PcensusShots=<folder>`.
 * Like the store pictures, it runs with its own data folder and the Independence Hall address.
 */
@OptIn(ExperimentalTestApi::class)
class CensusShots {
    private object Resumed : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun shots() {
        val out = System.getProperty("censusShots")?.let { File(it) } ?: return
        out.mkdirs()
        File(out, "appdata").deleteRecursively()
        var failure: Throwable? = null
        javax.swing.SwingUtilities.invokeAndWait { failure = runCatching { tour(out) }.exceptionOrNull() }
        if (!File(out, "9-debt.png").exists()) failure?.let { throw it }
    }

    private fun tour(out: File) {
        runDesktopComposeUiTest(width = 824, height = 1800, testTimeout = 10.minutes) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(2f), LocalLifecycleOwner provides Resumed) {
                    Box(Modifier.size(412.dp, 900.dp)) { App() }
                }
            }
            fun settle(seconds: Int) {
                repeat(seconds * 4) { Thread.sleep(250); mainClock.advanceTimeBy(250) }
                waitForIdle()
            }
            fun node(text: String, substring: Boolean = true): SemanticsNodeInteraction {
                println("tour: looking for $text")
                // Cards further down a long page aren't built until scrolled to.
                val lists = onAllNodes(hasScrollToNodeAction())
                for (i in lists.fetchSemanticsNodes().indices) {
                    if (runCatching { lists[i].performScrollToNode(hasText(text, substring = substring, ignoreCase = true)) }.isSuccess) break
                }
                waitForIdle()
                runCatching {
                    waitUntil("\"$text\" on screen", timeoutMillis = 45_000) {
                        onAllNodes(hasText(text, substring = substring, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty()
                    }
                }.onFailure {
                    // A picture of where the tour got stuck.
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "stuck.png"))
                    throw it
                }
                return onAllNodes(hasText(text, substring = substring, ignoreCase = true))[0]
            }
            fun shot(name: String) {
                settle(2)
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
            }
            settle(3)
            node("Set my location").performClick()
            settle(1)
            onAllNodes(hasSetTextAction())[0].performTextInput("520 Chestnut St, Philadelphia, PA 19106")
            node("Find my districts").performClick()
            node("See who represents me").performClick()
            settle(3)

            node("More", substring = false).performClick()
            settle(2)
            node("Your community by the numbers").performScrollTo().performClick()
            node("Population")
            shot("1-community")
            node("Citizenship", substring = false).performScrollTo()
            shot("2-citizenship")
            node("Show every year").performScrollTo()
            shot("3-history")
            node("Show the figures for").performScrollTo()
            onAllNodes(hasText("Philadelphia County") and hasClickAction())[0].performClick()
            settle(3)
            shot("debug-after-county")
            node("Where people born in other countries").performScrollTo()
            shot("4-born-in")
            node("All local governments").performScrollTo()
            shot("5-local")

            node("Choose another place").performScrollTo().performClick()
            node("Texas", substring = false).performScrollTo().performClick()
            node("Counties", substring = false).performClick()
            onAllNodes(hasSetTextAction())[0].performTextInput("Harris")
            settle(1)
            shot("6b-picker")
            node("Harris County", substring = false).performClick()
            node("Population")
            shot("6c-harris")
            node("Citizenship", substring = false).performScrollTo()
            shot("6d-harris-citizenship")
            node("Immigrants over the years").performScrollTo()
            shot("6e-harris-history")
            node("Show the figures for").performScrollTo()
            onAllNodes(hasText("United States", substring = false) and hasClickAction())[0].performClick()
            settle(3)
            node("Show every year").performScrollTo()
            shot("6f-us-history")

            node("More", substring = false).performClick()
            settle(2)
            node("Seats in the House").performScrollTo().performClick()
            node("Pennsylvania")
            shot("7-seats")

            node("More", substring = false).performClick()
            settle(2)
            node("Your vote").performScrollTo().performClick()
            node("By age").performScrollTo()
            shot("8-voting")

            node("More", substring = false).performClick()
            settle(2)
            node("Debt & deficit").performScrollTo().performClick()
            node("Interest on debt").performScrollTo()
            shot("9-debt")
        }
    }
}

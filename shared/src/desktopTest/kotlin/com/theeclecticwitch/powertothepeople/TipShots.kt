package com.theeclecticwitch.powertothepeople

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.theeclecticwitch.powertothepeople.support.TipCard
import com.theeclecticwitch.powertothepeople.support.TipJar
import com.theeclecticwitch.powertothepeople.support.TipWay
import com.theeclecticwitch.powertothepeople.ui.theme.PowerTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Draws the Tip the Creator card both ways - a store's typed amount and Windows' Ko-fi - into build/tips/, to look at
 * by eye, and the App Store review picture for p2p_tip_1 ... p2p_tip_100 (docs/store/apple/tip-review.png).
 */
class TipShots {
    @Test
    fun drawTipCard() {
        File("build/tips").mkdirs()
        TipJar.setPrice(TipJar.productId(5), "\$5.00")
        listOf(TipWay.STORE, TipWay.KO_FI).forEach { way ->
            listOf(false, true).forEach { dark ->
                ImageComposeScene(412, 520, Density(1f)) {
                    PowerTheme(dark) {
                        Surface(Modifier.fillMaxSize()) { Column(Modifier.padding(16.dp)) { TipCard(way, "Google Play", "5") } }
                    }
                }.use { scene ->
                    scene.render(0)
                    File("build/tips/$way-${if (dark) "dark" else "light"}.png")
                        .writeBytes(scene.render(500_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes)
                }
            }
        }
    }

    /** The card on an iPhone, $5 typed: Apple wants a picture of each in-app purchase where it's sold. */
    @Test
    fun drawAppleReviewPicture() {
        TipJar.setPrice(TipJar.productId(5), "\$5.00")
        ImageComposeScene(1242, 2208, Density(3f)) {
            PowerTheme(false) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 160.dp)) { TipCard(TipWay.STORE, "the App Store", "5") }
                }
            }
        }.use { scene ->
            scene.render(0)
            File("../docs/store/apple").mkdirs()
            File("../docs/store/apple/tip-review.png").writeBytes(scene.render(500_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }
}

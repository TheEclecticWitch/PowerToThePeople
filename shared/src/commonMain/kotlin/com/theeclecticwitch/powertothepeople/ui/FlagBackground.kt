package com.theeclecticwitch.powertothepeople.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val OldGloryRed = Color(0xFFB22234)
private val OldGloryBlue = Color(0xFF3C3B6E)

/**
 * The flag behind the whole app: a close-up of the canton and stripes, draped in soft vertical folds the way a
 * flag hangs from an indoor stand. Drawn rather than photographed, so it is sharp at any size, from a phone to a
 * wide desktop window. [wash] is laid over it, the screen's background color at partial strength, so text on top
 * stays easy to read.
 */
@Composable
fun FlagBackground(wash: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().drawWithCache {
            val w = size.width
            val h = size.height
            // Stripes and folds sized from the screen, so a phone and a desktop both see a close-up, not a postage stamp.
            val stripe = max(h / 9f, min(w, h) / 7f)
            val foldPeriod = max(w / 3.2f, stripe * 3.4f)
            val amplitude = stripe * 0.22f
            fun dy(x: Float, y: Float): Float {
                // Folds deepen toward the bottom, where the cloth hangs free.
                val depth = 0.55f + 0.45f * (y / h).coerceIn(0f, 1f)
                return amplitude * depth * (sin(2 * PI * x / foldPeriod).toFloat() + 0.35f * sin(2 * PI * x / (foldPeriod * 0.47f) + 1.3).toFloat())
            }
            val step = max(4f, w / 160f)
            fun wavyBand(top: Float, bottom: Float, left: Float, right: Float): Path = Path().apply {
                var x = left
                moveTo(x, top + dy(x, top))
                while (x < right) { x = min(right, x + step); lineTo(x, top + dy(x, top)) }
                while (x > left) { lineTo(x, bottom + dy(x, bottom)); x = max(left, x - step) }
                lineTo(left, bottom + dy(left, bottom))
                close()
            }
            val rows = (h / stripe).roundToInt() + 2
            val reds = (0 until rows step 2).map { i -> wavyBand(i * stripe - stripe, i * stripe, -step, w + step) }
            // The canton in the upper corner, about half the width, with the stripes running out beside and below it.
            val cantonW = if (w > h) w * 0.4f else w * 0.55f
            val cantonH = stripe * 4f
            val canton = wavyBand(-stripe, cantonH, -step, cantonW)
            val stars = buildStars(cantonW, cantonH, stripe, ::dy)
            // Light from the upper left: the slope of each fold sets how bright the cloth is there.
            val shades = (0..96).map { i ->
                val x = w * i / 96f
                // The slope of the fold curve above: its two waves, the second 0.35 as deep and 0.47 as wide.
                val slope = (cos(2 * PI * x / foldPeriod).toFloat() + 0.745f * cos(2 * PI * x / (foldPeriod * 0.47f) + 1.3).toFloat()) / 1.745f
                if (slope >= 0) Color.White.copy(alpha = 0.16f * min(1f, slope)) else Color.Black.copy(alpha = 0.28f * min(1f, -slope))
            }
            val shading = Brush.horizontalGradient(shades)
            onDrawBehind {
                drawRect(Color.White)
                reds.forEach { drawPath(it, OldGloryRed) }
                drawPath(canton, OldGloryBlue)
                stars.forEach { drawPath(it, Color.White) }
                drawRect(shading)
                drawRect(wash)
            }
        },
    )
}

/** Rows of six and five, offset like the real canton, filling whatever shape the canton has on this screen. */
private fun buildStars(w: Float, h: Float, stripe: Float, dy: (Float, Float) -> Float): List<Path> {
    val gap = stripe * 0.42f
    val radius = gap * 0.3f
    val paths = mutableListOf<Path>()
    var row = 0
    var y = gap * 0.7f
    while (y < h - gap * 0.4f) {
        var x = gap * (if (row % 2 == 0) 0.7f else 1.2f)
        while (x < w - gap * 0.4f) {
            paths += star(x, y + dy(x, y), radius)
            x += gap
        }
        y += gap * 0.86f
        row++
    }
    return paths
}

private fun star(cx: Float, cy: Float, r: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) r else r * 0.4f
        val angle = -PI / 2 + i * PI / 5
        val p = Offset(cx + (radius * cos(angle)).toFloat(), cy + (radius * sin(angle)).toFloat())
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

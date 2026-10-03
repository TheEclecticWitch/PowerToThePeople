package com.theeclecticwitch.powertothepeople.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/** The page's whole width, without the reading-width cap: for pages laid out as cards in columns. */
@Composable
fun PageColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { content() }
}

/**
 * A scrolling page of cards. On a phone, one column, exactly as before. On a wide window, two or three columns,
 * each card going into the shortest column so the page fills evenly, like a newspaper. Anything marked with
 * [Modifier.fullWidth] (a page's title, a big header) spans every column and starts a new row.
 */
@Composable
fun CardPage(spacing: Dp = 12.dp, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = when {
            maxWidth >= 1300.dp -> 3
            maxWidth >= 860.dp -> 2
            else -> 1
        }
        if (columns == 1) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = ReadingWidth).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) { content() }
            }
        } else {
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
                Masonry(columns, spacing, Modifier.widthIn(max = 1500.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), content)
            }
        }
    }
}

private object FullWidth : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = FullWidth
}

/** In a [CardPage] on a wide window, this item spans all the columns. On a phone it changes nothing. */
fun Modifier.fullWidth(): Modifier = this.then(FullWidth)

@Composable
private fun Masonry(columns: Int, spacing: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val width = constraints.maxWidth
        val columnWidth = (width - gap * (columns - 1)) / columns
        val heights = IntArray(columns)
        val placed = measurables.map { m ->
            if (m.parentData == FullWidth) {
                val p = m.measure(Constraints(minWidth = width, maxWidth = width))
                val y = heights.max()
                for (i in heights.indices) heights[i] = y + p.height + gap
                Triple(p, 0, y)
            } else {
                val p = m.measure(Constraints(minWidth = columnWidth, maxWidth = columnWidth))
                val col = heights.indices.minBy { heights[it] }
                val y = heights[col]
                heights[col] = y + p.height + if (p.height > 0) gap else 0
                Triple(p, col * (columnWidth + gap), y)
            }
        }
        val height = max(0, heights.max() - gap)
        layout(width, height) { placed.forEach { (p, x, y) -> p.place(x, y) } }
    }
}

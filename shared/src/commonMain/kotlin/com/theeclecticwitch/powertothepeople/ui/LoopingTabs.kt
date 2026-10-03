package com.theeclecticwitch.powertothepeople.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import com.theeclecticwitch.powertothepeople.ui.theme.OnSurfaceColors
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** So many pages that no one swipes to either end; the pager starts in the middle. */
private const val PAGES = 10_000

/** The space a Material tab keeps on each side of its label. */
private val TAB_PADDING = 16.dp

/**
 * Tabs across the top that can also be swiped, round and round: past the last tab comes the first again, in
 * either direction. Each tab keeps what it was showing (search text, scroll position) while you're away.
 */
@Composable
fun LoopingTabs(labels: List<String>, modifier: Modifier = Modifier, content: @Composable (tab: Int) -> Unit) {
    val count = labels.size
    val middle = PAGES / 2 - (PAGES / 2) % count
    val pager = rememberPagerState(initialPage = middle) { PAGES }
    val scope = rememberCoroutineScope()
    val saved = rememberSaveableStateHolder()
    val selected = pager.currentPage % count
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.titleSmall
    val density = LocalDensity.current
    Column(modifier) {
        // The tabs sit with the top bar, in the chosen theme's colors.
        OnSurfaceColors { BoxWithConstraints {
            // One size for every label, the largest that keeps each on one line, so "Citizenship test" doesn't
            // break in two on a phone. (FitText can't go inside a Tab: the row asks its labels for their height.)
            val room = with(density) { (maxWidth / count - TAB_PADDING * 2).roundToPx() }
            val size = remember(labels, style, room) {
                var sp = style.fontSize.value
                while (sp > 9f && labels.any { measurer.measure(it, style.copy(fontSize = sp.sp), softWrap = false).size.width > room }) sp -= 0.5f
                sp.sp
            }
            PrimaryTabRow(selectedTabIndex = selected) {
                labels.forEachIndexed { i, label ->
                    Tab(
                        selected = selected == i,
                        onClick = {
                            // The shortest way round to that tab.
                            var step = i - selected
                            if (step > count / 2) step -= count
                            if (step < -count / 2) step += count
                            scope.launch { pager.animateScrollToPage(pager.currentPage + step) }
                        },
                        text = { Text(label, style = style.copy(fontSize = size), maxLines = 1, softWrap = false) },
                    )
                }
            }
        } }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) { page ->
            val tab = page % count
            Box(Modifier.fillMaxSize()) {
                saved.SaveableStateProvider(tab) { content(tab) }
            }
        }
    }
}

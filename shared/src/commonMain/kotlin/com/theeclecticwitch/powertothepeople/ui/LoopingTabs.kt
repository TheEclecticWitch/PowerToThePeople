package com.theeclecticwitch.powertothepeople.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/** So many pages that no one swipes to either end; the pager starts in the middle. */
private const val PAGES = 10_000

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
    Column(modifier) {
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
                    text = { Text(label) },
                )
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) { page ->
            val tab = page % count
            Box(Modifier.fillMaxSize()) {
                saved.SaveableStateProvider(tab) { content(tab) }
            }
        }
    }
}

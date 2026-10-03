package com.theeclecticwitch.powertothepeople.constitution

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.theeclecticwitch.powertothepeople.ui.AppTopBar
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.LoadingBox
import com.theeclecticwitch.powertothepeople.ui.SourceLine
import com.theeclecticwitch.powertothepeople.ui.openSafely

/**
 * The original parchments, photographed by the National Archives. The app shows phone-sized copies (from
 * Wikimedia Commons, which hosts the Archives' public-domain scans at any size); full-size scans are a link
 * away at the Archives itself.
 */
data class OriginalPage(val document: String, val page: Int, val pages: Int, val path: String, val note: String) {
    private val file get() = path.substringAfterLast('/')
    val thumbnail get() = "$COMMONS/thumb/$path/500px-$file"
    val image get() = "$COMMONS/thumb/$path/1920px-$file"
    val label get() = if (pages > 1) "$document, page $page of $pages" else document

    companion object {
        private const val COMMONS = "https://upload.wikimedia.org/wikipedia/commons"
    }
}

object Originals {
    const val FULL_SIZE = "https://www.archives.gov/founding-docs/downloads"
    const val VISIT = "https://visit.archives.gov/visit"

    val pages = listOf(
        OriginalPage(
            "The Constitution", 1, 4,
            "c/c7/Constitution_of_the_United_States_-_DPLA_-_9ca804144bd5965e992ae3528bc3c6a3_%28page_1%29.jpg",
            "The Preamble, \"We the People,\" and Article I, Sections 1 to 7.",
        ),
        OriginalPage(
            "The Constitution", 2, 4, "a/a4/Constitution_of_the_United_States%2C_page_2.jpg",
            "Article I, Sections 8 to 10, and the beginning of Article II.",
        ),
        OriginalPage(
            "The Constitution", 3, 4, "5/53/Constitution_of_the_United_States%2C_page_3.jpg",
            "The rest of Article II, and Articles III to V.",
        ),
        OriginalPage(
            "The Constitution", 4, 4, "9/93/Constitution_of_the_United_States%2C_page_4.jpg",
            "Articles VI and VII, and the signatures of the thirty-nine delegates.",
        ),
        OriginalPage(
            "The Bill of Rights", 1, 1,
            "6/6c/Bill_of_Rights_-_DPLA_-_a65f6d0b6210c18d17c596908ebcf270_%28page_1%29.jpg",
            "Congress sent twelve amendments to the states in 1789. The third through the twelfth on this page were " +
                "ratified in 1791 as the First through Tenth Amendments.",
        ),
    )

    val billOfRightsIndex get() = pages.indexOfFirst { it.document == "The Bill of Rights" }
}

@Composable
private fun Page(url: String, description: String, modifier: Modifier, crop: Boolean = false) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = description,
        contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
        modifier = modifier,
        loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingBox() } },
        error = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Couldn't load the picture. Check your connection.", style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

/** Small pictures of each original page; tap one to look at it closely. */
@Composable
fun OriginalsCard(onOpen: (Int) -> Unit) {
    val uri = LocalUriHandler.current
    InfoCard(title = "The original documents") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Originals.pages.forEachIndexed { i, p ->
                Column(Modifier.width(96.dp).clickable { onOpen(i) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Page(
                        p.thumbnail, "Photograph of ${p.label}",
                        Modifier.width(96.dp).height(118.dp).clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        crop = true,
                    )
                    Text(
                        if (p.pages > 1) "Page ${p.page}" else "Bill of Rights",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Text(
            "Tap a page to see the handwriting up close. Pinch or double-tap to zoom.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("See them in person", style = MaterialTheme.typography.titleSmall)
        Text(
            "The signed Constitution, the Bill of Rights and the Declaration of Independence are on display in the " +
                "Rotunda for the Charters of Freedom at the National Archives Museum on Constitution Avenue in " +
                "Washington, D.C. Admission is free; it's open 10 a.m. to 5:30 p.m. every day except Thanksgiving " +
                "and Christmas. Reserving a timed ticket ahead is suggested but not required.",
            style = MaterialTheme.typography.bodyMedium,
        )
        LinkLine("Plan a visit (National Archives)", Originals.VISIT) { openSafely(uri, it) }
        LinkLine("Full-size scans online (National Archives)", Originals.FULL_SIZE) { openSafely(uri, it) }
    }
}

@Composable
private fun LinkLine(label: String, url: String, open: (String) -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable { open(url) }.padding(vertical = 4.dp),
    )
}

/**
 * One original page, filling the screen: pinch or double-tap to zoom and drag to move around; arrows move
 * between pages. "Read the text" goes to the typed transcription of the same part.
 */
@Composable
fun OriginalScreen(start: Int, onBack: () -> Unit, onReadText: (OriginalPage) -> Unit) {
    var index by remember { mutableIntStateOf(start.coerceIn(0, Originals.pages.lastIndex)) }
    val page = Originals.pages[index]
    var scale by remember(index) { mutableFloatStateOf(1f) }
    var offset by remember(index) { mutableStateOf(Offset.Zero) }
    val uri = LocalUriHandler.current
    Scaffold(topBar = { AppTopBar(page.label, onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(0.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .pointerInput(index) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 8f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .pointerInput(index) {
                        // Zooms in on the spot tapped, the way photos do; a second double-tap zooms back out.
                        detectTapGestures(onDoubleTap = { tap ->
                            if (scale > 1f) {
                                scale = 1f; offset = Offset.Zero
                            } else {
                                scale = 3f
                                offset = (Offset(size.width / 2f, size.height / 2f) - tap) * 2f
                            }
                        })
                    },
            ) {
                Page(
                    page.image, "Photograph of ${page.label}",
                    Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    },
                )
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(page.note, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { index-- }, enabled = index > 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous page")
                    }
                    Text("${index + 1} of ${Originals.pages.size}", style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { index++ }, enabled = index < Originals.pages.lastIndex) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next page")
                    }
                    Box(Modifier.weight(1f))
                    OutlinedButton(onClick = { onReadText(page) }) { Text("Read the text") }
                }
                SourceLine("National Archives, via Wikimedia Commons", Originals.FULL_SIZE, "full-size scans at the Archives")
            }
        }
    }
}

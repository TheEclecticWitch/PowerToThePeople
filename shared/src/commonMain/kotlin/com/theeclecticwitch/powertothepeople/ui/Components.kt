package com.theeclecticwitch.powertothepeople.ui

import com.theeclecticwitch.powertothepeople.ui.theme.OnSurfaceColors
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Reading width on big screens: a line of text past ~720dp is hard to follow. */
val ReadingWidth = 760.dp

/** Centres content and caps its width, so the desktop window reads like a page, not a banner. */
@Composable
fun ReadingColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = ReadingWidth).fillMaxWidth()) { content() }
    }
}

/** Opens Settings; provided once by the app so every main screen's top bar can offer it. */
val LocalOpenSettings = staticCompositionLocalOf<(() -> Unit)?> { null }

/** The gear in the upper right of the main screens. */
@Composable
fun SettingsButton() {
    val open = LocalOpenSettings.current ?: return
    IconButton(onClick = open) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
}

@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    OnSurfaceColors { AppTopBarBody(title, onBack, actions) }
}

@Composable
private fun AppTopBarBody(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = {
            actions()
            // Main screens (the ones without a back arrow) carry the Settings gear.
            if (onBack == null) SettingsButton()
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/** A titled card: the building block of the home screen and the detail pages. */
@Composable
fun InfoCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    OnSurfaceColors { InfoCardBody(title, modifier, onClick, content) }
}

@Composable
private fun InfoCardBody(
    title: String?,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val body: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) {
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            content()
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, content = body)
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, content = body)
    }
}

/**
 * "Source: U.S. Treasury - updated September 28, 2026", tappable to the source itself.
 * Every number in this app carries one of these: it is how the app stays neutral.
 */
@Composable
fun SourceLine(source: String, url: String?, detail: String? = null, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    val text = buildString {
        append("Source: ")
        append(source)
        if (!detail.isNullOrBlank()) append(" · ").append(detail)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textDecoration = if (url != null) TextDecoration.Underline else null,
        modifier = modifier.then(
            if (url != null) Modifier.clickable { openSafely(uri, url) } else Modifier,
        ),
    )
}

/** Opens a link, a phone number or an email, and quietly does nothing where that is impossible. */
fun openSafely(uri: androidx.compose.ui.platform.UriHandler, url: String) {
    try {
        uri.openUri(url)
    } catch (e: Exception) {
        // A desktop has no dialer, and a simulator has no phone; neither is worth a crash.
    }
}

@Composable
fun LoadingBox(message: String = "Loading…") {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(12.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        if (onRetry != null) OutlinedButton(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
fun CenteredMessage(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A round badge with someone's initials, for anyone without an official photograph. */
@Composable
fun InitialsBadge(name: String, sizeDp: Int = 56) {
    val initials = name.split(' ').filter { it.isNotBlank() && it.first().isLetter() }
        .let { parts -> (parts.firstOrNull()?.take(1) ?: "") + (parts.drop(1).lastOrNull()?.take(1) ?: "") }
        .uppercase()
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(sizeDp.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(initials, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/** A small rounded label, e.g. "Changed by Amendment XVII". */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * A short label in a fixed space (a tab, a tile) that shrinks to fit rather than wrapping mid-word or being
 * cut off, at any text size the reader picks. Never larger than [style]. Words are never split: on one line it
 * must fit whole; on more, each word must fit a line by itself.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    textAlign: TextAlign = TextAlign.Center,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val room = constraints.maxWidth
        val size = remember(text, style, room, maxLines) {
            fun fits(sized: TextStyle): Boolean {
                val words = if (maxLines == 1) listOf(text) else text.split(' ')
                if (words.any { measurer.measure(it, sized, softWrap = false, maxLines = 1).size.width > room }) return false
                return maxLines == 1 ||
                    !measurer.measure(text, sized, maxLines = maxLines, constraints = Constraints(maxWidth = room)).hasVisualOverflow
            }
            var sp = style.fontSize.value
            while (sp > 6f && !fits(style.copy(fontSize = sp.sp))) sp -= 0.5f
            sp.sp
        }
        Text(
            text,
            Modifier.fillMaxWidth(),
            color = color,
            style = style.copy(fontSize = size),
            maxLines = maxLines,
            softWrap = maxLines > 1,
            textAlign = textAlign,
        )
    }
}

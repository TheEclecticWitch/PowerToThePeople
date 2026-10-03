package com.theeclecticwitch.powertothepeople.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.ui.Modifier
import com.theeclecticwitch.powertothepeople.ui.FlagBackground
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.theeclecticwitch.powertothepeople.ui.TextSize
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.theeclecticwitch.powertothepeople.shared.resources.Res
import com.theeclecticwitch.powertothepeople.shared.resources.libre_caslon_display
import com.theeclecticwitch.powertothepeople.shared.resources.libre_caslon_text
import com.theeclecticwitch.powertothepeople.shared.resources.libre_caslon_text_italic
import org.jetbrains.compose.resources.Font

/*
 * Navy, parchment and gold: the colours of the founding documents rather than of either party.
 * Red and blue are deliberately kept out of anything that could read as a party colour, and a
 * party is only ever shown as a word.
 *
 * Every surface slot is set, not just `surface`. Material 3 draws cards, sheets and menus from the
 * surfaceContainer family, and any slot left unset falls back to baseline purple - Book of Shadows
 * shipped lilac cards on its sepia theme that way.
 */
private val Navy = Color(0xFF1F3A5F)
private val Gold = Color(0xFF8A6D2B)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E0EE),
    onPrimaryContainer = Color(0xFF0E2139),
    secondary = Gold,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0E2BD),
    onSecondaryContainer = Color(0xFF3A2C0B),
    tertiary = Color(0xFF4F5B4A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDE5D3),
    onTertiaryContainer = Color(0xFF1A2415),
    background = Color(0xFFF6F1E4),
    onBackground = Color(0xFF231E16),
    surface = Color(0xFFF6F1E4),
    onSurface = Color(0xFF231E16),
    surfaceVariant = Color(0xFFE9E1CD),
    onSurfaceVariant = Color(0xFF4D4639),
    surfaceTint = Navy,
    surfaceBright = Color(0xFFFBF8F0),
    surfaceDim = Color(0xFFE2DBC9),
    surfaceContainerLowest = Color(0xFFFFFDF8),
    surfaceContainerLow = Color(0xFFFBF7EC),
    surfaceContainer = Color(0xFFF3EDDD),
    surfaceContainerHigh = Color(0xFFEEE7D5),
    surfaceContainerHighest = Color(0xFFE8E0CC),
    inverseSurface = Color(0xFF383328),
    inverseOnSurface = Color(0xFFF8F1E2),
    inversePrimary = Color(0xFFA9C4E8),
    outline = Color(0xFF7F7666),
    outlineVariant = Color(0xFFD2C8B2),
    error = Color(0xFF9C3B2E),
    onError = Color.White,
    errorContainer = Color(0xFFF6DAD4),
    onErrorContainer = Color(0xFF3D0E07),
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C4E8),
    onPrimary = Color(0xFF0E2139),
    primaryContainer = Color(0xFF2B4A72),
    onPrimaryContainer = Color(0xFFD6E0EE),
    secondary = Color(0xFFDDBF74),
    onSecondary = Color(0xFF3A2C0B),
    secondaryContainer = Color(0xFF5A4715),
    onSecondaryContainer = Color(0xFFF0E2BD),
    tertiary = Color(0xFFBFCBB5),
    onTertiary = Color(0xFF1A2415),
    tertiaryContainer = Color(0xFF3A4535),
    onTertiaryContainer = Color(0xFFDDE5D3),
    background = Color(0xFF15181E),
    onBackground = Color(0xFFEAE3D3),
    surface = Color(0xFF15181E),
    onSurface = Color(0xFFEAE3D3),
    surfaceVariant = Color(0xFF3A3F48),
    onSurfaceVariant = Color(0xFFC9C2B2),
    surfaceTint = Color(0xFFA9C4E8),
    surfaceBright = Color(0xFF3A3E46),
    surfaceDim = Color(0xFF15181E),
    surfaceContainerLowest = Color(0xFF101318),
    surfaceContainerLow = Color(0xFF1C2027),
    surfaceContainer = Color(0xFF20242B),
    surfaceContainerHigh = Color(0xFF2A2E36),
    surfaceContainerHighest = Color(0xFF353941),
    inverseSurface = Color(0xFFEAE3D3),
    inverseOnSurface = Color(0xFF2C2A25),
    inversePrimary = Navy,
    outline = Color(0xFF928B7C),
    outlineVariant = Color(0xFF474B53),
    error = Color(0xFFF1B3A6),
    onError = Color(0xFF5E1A10),
    errorContainer = Color(0xFF7C2B1F),
    onErrorContainer = Color(0xFFF6DAD4),
    scrim = Color.Black,
)

/** Old paper and ink: easy on the eyes for long reading, as in a printed book. */
private val SepiaColors = lightColorScheme(
    primary = Color(0xFF5B3A1E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6D2B0),
    onPrimaryContainer = Color(0xFF2E1A08),
    secondary = Gold,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBDCB5),
    onSecondaryContainer = Color(0xFF3A2C0B),
    tertiary = Color(0xFF5E5A3E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE2DCC0),
    onTertiaryContainer = Color(0xFF1F1D0E),
    background = Color(0xFFF1E6CE),
    onBackground = Color(0xFF3A2D1C),
    surface = Color(0xFFF1E6CE),
    onSurface = Color(0xFF3A2D1C),
    surfaceVariant = Color(0xFFE4D5B6),
    onSurfaceVariant = Color(0xFF5C4A33),
    surfaceTint = Color(0xFF5B3A1E),
    surfaceBright = Color(0xFFF8F0DD),
    surfaceDim = Color(0xFFDDCFB2),
    surfaceContainerLowest = Color(0xFFFBF5E7),
    surfaceContainerLow = Color(0xFFF7EEDA),
    surfaceContainer = Color(0xFFEFE3C8),
    surfaceContainerHigh = Color(0xFFE9DCBF),
    surfaceContainerHighest = Color(0xFFE3D5B6),
    inverseSurface = Color(0xFF3B3024),
    inverseOnSurface = Color(0xFFF6EBD4),
    inversePrimary = Color(0xFFE2BE93),
    outline = Color(0xFF8A7759),
    outlineVariant = Color(0xFFD5C3A1),
    error = Color(0xFF9C3B2E),
    onError = Color.White,
    errorContainer = Color(0xFFF3D6C9),
    onErrorContainer = Color(0xFF3D0E07),
    scrim = Color.Black,
)

/**
 * The colors for cards, tiles and bars: the reader's chosen theme. With the flag on, the page itself (and any
 * text written straight onto it) takes the dark look so it reads against the deep flag, while everything drawn
 * on a card keeps the chosen theme. Wrap a surface's content in [OnSurfaceColors] to use these.
 */
val LocalSurfaceScheme = staticCompositionLocalOf<ColorScheme?> { null }

/** Draws [content] in the chosen theme's colors, for anything that sits on its own card, tile or bar. */
@Composable
fun OnSurfaceColors(content: @Composable () -> Unit) {
    val scheme = LocalSurfaceScheme.current
    if (scheme == null || scheme == MaterialTheme.colorScheme) content()
    else MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        // Text that names no color takes the theme's own, not the light lettering meant for the flag.
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface, content = content)
    }
}

/** The typefaces the app needs beyond Material's: Caslon for the founding documents. */
class AppFonts(val caslon: FontFamily, val caslonDisplay: FontFamily)

val LocalAppFonts = staticCompositionLocalOf { AppFonts(FontFamily.Serif, FontFamily.Serif) }

@Composable
fun PowerTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    // Libre Caslon, a revival of the typeface of the founding era's printers. SIL Open Font
    // Licence; the licence ships in files/.
    val caslon = FontFamily(
        Font(Res.font.libre_caslon_text, FontWeight.Normal),
        Font(Res.font.libre_caslon_text, FontWeight.SemiBold),
        Font(Res.font.libre_caslon_text, FontWeight.Bold),
        Font(Res.font.libre_caslon_text_italic, FontWeight.Normal, FontStyle.Italic),
    )
    val display = FontFamily(Font(Res.font.libre_caslon_display, FontWeight.Normal))
    val base = Typography()
    val typography = base.copy(
        displaySmall = base.displaySmall.copy(fontFamily = display),
        headlineLarge = base.headlineLarge.copy(fontFamily = display),
        headlineMedium = base.headlineMedium.copy(fontFamily = display),
        headlineSmall = base.headlineSmall.copy(fontFamily = caslon, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = caslon, fontWeight = FontWeight.SemiBold),
    )
    val scale by TextSize.flow.collectAsState()
    val density = LocalDensity.current
    val scheme = when (scale.theme) {
        "light" -> LightColors
        "dark" -> DarkColors
        "sepia" -> SepiaColors
        else -> if (dark) DarkColors else LightColors
    }
    val isDark = scheme == DarkColors
    // With the flag on, the flag is always shown deep and rich, never faded: the page takes the dark look over
    // it, cards keep the chosen theme and let a little of the flag through.
    val surfaces = if (scale.flag) scheme.copy(surfaceContainerLow = scheme.surfaceContainerLow.copy(alpha = if (isDark) 0.88f else 0.92f)) else scheme
    val colors = if (scale.flag) DarkColors.copy(background = Color.Transparent, surfaceContainerLow = surfaces.surfaceContainerLow) else scheme
    CompositionLocalProvider(
        LocalAppFonts provides AppFonts(caslon, display),
        LocalDensity provides Density(density.density, density.fontScale * scale.scale),
        LocalSurfaceScheme provides surfaces,
    ) {
        MaterialTheme(colorScheme = colors, typography = typography) {
            Box(Modifier.fillMaxSize().background(if (scale.flag) DarkColors.background else scheme.background)) {
                if (scale.flag) FlagBackground(wash = DarkColors.background.copy(alpha = 0.80f))
                content()
                // Behind the phone's clock and battery, the theme's own color, so those icons always read.
                if (scale.flag) {
                    Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(scheme.surface))
                }
            }
        }
    }
}

/**
 * Libre Caslon's italic joins "st" and "ct" into the long-s ligatures of 18th-century printing,
 * so "establish" reads as "eftablish". Handsome, but it trips modern readers, so it is turned off.
 */
const val CaslonFeatures = "liga 0, dlig 0, hlig 0"

/** The reading style for the founding text itself. */
@Composable
fun foundingTextStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(
    fontFamily = LocalAppFonts.current.caslon,
    fontSize = 18.sp,
    lineHeight = 29.sp,
    fontFeatureSettings = CaslonFeatures,
)

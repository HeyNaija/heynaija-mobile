package ng.heynaija.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

val LocalHeyNaijaColors = staticCompositionLocalOf { LightColors }

/** The theme's own tokens, for what Material's colour scheme has no name for. */
object HeyNaija {
    val colors: HeyNaijaColors
        @Composable @ReadOnlyComposable get() = LocalHeyNaijaColors.current
}

/** Radius follows role: small for things you press or type in, more for the surfaces that hold them. */
object Radii {
    val chip = RoundedCornerShape(6.dp)
    val control = RoundedCornerShape(8.dp)
    val card = RoundedCornerShape(12.dp)
}

/** Below this width a phone is small (an iPhone SE, a compact Android), and text steps down. */
private val COMPACT_WIDTH = 360.dp

/**
 * Light or dark with the system, like the web app's "system" theme. Text follows
 * the phone's own text size setting, as in other apps; on a small screen it is
 * one step (1/16) smaller, as Bluesky's smaller setting is.
 */
@Composable
fun HeyNaijaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    val density = LocalDensity.current
    val width = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val scaled = if (width in 1.dp..<COMPACT_WIDTH) Density(density.density, density.fontScale * 15f / 16f) else density
    CompositionLocalProvider(LocalHeyNaijaColors provides colors, LocalDensity provides scaled) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = heyNaijaTypography(),
            shapes = Shapes(extraSmall = Radii.chip, small = Radii.control, medium = Radii.card, large = Radii.card),
            content = content,
        )
    }
}

// Material components (buttons, progress, pull to refresh) read these, so they
// match the rest without per-call colours.
private fun HeyNaijaColors.toMaterial() = if (dark) {
    darkColorScheme(
        primary = accent, onPrimary = accentInk, primaryContainer = accentSoft, onPrimaryContainer = accentStrong,
        secondary = accentStrong, onSecondary = accentInk,
        background = bg, onBackground = ink,
        surface = surface, onSurface = ink, surfaceVariant = panel, onSurfaceVariant = muted,
        surfaceContainerLowest = surface, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = panel, surfaceContainerHighest = panel,
        outline = lineStrong, outlineVariant = line,
        error = danger, errorContainer = dangerSoft,
    )
} else {
    lightColorScheme(
        primary = accent, onPrimary = accentInk, primaryContainer = accentSoft, onPrimaryContainer = accentStrong,
        secondary = accentStrong, onSecondary = accentInk,
        background = bg, onBackground = ink,
        surface = surface, onSurface = ink, surfaceVariant = panel, onSurfaceVariant = muted,
        surfaceContainerLowest = surface, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = surface, surfaceContainerHighest = panel,
        outline = lineStrong, outlineVariant = line,
        error = danger, errorContainer = dangerSoft,
    )
}

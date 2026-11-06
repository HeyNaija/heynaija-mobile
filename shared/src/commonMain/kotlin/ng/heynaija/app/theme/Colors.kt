package ng.heynaija.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The web app's colour tokens, by the same names (apps/web/app/globals.css).
 * Change them there first; this file follows.
 */
@Immutable
data class HeyNaijaColors(
    val dark: Boolean,
    /** The ground cards sit on. */
    val bg: Color,
    val surface: Color,
    /** Quiet fill for inputs and inset blocks. */
    val panel: Color,
    val ink: Color,
    /** Safe for small text. */
    val muted: Color,
    /** Decorative only: icons, dividers, large numerals. */
    val faint: Color,
    val line: Color,
    val lineStrong: Color,
    val accent: Color,
    val accentStrong: Color,
    val accentSoft: Color,
    val accentInk: Color,
    /** Headings on sage panels, and avatar initials. */
    val brandDeep: Color,
    val danger: Color,
    val dangerSoft: Color,
    /** Avatar backgrounds, one per hash bucket. */
    val tones: List<Color>,
)

val LightColors = HeyNaijaColors(
    dark = false,
    bg = Color(0xFFF1F4F2),
    surface = Color(0xFFFFFFFF),
    panel = Color(0xFFF3F6F4),
    ink = Color(0xFF10212B),
    muted = Color(0xFF5C6B76),
    faint = Color(0xFF8A96A0),
    line = Color(0xFFE3E9E5),
    lineStrong = Color(0xFFD3DCD7),
    accent = Color(0xFF258153),
    accentStrong = Color(0xFF176541),
    accentSoft = Color(0xFFEAF4EE),
    accentInk = Color(0xFFFFFFFF),
    brandDeep = Color(0xFF123B30),
    danger = Color(0xFFB3261E),
    dangerSoft = Color(0xFFFBEDEC),
    tones = listOf(
        0xFFDCECE2, 0xFFE8EEE5, 0xFFEFE7D5, 0xFFEDDFD5, 0xFFE0E8ED, 0xFFE8E2ED, 0xFFEFDEE2, 0xFFDCEBE6,
    ).map { Color(it) },
)

val DarkColors = HeyNaijaColors(
    dark = true,
    bg = Color(0xFF0B1210),
    surface = Color(0xFF111A17),
    panel = Color(0xFF18231F),
    ink = Color(0xFFE6EFE9),
    muted = Color(0xFF9DAFA5),
    faint = Color(0xFF6C7F75),
    line = Color(0xFF212D28),
    lineStrong = Color(0xFF2F4038),
    accent = Color(0xFF3FAE79),
    accentStrong = Color(0xFF7DD9AA),
    accentSoft = Color(0xFF15281F),
    accentInk = Color(0xFF06150D),
    brandDeep = Color(0xFFD6ECDF),
    danger = Color(0xFFFF8F85),
    dangerSoft = Color(0xFF3A1C1A),
    tones = listOf(
        0xFF1D3327, 0xFF263326, 0xFF383021, 0xFF3A2A22, 0xFF22303A, 0xFF2E2A3B, 0xFF3A242B, 0xFF1D3330,
    ).map { Color(it) },
)

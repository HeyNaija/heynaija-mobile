package ng.heynaija.app.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ng.heynaija.app.resources.Res
import ng.heynaija.app.resources.plus_jakarta_sans_bold
import ng.heynaija.app.resources.plus_jakarta_sans_medium
import ng.heynaija.app.resources.plus_jakarta_sans_regular
import ng.heynaija.app.resources.plus_jakarta_sans_semibold
import org.jetbrains.compose.resources.Font

/** Plus Jakarta Sans throughout, as on the web. */
@Composable
fun jakartaSans(): FontFamily = FontFamily(
    Font(Res.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(Res.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(Res.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(Res.font.plus_jakarta_sans_bold, FontWeight.Bold),
)

/**
 * The type scale for phones. Post text is 15, as on the web and in the apps
 * people compare us with (X, Threads and Bluesky all use about 15), but at 1.4
 * line height, not the web's 1.6: on a narrow screen the looser leading made
 * every post look big. Titles are smaller than the web's for the same reason.
 *
 * - headlineMedium: page titles, 24 bold
 * - titleLarge: screen titles in the top bar, 17 bold
 * - titleMedium: names on profiles and cards, 15 bold
 * - titleSmall: a post's author, 14.5 semibold
 * - bodyLarge: post text, 15 at 1.4
 * - bodyMedium: secondary text, 13.5 at 1.45
 * - labelLarge: buttons, 14 semibold
 * - labelMedium: chips and labels, 12 semibold
 * - labelSmall: post meta (handle, time, counts), 13
 */
@Composable
fun heyNaijaTypography(): Typography {
    val sans = jakartaSans()
    val base = Typography().withFamily(sans)
    return base.copy(
        headlineMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.04).em),
        titleLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.02).em),
        titleMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.01).em),
        titleSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, lineHeight = 19.sp, letterSpacing = (-0.01).em),
        bodyLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
        bodyMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 19.5.sp),
        labelLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 17.sp),
    )
}

private fun Typography.withFamily(family: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
)

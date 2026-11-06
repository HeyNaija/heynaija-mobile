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
 * The web app's type scale (apps/web/DESIGN.md), on Material's slots: weight,
 * not size, does most of the work.
 *
 * - headlineMedium: page titles, 28 bold
 * - titleMedium: names and card titles, 15 bold
 * - bodyLarge: post text, 15 at 1.6
 * - bodyMedium: secondary text, 13 at 1.5
 * - labelMedium: chips and labels, 12 semibold
 * - labelSmall: post meta (handle, time, counts), 12
 */
@Composable
fun heyNaijaTypography(): Typography {
    val sans = jakartaSans()
    val base = Typography().withFamily(sans)
    return base.copy(
        headlineMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 35.sp, letterSpacing = (-0.045).em),
        titleMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = (-0.01).em),
        bodyLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.5.sp),
        labelLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 18.sp),
        labelSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    )
}

private fun Typography.withFamily(family: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
)

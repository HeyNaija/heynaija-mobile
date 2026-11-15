package ng.heynaija.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii

const val FOUNDER_LABEL = "Founder"
const val FOUNDER_TOOLTIP = "One of the people who founded HeyNaija. Their posts are their own views, not decisions by HeyNaija."

/**
 * The Founder badge, as on the web (apps/web/app/ui/founder-badge.tsx): one of the
 * people who founded HeyNaija. Show it only where the API says so (`founderBadge` on
 * a profile, `authorFounder` on a post, `founder` on a member). An award in the
 * colour of the name beside it, not a tick, so it is never read as the green identity
 * tick; it stands beside that tick rather than combining with it.
 *
 * Tapped, it says what it means until tapped away. [plain] is for rows that are
 * already one tap (a quoted post, a search result, a notification, a mention): just
 * the award, and a tap goes to the row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FounderBadge(modifier: Modifier = Modifier, size: Dp = 16.dp, plain: Boolean = false, color: Color = HeyNaija.colors.ink) {
    if (plain) {
        Icon(Icons.Award, FOUNDER_LABEL, modifier.size(size), tint = color)
        return
    }
    val colors = HeyNaija.colors
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below, 6.dp),
        tooltip = {
            PlainTooltip(
                Modifier.border(1.dp, colors.line, Radii.control),
                maxWidth = 260.dp,
                shape = Radii.control,
                contentColor = colors.ink,
                containerColor = colors.surface,
                shadowElevation = 6.dp,
            ) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("$FOUNDER_LABEL.") }
                        append(" $FOUNDER_TOOLTIP")
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium, lineHeight = 18.sp),
                )
            }
        },
        state = state,
    ) {
        // A 16dp mark still takes a fingertip: Compose widens a small target's touch area without growing the line.
        Icon(
            Icons.Award,
            FOUNDER_LABEL,
            modifier.size(size).clickable(onClickLabel = "What this means", role = Role.Button) { scope.launch { state.show() } },
            tint = color,
        )
    }
}

/** For the badge inside a line of text: `appendInlineContent(FOUNDER_INLINE)`, with this as the Text's inlineContent. */
const val FOUNDER_INLINE = "founder"

val founderInlineContent = mapOf(
    FOUNDER_INLINE to InlineTextContent(Placeholder(16.sp, 16.sp, PlaceholderVerticalAlign.Center)) {
        FounderBadge(Modifier.fillMaxSize(), plain = true)
    },
)

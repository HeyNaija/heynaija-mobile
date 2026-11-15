package ng.heynaija.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii

/** Beside an author's name on a post. */
const val AUTOMATED_LABEL = "Automated"
/** On the account's profile. */
const val AUTOMATED_ACCOUNT_LABEL = "Automated account"
const val AUTOMATED_TOOLTIP = "An automated HeyNaija account. Nobody signs in to it."

/**
 * The label on HeyNaija's own automated accounts (The Arbiter, Keke), as on the web
 * (`post-item.tsx` and the profile page): a chip in the sage wash, shown only where
 * the API says so (`authorAutomated` on a post, `automatedBadge` on a profile).
 * Like the Founder badge, a tap says what it means until tapped away.
 */
@Composable
fun AutomatedBadge(modifier: Modifier = Modifier, label: String = AUTOMATED_LABEL) {
    val colors = HeyNaija.colors
    ExplainedBadge(AnnotatedString(AUTOMATED_TOOLTIP)) { explain ->
        Text(
            label,
            modifier
                .clip(Radii.chip)
                .background(colors.accentSoft)
                .clickable(onClickLabel = "What this means", role = Role.Button, onClick = explain)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            color = colors.accentStrong,
            maxLines = 1,
            softWrap = false,
        )
    }
}

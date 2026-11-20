package ng.heynaija.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import ng.heynaija.app.LocalApp
import ng.heynaija.app.api.Servers
import ng.heynaija.app.nav.Route
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii

const val OFFICIAL_LABEL = "Official HeyNaija account"
const val OFFICIAL_AUTOMATED = "Automated: nobody signs in to it."
const val OFFICIAL_ABOUT = "About this account"
/** On the account's profile, under the handle. */
const val AUTOMATED_ACCOUNT_LABEL = "Automated account"

/**
 * Where "About this account" leads: Keke's own page on the site [web], or null for
 * any other account, whose profile (and bio) says what it does. As on the web
 * (`official-badge.tsx`).
 */
fun officialAboutPage(handle: String, web: String): String? =
    if (handle == "keke" || handle == "keke_hn") "$web/about/keke" else null

/**
 * The tick on HeyNaija's own automated accounts (Keke, The Arbiter), as on the web
 * (`apps/web/app/ui/official-badge.tsx`): the API's `authorAutomated` on a post,
 * `automatedBadge` on a profile. A shield in the accent green, not the identity
 * tick ([Icons.Verified]), which means a person's NIN was checked.
 *
 * Tapped, it opens a small card: an official account, automated, and a link to what
 * it does. A second tap, a tap elsewhere or back closes it; the tap that closes it
 * goes no further, so it does not open the post underneath. [onItsProfile] leaves the
 * profile link out on that profile itself, where it would lead to the same page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficialBadge(handle: String, modifier: Modifier = Modifier, size: Dp = 16.dp, onItsProfile: Boolean = false) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    var open by remember { mutableStateOf(false) }
    // The tap target is 24dp square however small the shield is drawn.
    Box(
        modifier
            .size(maxOf(size, 24.dp))
            .clip(Radii.chip)
            .clickable(onClickLabel = "What this means", role = Role.Button) { open = !open },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.ShieldCheck, OFFICIAL_LABEL, Modifier.size(size), tint = colors.accent)
        if (open) {
            // Focusable, so back closes it and a tap outside only closes it.
            Popup(
                popupPositionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below, 6.dp),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    Modifier.widthIn(min = 200.dp, max = 260.dp),
                    shape = Radii.control,
                    color = colors.surface,
                    contentColor = colors.ink,
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.dp, colors.line),
                ) {
                    val text = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium, lineHeight = 18.sp)
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(OFFICIAL_LABEL, style = text.copy(fontWeight = FontWeight.SemiBold))
                        Text(OFFICIAL_AUTOMATED, style = text)
                        val page = officialAboutPage(handle, Servers.current.web)
                        if (page != null || !onItsProfile) {
                            Text(
                                OFFICIAL_ABOUT,
                                Modifier
                                    .padding(top = 2.dp)
                                    .clickable(role = Role.Button) {
                                        open = false
                                        if (page != null) app.openLink(page) else app.navigator.push(Route.Profile(handle))
                                    }
                                    .padding(vertical = 4.dp),
                                style = text.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.accentStrong,
                            )
                        }
                    }
                }
            }
        }
    }
}

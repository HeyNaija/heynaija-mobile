package ng.heynaija.app.gists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ng.heynaija.app.LocalApp
import ng.heynaija.app.api.LiveGist
import ng.heynaija.app.nav.Route
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii
import ng.heynaija.app.ui.Icons
import ng.heynaija.app.ui.PrimaryButton

/** What the Gists button says to a screen reader: the member's own Gist first, then what else is live. */
fun gistsButtonLabel(inGist: Boolean, othersLive: Int) = when {
    inGist && othersLive > 0 -> "Gists. You are in one, and ${grouped(othersLive)} more ${if (othersLive == 1) "is" else "are"} live."
    inGist -> "Gists. You are in one."
    othersLive > 0 -> "Gists. ${grouped(othersLive)} live now."
    else -> "Gists"
}

/**
 * The Gists button on Home's bar, which says at a glance what is on: a green bubble
 * while the member is in a Gist (the selected tab's), and a red dot while others are
 * live (the Live now dot). A tap opens [GistsSheet]: the Gist they are in, the ones
 * live now, and starting one for a member who may host.
 *
 * [live] is Home's Live now list, asked for again every 30 seconds while Home is on screen.
 */
@Composable
fun GistsButton(live: List<LiveGist>, mayHost: Boolean) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val current by app.gists.current.collectAsStateWithLifecycle()
    val others = live.filter { it.id != current?.id }
    var open by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button) { open = true }
            .semantics { contentDescription = gistsButtonLabel(current != null, others.size) },
        contentAlignment = Alignment.Center,
    ) {
        if (current != null) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(colors.accentSoft).border(1.dp, colors.accent.copy(alpha = 0.18f), CircleShape))
        }
        Icon(Icons.Live, null, Modifier.size(22.dp), tint = if (current != null) colors.accentStrong else colors.muted)
        if (others.isNotEmpty()) {
            // A ring of the bar's own colour keeps the dot clear of the icon under it.
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = (-6).dp, y = 6.dp).size(12.dp).clip(CircleShape).background(colors.bg),
                contentAlignment = Alignment.Center,
            ) { LiveDot() }
        }
    }
    if (open) {
        GistsSheet(
            current = current,
            others = others,
            mayHost = mayHost,
            onStart = { starting = true },
            onDismiss = { open = false },
        )
    }
    if (starting) StartGistDialog(onDismiss = { starting = false })
}

/** The Gist the member is in, what else is live, and Start a Gist. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GistsSheet(current: GistRoom?, others: List<LiveGist>, mayHost: Boolean, onStart: () -> Unit, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Gists", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge, color = colors.ink)
            Text("Live audio rooms. Not recorded.", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            if (current != null) {
                SheetHeading("You are in")
                val state by current.state.collectAsStateWithLifecycle()
                val view = state.view
                GistRow(
                    title = view?.title ?: "Gist",
                    detail = view?.let { gistBarStatus(state, controlsFor(it, app.me?.mayTakeStage == true).mic) } ?: "Connecting…",
                    blink = true,
                ) {
                    onDismiss()
                    app.navigator.returnTo(Route.Gist(current.id))
                }
            }
            SheetHeading("Live now")
            if (others.isEmpty()) {
                Text(
                    if (current == null) "No Gists are live right now." else "No other Gists are live right now.",
                    Modifier.padding(vertical = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
            }
            for (gist in others) {
                GistRow(gist.title, liveMeta(gist)) {
                    onDismiss()
                    app.navigator.push(Route.Gist(gist.id))
                }
            }
            if (mayHost) {
                Spacer(Modifier.size(6.dp))
                PrimaryButton("Start a Gist", {
                    onDismiss()
                    onStart()
                }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SheetHeading(text: String) {
    Text(
        text,
        Modifier.padding(top = 10.dp).semantics { heading() },
        style = MaterialTheme.typography.labelLarge,
        color = HeyNaija.colors.muted,
    )
}

/** One Gist: the live dot, its title, and a line under it. */
@Composable
private fun GistRow(title: String, detail: String, blink: Boolean = false, onClick: () -> Unit) {
    val colors = HeyNaija.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radii.control)
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveDot(blink = blink)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

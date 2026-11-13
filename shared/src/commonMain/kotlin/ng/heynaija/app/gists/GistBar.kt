package ng.heynaija.app.gists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ng.heynaija.app.LocalApp
import ng.heynaija.app.nav.Route
import ng.heynaija.app.platform.rememberMicrophonePermission
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii
import ng.heynaija.app.ui.Icons

/** What the bar says under the Gist's title: the connection while it is being made, then the member's microphone. */
fun gistBarStatus(state: GistState, mayUseMic: Boolean): String = when {
    state.phase == Phase.Connecting -> "Connecting…"
    state.phase == Phase.Reconnecting -> "Reconnecting…"
    !mayUseMic -> "Listening"
    state.micOn -> "Your microphone is on"
    state.micBusy -> "Starting your microphone…"
    else -> "Your microphone is off"
}

/**
 * The Gist the member is in, over every screen but its own: they left the room's
 * screen to read something else, and the sound goes on. Its title and, in words,
 * what their microphone is doing; a tap leads back to the room, the microphone
 * can be switched from here, and Leave leaves.
 */
@Composable
fun GistBar(room: GistRoom, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val state by room.state.collectAsStateWithLifecycle()
    val view = state.view ?: return
    val mayUseMic = controlsFor(view).mic
    val askMic = rememberMicrophonePermission { granted -> if (granted) room.setMic(true) else room.micDenied() }
    // Why the microphone did not start is written in the room; away from it, say it here.
    var told by remember { mutableStateOf(state.micProblem) }
    LaunchedEffect(state.micProblem) {
        val problem = state.micProblem
        if (problem != null && problem != told) app.say(problem)
        told = problem
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(Radii.card)
            .background(colors.surface)
            .border(1.dp, colors.lineStrong, Radii.card)
            .clickable(onClickLabel = "Go back to the Gist", role = Role.Button) { app.navigator.returnTo(Route.Gist(room.id)) }
            .heightIn(min = 52.dp)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveDot(blink = true)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(view.title, style = MaterialTheme.typography.labelLarge, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(gistBarStatus(state, mayUseMic), style = MaterialTheme.typography.labelSmall, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (mayUseMic) {
            IconButton(
                onClick = { if (state.micOn) room.setMic(false) else askMic() },
                enabled = !state.micBusy && state.phase == Phase.Live,
            ) {
                Icon(
                    if (state.micOn) Icons.Mic else Icons.MicOff,
                    if (state.micOn) "Turn your microphone off" else "Turn your microphone on",
                    Modifier.size(22.dp),
                    tint = if (state.micOn) colors.accentStrong else colors.muted,
                )
            }
        }
        TextButton(onClick = {
            room.leave()
            app.say("You left the Gist.")
        }) { Text("Leave", style = MaterialTheme.typography.labelLarge, color = colors.danger) }
    }
}

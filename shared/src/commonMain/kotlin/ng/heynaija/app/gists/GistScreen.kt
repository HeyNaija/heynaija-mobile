package ng.heynaija.app.gists

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import ng.heynaija.app.LocalApp
import ng.heynaija.app.api.GistView
import ng.heynaija.app.api.Person
import ng.heynaija.app.nav.Route
import ng.heynaija.app.platform.KeepScreenOn
import ng.heynaija.app.platform.rememberMicrophonePermission
import ng.heynaija.app.platform.rememberShare
import ng.heynaija.app.posts.ListGutter
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii
import ng.heynaija.app.ui.Avatar
import ng.heynaija.app.ui.Card
import ng.heynaija.app.ui.Confirm
import ng.heynaija.app.ui.Empty
import ng.heynaija.app.ui.GistReplyContext
import ng.heynaija.app.ui.Icons
import ng.heynaija.app.ui.Loading
import ng.heynaija.app.ui.PostCard
import ng.heynaija.app.ui.Problem
import ng.heynaija.app.ui.SecondaryButton
import ng.heynaija.app.ui.TopBar
import ng.heynaija.app.ui.rememberNow

/** About 43 MB an hour with three speakers (ADR 0017 section 4a), said plainly. */
private const val DATA_HINT = "Listening uses about 43 MB an hour. It is more on mobile data, so Wi-Fi is kinder to your bundle."

/**
 * A Gist, after the web's gist-room.tsx: who hosts it and who is on stage, joining
 * to listen, asking to speak, the microphone, the host's controls, and the replies.
 */
@Composable
fun GistScreen(id: String) {
    val app = LocalApp.current
    val viewModel = viewModel(key = "gist-$id") { GistViewModel(app, id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = state.view
    // No polling while the app is in the background; a fresh look on coming back.
    LifecycleResumeEffect(viewModel) {
        viewModel.setVisible(true)
        onPauseOrDispose { viewModel.setVisible(false) }
    }
    if (state.polling || state.phase == Phase.Connecting) KeepScreenOn()
    val share = rememberShare()

    Column(Modifier.fillMaxSize()) {
        TopBar("Gist", onBack = { app.navigator.pop() }, actions = {
            // The Gist's page on the web, which opens in the app where it is installed. Only a member Gists are open
            // to gets past that page, so the link shows the room to nobody new.
            if (view != null) {
                IconButton(onClick = { share(app.gistLink(id)) }) {
                    Icon(Icons.Share, "Share this Gist", Modifier.size(22.dp), tint = HeyNaija.colors.muted)
                }
            }
            // Anyone in the room may report it, except its host (ADR 0017).
            if (state.polling && view?.viewer?.role != "host") {
                IconButton(onClick = { app.navigator.push(Route.Report("gistId" to id, "this Gist")) }) {
                    Icon(Icons.Flag, "Report this Gist", Modifier.size(22.dp), tint = HeyNaija.colors.muted)
                }
            }
        })
        when {
            view == null && state.phase == Phase.SignIn -> GistSignIn()
            view == null && state.phase == Phase.Missing ->
                Problem("This Gist is not available. It may have been taken down, or it is not open to you.", onRetry = null)
            view == null && state.phase == Phase.LoadFailed ->
                Problem("This Gist could not be loaded. Check your connection and try again.", viewModel::load)
            view == null -> Loading()
            else -> {
                Room(view, state, viewModel, Modifier.weight(1f))
                if (state.polling) Controls(view, state, viewModel)
            }
        }
    }
}

@Composable
private fun Room(view: GistView, state: GistState, viewModel: GistViewModel, modifier: Modifier) {
    val app = LocalApp.current
    val me = app.me
    val now = rememberNow()
    val moderator = view.viewer.role == "host" || view.viewer.role == "cohost"
    var listenersOpen by remember { mutableStateOf(false) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ListGutter, end = ListGutter, top = ListGutter, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(ListGutter),
    ) {
        item(key = "head") {
            Head(view, state, onListeners = {
                listenersOpen = true
                if (moderator) viewModel.loadListeners()
            })
        }
        item(key = "phase") { PhaseCard(view, state, viewModel) }
        if (state.polling) {
            state.problem?.let { problem -> item(key = "problem") { Notice(problem, danger = true) } }
            if (state.phase == Phase.Live && me != null && me.id in state.weak) {
                item(key = "weak") {
                    Notice("Your connection is weak, so the sound may break up${if (controlsFor(view).mic) " and others may not hear you clearly" else ""}. Move closer to your Wi-Fi, or switch between Wi-Fi and mobile data.")
                }
            }
            item(key = "stage") { Stage(view, state) }
            if (moderator) item(key = "host") { HostPanel(view, state, viewModel, onListeners = { listenersOpen = true; viewModel.loadListeners() }) }
            state.micProblem?.let { problem -> item(key = "mic-problem") { Notice(problem, danger = true) } }
        }
        // The gistReplies flag off stops new replies and leaves the ones there to read; with none, there is nothing to show.
        if (me?.has("gistReplies") == true || state.replies.count > 0) replies(view, state, viewModel, now)
    }

    if (listenersOpen) {
        if (moderator && state.polling) {
            ListenersSheet(view, state, viewModel, onDismiss = { listenersOpen = false; viewModel.closeListeners() })
        } else {
            // Everyone else gets the count: only the host and co-hosts see who is listening.
            AlertDialog(
                onDismissRequest = { listenersOpen = false },
                title = { Text("Listeners", style = MaterialTheme.typography.titleMedium) },
                text = {
                    Text(
                        "${listenerLabel(view.listenerCount)}, out of room for ${grouped(view.listenerCap)}. Only the host and co-hosts can see who is listening. Nobody can tell what you are listening to.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = { TextButton(onClick = { listenersOpen = false }) { Text("Close", color = HeyNaija.colors.ink) } },
                containerColor = HeyNaija.colors.surface,
                shape = Radii.card,
            )
        }
    }
}

/** Live or ended, how long, the title, who hosts it, and how many listen. */
@Composable
private fun Head(view: GistView, state: GistState, onListeners: () -> Unit) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val ended = view.status == "ended" || state.phase == Phase.Ended
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!ended) {
                    LiveDot(blink = true)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (ended) "Ended" else "Live", style = MaterialTheme.typography.labelLarge, color = colors.ink)
                if (!ended) Elapsed(view.startedAt)
                Spacer(Modifier.weight(1f))
                if (view.locked && !ended) Badge("Room locked", icon = Icons.Lock)
            }
            Spacer(Modifier.height(10.dp))
            Text(view.title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium, color = colors.ink)
            Spacer(Modifier.height(6.dp))
            FlowLine {
                Text("Hosted by ", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                Text(
                    view.host.name,
                    Modifier.clickable { app.navigator.push(Route.Profile(view.host.handle)) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.ink,
                )
                view.community?.let { community ->
                    Text(" in ", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                    Text(
                        community.name,
                        Modifier.clickable { app.navigator.push(Route.Community(community.slug)) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink,
                    )
                }
            }
            if (!ended) {
                Spacer(Modifier.height(12.dp))
                GistButton(
                    listenerLabel(view.listenerCount),
                    onClick = onListeners,
                    icon = Icons.People,
                    // A host or co-host's list opens from their controls, which appear once they have joined.
                    enabled = !(view.viewer.role == "host" || view.viewer.role == "cohost") || state.polling,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowLine(content: @Composable () -> Unit) {
    FlowRow(verticalArrangement = Arrangement.Center) { content() }
}

/** What to do next, for every phase but being in the room. */
@Composable
private fun PhaseCard(view: GistView, state: GistState, viewModel: GistViewModel) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val home: () -> Unit = { app.navigator.pop() }
    when (state.phase) {
        Phase.Prejoin -> Section("Ready to listen?") {
            Text(
                "Keep this screen open. If your phone locks or you switch to another app, the sound can stop. Audio only: no camera, and no recording.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink,
            )
            Text(DATA_HINT, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            Text("The sound starts when you tap Join. You only need your microphone if the host lets you speak.", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            GistButton("Join the Gist", viewModel::join, primary = true, modifier = Modifier.fillMaxWidth())
        }
        Phase.Connecting -> Card {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Connecting… keep this screen open.", style = MaterialTheme.typography.bodyMedium, color = colors.ink)
            }
        }
        Phase.Reconnecting -> Notice("Reconnecting… keep this screen open. The sound comes back on its own.")
        Phase.Live -> Text(
            "Keep this screen open. $DATA_HINT",
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
        )
        Phase.Failed -> Section(null) {
            Notice(state.problem ?: "Could not join the Gist.", danger = true)
            GistButton("Try again", viewModel::join, primary = true)
        }
        Phase.Full, Phase.Locked -> Section(if (state.phase == Phase.Full) "This Gist is full" else "This Gist is locked") {
            Text(
                if (state.phase == Phase.Full) "It has all the listeners it can take. Spots open up as people leave."
                else "The host has locked the room, so no new listeners can join.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GistButton("Try again", viewModel::join, primary = true)
                GistButton("Back", home)
            }
        }
        Phase.Elsewhere -> Section("This Gist is open on another device") {
            Text(
                "You can only listen in one place at a time. The sound is playing there. To move it here, tap the button.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
            GistButton("Use it here", viewModel::join, primary = true)
        }
        Phase.Gone, Phase.Missing -> Section("You are no longer in this Gist") {
            GistButton("Back", home, primary = true)
        }
        Phase.Ended -> Section("This Gist has ended") {
            Text(
                (if (view.peakListeners > 0) "Up to ${listenerLabel(view.peakListeners)} listened. " else "") + "Thanks for being part of it. Gists are not recorded.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
            GistButton("Back", home, primary = true)
        }
        Phase.Loading, Phase.LoadFailed, Phase.SignIn -> Unit
    }
}

// ---- the stage ----------------------------------------------------------------------

private data class OnStage(val person: Person, val label: String)

/**
 * Who is on stage. State is never colour alone, as on the web: a speaking tile has
 * a ring and the word "Speaking", a silent microphone the word "Muted", a weak
 * connection the words "Weak connection", and the role is a word.
 */
@Composable
private fun Stage(view: GistView, state: GistState) {
    val me = LocalApp.current.me
    val people = listOf(OnStage(view.host, "Host")) +
        view.speakers.map { OnStage(it.person, if (it.role == "cohost") "Co-host" else "Speaker") }
    Section("On stage") {
        for (row in people.chunked(3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in row) Tile(p, self = p.person.id == me?.id, state, Modifier.weight(1f))
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tile(p: OnStage, self: Boolean, state: GistState, modifier: Modifier) {
    val colors = HeyNaija.colors
    val live = p.person.id in state.mics
    val talking = live && p.person.id in state.speaking
    Column(modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.then(if (talking) Modifier.speakingRing(colors.accent) else Modifier).padding(5.dp)) {
            Avatar(p.person.handle, null, 64.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            p.person.name + if (self) " (you)" else "",
            style = MaterialTheme.typography.labelLarge,
            color = colors.ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Badge(p.label, tone = if (p.label == "Speaker") BadgeTone.Plain else BadgeTone.Role)
            if (!live) Badge("Muted", icon = Icons.MicOff, tone = BadgeTone.Outline)
            if (talking) Badge("Speaking", tone = BadgeTone.Speaking)
            if (p.person.id in state.weak) Badge("Weak connection", tone = BadgeTone.Weak)
        }
    }
}

// ---- host and co-host controls ---------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HostPanel(view: GistView, state: GistState, viewModel: GistViewModel, onListeners: () -> Unit) {
    val colors = HeyNaija.colors
    val role = view.viewer.role
    val controls = controlsFor(view)
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    Section(if (role == "host") "Host controls" else "Co-host controls") {
        Subhead("Requests to speak (${state.requests.size})")
        if (state.requests.isEmpty()) {
            Text(
                if (view.requestsOpen) "Nobody is asking to speak." else "Requests are switched off.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        }
        for (request in state.requests) {
            PersonRow(request.person, detail = request.person.handleIfNamed) {
                GistButton("Let speak", { viewModel.approve(request.person) }, primary = true, enabled = !state.busy, small = true)
            }
        }
        if (view.speakers.isNotEmpty()) {
            Subhead("On stage")
            for (speaker in view.speakers) {
                val p = speaker.person
                val isSpeaker = speaker.role == "speaker"
                PersonRow(p, detail = if (isSpeaker) "Speaker" else "Co-host")
                FlowRow(Modifier.padding(start = 46.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isSpeaker) GistButton("Mute", { viewModel.mute(p) }, enabled = !state.busy, small = true)
                    if (isSpeaker && controls.appointCohost) GistButton("Make co-host", { viewModel.makeCohost(p) }, enabled = !state.busy, small = true)
                    if (isSpeaker || role == "host") {
                        GistButton("To listeners", { viewModel.demote(p) }, enabled = !state.busy, small = true)
                        GistButton("Remove", { confirm = "Remove ${p.name} from this Gist?" to { viewModel.remove(p) } }, enabled = !state.busy, small = true, danger = true)
                    }
                }
            }
        }
        Subhead("Listeners")
        GistButton("See who is listening", onListeners, icon = Icons.People)
        if (controls.settings) {
            Subhead("Room")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GistButton(if (view.locked) "Unlock the room" else "Lock the room", { viewModel.setLocked(!view.locked) }, icon = Icons.Lock, enabled = !state.busy)
                GistButton(if (view.requestsOpen) "Turn requests off" else "Turn requests on", { viewModel.setRequestsOpen(!view.requestsOpen) }, enabled = !state.busy)
                GistButton("End the Gist", { confirm = "End this Gist for everyone?" to viewModel::end }, enabled = !state.busy, danger = true)
            }
        }
    }
    confirm?.let { (question, run) ->
        Confirm(
            title = question,
            text = if (question.startsWith("End")) "Everyone leaves the room, and it cannot be started again." else "They leave the room and cannot come back to this Gist.",
            action = if (question.startsWith("End")) "End it" else "Remove",
            onConfirm = { confirm = null; run() },
            onDismiss = { confirm = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListenersSheet(view: GistView, state: GistState, viewModel: GistViewModel, onDismiss: () -> Unit) {
    val colors = HeyNaija.colors
    var confirm by remember { mutableStateOf<Person?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Listeners", style = MaterialTheme.typography.titleLarge, color = colors.ink)
            Text("${listenerLabel(view.listenerCount)}. Only hosts and co-hosts can see who is listening.", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            val listeners = state.listeners
            when {
                listeners == null -> Text("Loading…", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                listeners.isEmpty() -> Text("Nobody is listening yet.", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                else -> for (p in listeners) {
                    PersonRow(p, detail = p.handleIfNamed) {
                        GistButton("Invite to speak", { viewModel.approve(p) }, enabled = !state.busy, small = true)
                    }
                    Row(Modifier.padding(start = 46.dp)) {
                        GistButton("Remove", { confirm = p }, enabled = !state.busy, small = true, danger = true)
                    }
                }
            }
        }
    }
    confirm?.let { p ->
        Confirm(
            title = "Remove ${p.name} from this Gist?",
            text = "They leave the room and cannot come back to this Gist.",
            action = "Remove",
            onConfirm = { confirm = null; viewModel.remove(p) },
            onDismiss = { confirm = null },
        )
    }
}

/** The handle under a display name; nothing when the name already is the handle. */
private val Person.handleIfNamed get() = if (displayName.isNullOrBlank()) null else "@$handle"

@Composable
private fun PersonRow(p: Person, detail: String?, action: (@Composable () -> Unit)? = null) {
    val colors = HeyNaija.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(p.handle, null, 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.labelLarge, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, style = MaterialTheme.typography.labelSmall, color = colors.muted, maxLines = 1)
        }
        action?.invoke()
    }
}

// ---- the member's own controls --------------------------------------------------------

/** The bar under the room: the microphone or a request to speak, and Leave. */
@Composable
private fun Controls(view: GistView, state: GistState, viewModel: GistViewModel) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val controls = controlsFor(view)
    val askMic = rememberMicrophonePermission { granted -> if (granted) viewModel.setMic(true) else viewModel.micDenied() }
    Column(Modifier.background(colors.surface)) {
        HorizontalDivider(color = colors.line)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                controls.mic -> GistButton(
                    // A tap waits for the room to allow the microphone, which can take a moment just after joining.
                    if (state.micOn) "Microphone on" else if (state.micBusy) "Starting…" else "Microphone off",
                    onClick = { if (state.micOn) viewModel.setMic(false) else askMic() },
                    icon = if (state.micOn) Icons.Mic else Icons.MicOff,
                    primary = state.micOn,
                    enabled = !state.micBusy && state.phase == Phase.Live,
                    modifier = Modifier.weight(1f),
                )
                controls.request -> GistButton("Request to speak", { viewModel.requestToSpeak(true) }, icon = Icons.Hand, primary = true, modifier = Modifier.weight(1f))
                controls.cancelRequest -> GistButton("Cancel request", { viewModel.requestToSpeak(false) }, modifier = Modifier.weight(1f))
                view.viewer.role == "listener" -> Text(
                    "The host is not taking requests to speak.",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
                else -> Spacer(Modifier.weight(1f))
            }
            GistButton("Leave", {
                viewModel.leave()
                app.navigator.pop()
            }, icon = Icons.Leave)
        }
    }
}

// ---- replies ------------------------------------------------------------------------

private fun LazyListScope.replies(view: GistView, state: GistState, viewModel: GistViewModel, now: kotlin.time.Instant) {
    val replies = state.replies
    item(key = "replies-head") { RepliesHead(view, state, viewModel) }
    if (!replies.loaded) {
        if (replies.failed) item(key = "replies-failed") {
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Replies could not be loaded.", style = MaterialTheme.typography.bodyMedium, color = HeyNaija.colors.muted)
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Try again", viewModel::loadReplies)
            }
        }
        return
    }
    if (replies.posts.isEmpty()) item(key = "replies-none") { Empty(if (replies.canReply) "No replies yet." else "No replies.") }
    val context = GistReplyContext(view.id, replies.viewerIsHost)
    items(replies.posts, key = { "reply-${it.id}" }) { post -> PostCard(post, now, compact = true, gist = context) }
    if (replies.cursor != null) {
        item(key = "replies-more") {
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                if (replies.loadingMore) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else SecondaryButton("More replies", viewModel::moreReplies)
            }
        }
    }
}

/** "Replies", who may write them (and the host's choice of that), and the way to write one. */
@Composable
private fun RepliesHead(view: GistView, state: GistState, viewModel: GistViewModel) {
    val app = LocalApp.current
    val me = app.me ?: return
    val colors = HeyNaija.colors
    val replies = state.replies
    val writable = me.has("gistReplies")
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (replies.count > 0) "Replies (${grouped(replies.count)})" else "Replies",
                Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
            )
            if (replies.viewerIsHost) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Dots, "Who can reply", Modifier.size(20.dp), tint = colors.muted) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        Text("Who can reply", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = colors.muted)
                        for ((key, label) in GIST_REPLY_AUDIENCES) {
                            DropdownMenuItem(
                                text = { Text(label) },
                                trailingIcon = if (replies.audience == key) ({ Icon(Icons.Check, null, Modifier.size(18.dp), tint = colors.accentStrong) }) else null,
                                onClick = { menu = false; viewModel.setReplyAudience(key) },
                            )
                        }
                    }
                }
            }
        }
        if (replies.loaded && replies.audience != "everyone") {
            Text(
                gistReplyAudienceNote(replies.audience, view.host.handle, replies.viewerIsHost),
                Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colors.muted,
            )
        }
        if (replies.loaded) {
            if (writable && replies.canReply) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(Radii.card)
                        .background(colors.surface)
                        .border(1.dp, colors.line, Radii.card)
                        .clickable { app.navigator.push(Route.Compose(gist = view.id)) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Avatar(me.handle, me.avatar, 32.dp)
                    Text("Reply to this Gist", style = MaterialTheme.typography.bodyLarge, color = colors.faint)
                }
            } else {
                Text(
                    if (writable) "The host has limited who can reply." else "Replies are paused for now.",
                    Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
            }
        }
    }
}

// ---- pieces -----------------------------------------------------------------------------

/** A card with an optional heading: one part of the room. */
@Composable
private fun Section(title: String?, content: @Composable () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = HeyNaija.colors.ink)
            content()
        }
    }
}

@Composable
private fun Subhead(text: String) {
    Text(text, Modifier.padding(top = 6.dp).semantics { heading() }, style = MaterialTheme.typography.labelLarge, color = HeyNaija.colors.muted)
}

/** A boxed note: sage for news, red-tinted for something that went wrong. */
@Composable
private fun Notice(text: String, danger: Boolean = false) {
    val colors = HeyNaija.colors
    Text(
        text,
        Modifier
            .fillMaxWidth()
            .clip(Radii.control)
            .background(if (danger) colors.dangerSoft else colors.accentSoft)
            .padding(12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = if (danger) colors.danger else colors.ink,
    )
}

private enum class BadgeTone { Plain, Role, Outline, Speaking, Weak }

@Composable
private fun Badge(text: String, icon: ImageVector? = null, tone: BadgeTone = BadgeTone.Plain) {
    val colors = HeyNaija.colors
    val (fill, ink) = when (tone) {
        BadgeTone.Plain, BadgeTone.Outline -> colors.panel to colors.muted
        BadgeTone.Role -> colors.accentSoft to colors.accentStrong
        BadgeTone.Speaking -> colors.accent to colors.accentInk
        BadgeTone.Weak -> colors.dangerSoft to colors.danger
    }
    Row(
        Modifier
            .clip(Radii.chip)
            .background(fill)
            .let { if (tone == BadgeTone.Outline) it.border(1.dp, colors.lineStrong, Radii.chip) else it }
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(12.dp), tint = ink)
            Spacer(Modifier.width(3.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
    }
}

/**
 * The ring round someone speaking, breathing. The animation is read while drawing,
 * so it redraws the ring without composing the room again every frame.
 */
@Composable
private fun Modifier.speakingRing(color: androidx.compose.ui.graphics.Color): Modifier {
    val pulse = rememberInfiniteTransition(label = "speaking").animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "ring",
    )
    return drawBehind {
        val width = 3.dp.toPx()
        drawCircle(color, radius = size.minDimension / 2 - width / 2, alpha = pulse.value, style = Stroke(width))
    }
}

/** The red dot by "Live": breathing slowly in the room ([blink]), still on Home, where it would only cost battery. */
@Composable
private fun LiveDot(blink: Boolean = false) {
    val dot = Modifier.size(8.dp).clip(CircleShape).background(HeyNaija.colors.danger)
    if (!blink) return Box(dot)
    val alpha = rememberInfiniteTransition(label = "live").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "dot",
    )
    Box(Modifier.graphicsLayer { this.alpha = alpha.value }.then(dot))
}

/** Time since the Gist started, ticking each second. */
@Composable
private fun Elapsed(startedAt: String?) {
    val now by produceState(Clock.System.now()) {
        while (true) {
            delay(1.seconds)
            value = Clock.System.now()
        }
    }
    val label = elapsedLabel(startedAt, now)
    if (label.isNotEmpty()) Text("  $label", style = MaterialTheme.typography.labelLarge, color = HeyNaija.colors.muted)
}

/** A button with an optional icon: green when [primary], else outlined; red text for [danger]. */
@Composable
private fun GistButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    danger: Boolean = false,
    small: Boolean = false,
) {
    val colors = HeyNaija.colors
    val padding = if (small) PaddingValues(horizontal = 12.dp, vertical = 4.dp) else PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, null, Modifier.size(if (small) 16.dp else 20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val height = Modifier.defaultMinSize(minHeight = if (small) 36.dp else 44.dp)
    if (primary) {
        Button(onClick, modifier.then(height), enabled = enabled, shape = Radii.control, contentPadding = padding, content = content)
    } else {
        OutlinedButton(
            onClick,
            modifier.then(height),
            enabled = enabled,
            shape = Radii.control,
            border = BorderStroke(1.dp, if (danger) colors.danger.copy(alpha = 0.35f) else colors.lineStrong),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (danger) colors.danger else colors.ink),
            contentPadding = padding,
            content = content,
        )
    }
}

// ---- on Home -------------------------------------------------------------------------

/**
 * Live now, on Home: the live Gists this member may join (`GET /v1/gists/live`,
 * which never lists one they would be refused). Shown only while one is live.
 */
@Composable
fun LiveNow(items: List<ng.heynaija.app.api.LiveGist>) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    Card {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot()
                Spacer(Modifier.width(8.dp))
                Text("Live now", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = colors.ink)
            }
            Spacer(Modifier.height(6.dp))
            for (gist in items) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(Radii.control)
                        .clickable { app.navigator.push(Route.Gist(gist.id)) }
                        .heightIn(min = 52.dp)
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(gist.title, style = MaterialTheme.typography.titleSmall, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(liveMeta(gist), style = MaterialTheme.typography.labelSmall, color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** "Start a Gist": a title and Go live. The member then joins it from the Gist's screen, as on the web. */
@Composable
fun StartGistDialog(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val viewModel = viewModel(key = "start-gist") { StartGistViewModel(app) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start a Gist", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "A live audio room. You talk, others listen, and you choose who else may speak. Not recorded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
                ng.heynaija.app.ui.Field(
                    state.title,
                    viewModel::setTitle,
                    "Gist title",
                    placeholder = "What is it about?",
                    error = state.error,
                    onDone = { viewModel.start { id -> onDismiss(); app.navigator.push(Route.Gist(id)) } },
                )
                state.hosting?.let { id ->
                    TextButton(onClick = { onDismiss(); app.navigator.push(Route.Gist(id)) }) {
                        Text("Return to your live Gist", color = colors.accentStrong)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.start { id -> onDismiss(); app.navigator.push(Route.Gist(id)) } },
                enabled = !state.busy,
            ) { Text(if (state.busy) "Starting…" else "Go live", color = colors.accentStrong) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.ink) } },
        containerColor = colors.surface,
        shape = Radii.card,
    )
}

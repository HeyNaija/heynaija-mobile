package ng.heynaija.app.games.chess

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ng.heynaija.app.AppState
import ng.heynaija.app.LocalApp
import ng.heynaija.app.api.ApiException
import ng.heynaija.app.api.CHESS_SPEEDS
import ng.heynaija.app.api.ChessAccount
import ng.heynaija.app.api.ChessCandidate
import ng.heynaija.app.api.ChessChallenge
import ng.heynaija.app.api.ChessOverview
import ng.heynaija.app.api.ChessRating
import ng.heynaija.app.api.LiveChessGame
import ng.heynaija.app.api.speedName
import ng.heynaija.app.games.Choice
import ng.heynaija.app.games.GAMES_OFF
import ng.heynaija.app.games.GameMark
import ng.heynaija.app.games.gamesGone
import ng.heynaija.app.games.notAvailable
import ng.heynaija.app.nav.Route
import ng.heynaija.app.posts.ListGutter
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.ui.Avatar
import ng.heynaija.app.ui.Card
import ng.heynaija.app.ui.Empty
import ng.heynaija.app.ui.Field
import ng.heynaija.app.ui.Icons
import ng.heynaija.app.ui.Loading
import ng.heynaija.app.ui.PrimaryButton
import ng.heynaija.app.ui.Problem
import ng.heynaija.app.ui.SecondaryButton
import ng.heynaija.app.ui.SectionTitle
import ng.heynaija.app.ui.TopBar
import ng.heynaija.app.ui.rememberNow
import ng.heynaija.app.ui.timeAgo

// Chess (ADR 0028 in the web repository): Lichess owns the game, its ratings and its
// fair play; HeyNaija owns the social side. The chess page as on the web: your Lichess
// account, games being played, challenges to you, challenging a member, and recent ones.

/** "Blitz 1520 · Rapid 1611?", a provisional rating marked as Lichess does. */
fun ratingsLine(ratings: Map<String, ChessRating>): String? =
    CHESS_SPEEDS.mapNotNull { (key, name) -> ratings[key]?.let { "$name ${it.rating}${if (it.provisional) "?" else ""}" } }
        .joinToString(" · ")
        .takeIf { it.isNotEmpty() }

private val COLOR_WORDS = mapOf("random" to "either colour", "white" to "white", "black" to "black")
private val STATUS_WORDS = mapOf(
    "pending" to "Waiting",
    "accepted" to "Accepted",
    "declined" to "Declined",
    "cancelled" to "Cancelled",
    "failed" to "Could not be made",
    "expired" to "Expired",
)

/** "Rated Rapid 10+5, you play white", as the web says it. */
fun challengeGame(c: ChessChallenge) = "${if (c.rated) "Rated" else "Casual"} ${speedName(c.speed)} ${c.time}, you play ${COLOR_WORDS[c.color] ?: c.color}"

data class ChessState(
    val overview: ChessOverview? = null,
    val live: List<LiveChessGame> = emptyList(),
    val failed: Boolean = false,
    val off: Boolean = false,
    /** A challenge or the account being changed: its buttons wait. */
    val busy: Set<String> = emptySet(),
)

class ChessViewModel(private val app: AppState) : ViewModel() {
    private val _state = MutableStateFlow(ChessState())
    val state: StateFlow<ChessState> = _state.asStateFlow()

    init {
        load()
        // Lichess was connected in the browser or iOS's sheet while this screen was open.
        viewModelScope.launch { app.chessChanged.collect { load() } }
    }

    fun load() {
        _state.update { it.copy(failed = false) }
        viewModelScope.launch {
            try {
                val overview = app.api.chess()
                val live = runCatching { app.api.liveChessGames() }.getOrDefault(_state.value.live)
                _state.update { it.copy(overview = overview, live = live) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (notAvailable(e)) {
                    _state.update { it.copy(off = true) }
                    app.gamesGone()
                } else {
                    _state.update { it.copy(failed = true) }
                }
            }
        }
    }

    /** Runs [work] with [key]'s buttons waiting, says what went wrong in the API's words, and loads the page again. */
    private fun act(key: String, failure: String, work: suspend () -> Unit) {
        if (key in _state.value.busy) return
        _state.update { it.copy(busy = it.busy + key) }
        viewModelScope.launch {
            try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                app.say(chessNotice(e.code) ?: e.message?.takeIf { e.status in 400..499 && e.status != 404 } ?: failure)
            } catch (e: Exception) {
                app.say("$failure Check your connection and try again.")
            } finally {
                _state.update { it.copy(busy = it.busy - key) }
                load()
            }
        }
    }

    fun connect(open: (String) -> Unit) = act("account", "Lichess could not be connected.") {
        val start = app.api.startLichessLink()
        if (start.returnsTo == "app") open(start.url)
        // A server from before the app could connect: only the website can finish it.
        else app.say("Connect Lichess on the HeyNaija website for now. This server cannot finish it in the app yet.")
    }

    fun disconnect() = act("account", "Lichess could not be disconnected.") {
        app.api.unlinkLichess()
        app.say("Your Lichess account is disconnected. HeyNaija no longer holds its key.")
    }

    fun refresh() = act("account", "Your ratings could not be refreshed.") {
        app.api.refreshLichessRatings()
        app.say("Ratings refreshed")
    }

    fun accept(c: ChessChallenge) = act(c.id, "The challenge could not be accepted.") {
        val accepted = app.api.acceptChallenge(c.id)
        app.say("Challenge accepted. Your game is ready.")
        accepted.gameId?.let { app.navigator.push(Route.ChessGame(it)) }
    }

    fun decline(c: ChessChallenge) = act(c.id, "The challenge could not be declined.") {
        app.api.declineChallenge(c.id)
        app.say("Challenge declined")
    }

    fun cancel(c: ChessChallenge) = act(c.id, "The challenge could not be cancelled.") {
        app.api.cancelChallenge(c.id)
        app.say("Challenge cancelled")
    }

    /** True when it was sent, so the form can clear. */
    fun send(handle: String, rated: Boolean, timeControl: String, color: String, openThread: Boolean, sent: () -> Unit) =
        act("send", "The challenge could not be sent.") {
            app.api.challenge(handle, rated, timeControl, color, openThread)
            app.say("Challenge sent to @$handle")
            sent()
        }

    private val _candidates = MutableStateFlow<List<ChessCandidate>>(emptyList())
    val candidates: StateFlow<List<ChessCandidate>> = _candidates.asStateFlow()
    private var searching: Job? = null

    /** Members who have connected Lichess, matching [query], a moment after typing stops. */
    fun search(query: String) {
        searching?.cancel()
        val q = query.trim().removePrefix("@")
        if (q.isEmpty()) {
            _candidates.value = emptyList()
            return
        }
        searching = viewModelScope.launch {
            delay(250)
            _candidates.value = try {
                app.api.chessCandidates(q)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}

@Composable
fun ChessScreen(opponent: String?) {
    val app = LocalApp.current
    val viewModel = viewModel(key = "chess") { ChessViewModel(app) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Back from lichess.org, or from the browser: challenges may have moved.
    val returns = remember { intArrayOf(0) }
    LifecycleResumeEffect(viewModel) {
        if (returns[0]++ > 0) viewModel.load()
        onPauseOrDispose { }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("Chess", onBack = { app.navigator.pop() })
        val overview = state.overview
        when {
            state.off -> Problem(GAMES_OFF, null)
            overview == null && state.failed -> Problem("Chess could not be loaded. Check your connection and try again.", viewModel::load)
            overview == null -> Loading()
            else -> ChessPage(overview, state, viewModel, opponent)
        }
    }
}

@Composable
private fun ChessPage(overview: ChessOverview, state: ChessState, viewModel: ChessViewModel, opponent: String?) {
    val colors = HeyNaija.colors
    val linked = overview.account != null
    LazyColumn(contentPadding = PaddingValues(start = ListGutter, end = ListGutter, top = ListGutter, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(ListGutter)) {
        item {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                GameMark("chess", 40)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Play members of HeyNaija on Lichess, the free chess site. Your games count toward your Game Score.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
            }
        }
        // Not connected yet: that comes first, as nothing else works without it.
        if (!linked) item { AccountCard(overview, state, viewModel) }
        // Opened from a profile's Challenge: the form comes first, with the member in it.
        val formFirst = linked && opponent != null
        if (formFirst) {
            item { SectionTitle("Challenge a member") }
            item(key = "form") { ChallengeForm(overview, state, viewModel, opponent) }
        }

        if (state.live.isNotEmpty()) {
            item { SectionTitle("Live now") }
            state.live.forEach { game -> item(key = "live-${game.id}") { LiveGameRow(game) } }
        }

        item { SectionTitle("Challenges to you") }
        if (overview.incoming.isEmpty()) {
            item { Card { Empty("No challenges waiting. When a member challenges you, it shows here.") } }
        } else {
            overview.incoming.forEach { c -> item(key = "in-${c.id}") { ChallengeCard(c, state.busy, viewModel) } }
        }

        if (linked) {
            if (!formFirst) {
                item { SectionTitle("Challenge a member") }
                item(key = "form") { ChallengeForm(overview, state, viewModel, opponent) }
            }
            if (overview.outgoing.isNotEmpty()) {
                item { SectionTitle("Waiting for an answer") }
                overview.outgoing.forEach { c -> item(key = "out-${c.id}") { ChallengeCard(c, state.busy, viewModel) } }
            }
        }

        if (overview.recent.isNotEmpty()) {
            item { SectionTitle("Recent challenges") }
            overview.recent.forEach { c -> item(key = "recent-${c.id}") { ChallengeCard(c, state.busy, viewModel) } }
        }

        if (linked) {
            item { SectionTitle("Your Lichess account") }
            item { AccountCard(overview, state, viewModel) }
        }
    }
}

@Composable
private fun AccountCard(overview: ChessOverview, state: ChessState, viewModel: ChessViewModel) {
    val colors = HeyNaija.colors
    val uri = LocalUriHandler.current
    val lichess = rememberLichessLink()
    val account = overview.account
    val busy = "account" in state.busy
    var confirming by remember { mutableStateOf(false) }
    Card {
        Column(Modifier.padding(16.dp)) {
            if (account == null) {
                Text("Connect your Lichess account", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                Text(
                    "Games are played on Lichess. Connect your account to challenge members and play here. HeyNaija keeps the key Lichess gives it, " +
                        "sealed, and never shows it to anyone; disconnect to take it back.",
                    Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
                Spacer(Modifier.height(12.dp))
                if (overview.linkingAvailable) {
                    PrimaryButton("Connect Lichess", { viewModel.connect(lichess::open) }, busy = busy)
                } else {
                    Text("Connecting Lichess is not available here yet.", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(account.username, style = MaterialTheme.typography.titleMedium, color = colors.ink)
                        Text("on Lichess", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    }
                    if (account.url.isNotBlank()) {
                        TextButton(onClick = { runCatching { uri.openUri(account.url) } }) {
                            Text("Profile", style = MaterialTheme.typography.labelLarge, color = colors.accentStrong)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.ExternalLink, null, Modifier.size(14.dp), tint = colors.accentStrong)
                        }
                    }
                }
                RatingsGrid(account)
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Refresh ratings", viewModel::refresh, enabled = !busy)
                    SecondaryButton("Disconnect", { confirming = true }, enabled = !busy, danger = true)
                }
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Disconnect Lichess?") },
            text = { Text("HeyNaija gives Lichess back its key. You can connect again at any time; challenges waiting for you stay until they run out.") },
            confirmButton = { TextButton(onClick = { confirming = false; viewModel.disconnect() }) { Text("Disconnect", color = colors.danger) } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Keep it") } },
        )
    }
}

@Composable
private fun RatingsGrid(account: ChessAccount) {
    val colors = HeyNaija.colors
    FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((key, name) in CHESS_SPEEDS) {
            val r = account.ratings[key]
            Column {
                Text(name, style = MaterialTheme.typography.labelSmall, color = colors.muted)
                Text(
                    r?.let { "${it.rating}${if (it.provisional) "?" else ""}" } ?: "–",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun LiveGameRow(game: LiveChessGame) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    Card(onClick = { app.navigator.push(Route.ChessGame(game.id)) }) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "${game.white.displayName ?: "@${game.white.handle}"} vs ${game.black.displayName ?: "@${game.black.handle}"}",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${if (game.rated) "Rated" else "Casual"} ${speedName(game.speed)} ${game.time} · ${game.moveCount} ${if (game.moveCount == 1) "move" else "moves"}",
                Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
        }
    }
}

@Composable
private fun ChallengeCard(c: ChessChallenge, busy: Set<String>, viewModel: ChessViewModel) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val now = rememberNow()
    val waiting = c.id in busy
    val name = c.opponent.displayName?.takeIf { it.isNotBlank() } ?: "@${c.opponent.handle}"
    Card(onClick = c.gameId?.let { { app.navigator.push(Route.ChessGame(it)) } }) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(c.opponent.handle, c.opponent.avatar, size = 36.dp, modifier = Modifier.clickable { app.navigator.push(Route.Profile(c.opponent.handle)) })
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (c.sent) "You challenged $name" else "$name challenged you",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val at = runCatching { Instant.parse(c.createdAt) }.getOrNull()
                    Text(
                        listOfNotNull(STATUS_WORDS[c.status], at?.let { timeAgo(it, now) }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                    )
                }
            }
            Text(challengeGame(c), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = colors.ink)
            if (c.openThread && c.status == "pending") {
                Text("This game will get a public thread in Chess Arena.", Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
            when {
                c.status == "pending" && !c.sent -> Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Accept", { viewModel.accept(c) }, busy = waiting)
                    SecondaryButton("Decline", { viewModel.decline(c) }, enabled = !waiting)
                }
                c.status == "pending" && c.sent -> Row(Modifier.padding(top = 10.dp)) {
                    SecondaryButton("Cancel challenge", { viewModel.cancel(c) }, enabled = !waiting)
                }
                c.gameId != null -> Text("Open the game", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge, color = colors.accentStrong)
            }
        }
    }
}

@Composable
private fun ChallengeForm(overview: ChessOverview, state: ChessState, viewModel: ChessViewModel, opponent: String?) {
    val colors = HeyNaija.colors
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf(opponent?.let { "@$it" } ?: "") }
    var chosen by remember { mutableStateOf(opponent) }
    var control by remember { mutableStateOf(overview.timeControls.firstOrNull { it.id == "10+5" }?.id ?: overview.timeControls.firstOrNull()?.id) }
    var rated by remember { mutableStateOf(true) }
    var color by remember { mutableStateOf("random") }
    var openThread by remember { mutableStateOf(true) }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Field(
                    value = query,
                    onValueChange = {
                        query = it
                        chosen = null
                        viewModel.search(it)
                    },
                    label = "Who",
                    placeholder = "@handle",
                )
                Text("Only members who have connected a Lichess account can be challenged.", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                if (chosen == null && candidates.isNotEmpty()) {
                    Column(Modifier.padding(top = 6.dp)) {
                        candidates.take(5).forEach { c ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    chosen = c.handle
                                    query = "@${c.handle}"
                                    viewModel.search("")
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(c.handle, c.avatar, size = 32.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.displayName?.takeIf { it.isNotBlank() } ?: "@${c.handle}", style = MaterialTheme.typography.bodyLarge, color = colors.ink, maxLines = 1)
                                    Text("@${c.handle}" + if (c.lichess.isNotBlank()) " · ${c.lichess} on Lichess" else "", style = MaterialTheme.typography.bodySmall, color = colors.muted, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Time", style = MaterialTheme.typography.labelLarge, color = colors.ink)
                for ((speed, name) in CHESS_SPEEDS) {
                    val controls = overview.timeControls.filter { it.speed == speed }
                    if (controls.isEmpty()) continue
                    Text(name, style = MaterialTheme.typography.labelSmall, color = colors.muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        controls.forEach { tc -> Choice(tc.label, control == tc.id, { control = tc.id }) }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Game", style = MaterialTheme.typography.labelLarge, color = colors.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("Rated", rated, { rated = true })
                    Choice("Casual", !rated, { rated = false })
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("You play", style = MaterialTheme.typography.labelLarge, color = colors.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("Either colour", color == "random", { color = "random" })
                    Choice("White", color == "white", { color = "white" })
                    Choice("Black", color == "black", { color = "black" })
                }
            }
            HorizontalDivider(color = colors.line)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Open a public thread in Chess Arena when the game starts, so members can watch and talk about it",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink,
                )
                Spacer(Modifier.width(12.dp))
                Switch(openThread, { openThread = it }, colors = SwitchDefaults.colors(checkedTrackColor = colors.accent))
            }
            val handle = chosen ?: query.trim().removePrefix("@").takeIf { Regex("^[A-Za-z0-9_]{1,20}$").matches(it) }
            PrimaryButton(
                "Send challenge",
                {
                    val tc = control ?: return@PrimaryButton
                    val to = handle ?: return@PrimaryButton
                    viewModel.send(to, rated, tc, color, openThread) {
                        query = ""
                        chosen = null
                    }
                },
                Modifier.fillMaxWidth(),
                enabled = handle != null && control != null,
                busy = "send" in state.busy,
            )
        }
    }
}

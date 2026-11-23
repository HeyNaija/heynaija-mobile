package ng.heynaija.app.games.chess

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import ng.heynaija.app.theme.Radii
import ng.heynaija.app.ui.SecondaryButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.withTimeoutOrNull
import ng.heynaija.app.AppState
import ng.heynaija.app.LocalApp
import ng.heynaija.app.api.ApiException
import ng.heynaija.app.api.ChessGame
import ng.heynaija.app.api.ChessPlayer
import ng.heynaija.app.api.speedName
import ng.heynaija.app.games.gamesGone
import ng.heynaija.app.nav.Route
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.ui.Avatar
import ng.heynaija.app.ui.Card
import ng.heynaija.app.ui.Icons
import ng.heynaija.app.ui.Loading
import ng.heynaija.app.ui.MenuRow
import ng.heynaija.app.ui.PrimaryButton
import ng.heynaija.app.ui.Problem
import ng.heynaija.app.ui.TopBar

private val HOW = mapOf("mate" to "by checkmate", "resign" to "by resignation", "outoftime" to "on time", "timeout" to "as the other player left")

private fun nameOf(p: ChessPlayer) = p.displayName?.takeIf { it.isNotBlank() } ?: "@${p.handle}"

/** How a finished game ended, in the web's words (chess-game.tsx). */
fun resultText(g: ChessGame): String = when {
    g.status == "aborted" -> "Game aborted"
    g.status == "noStart" -> "Game cancelled: the first move was not made in time"
    g.winner == "white" -> "${nameOf(g.white)} won ${HOW[g.status] ?: ""}".trim()
    g.winner == "black" -> "${nameOf(g.black)} won ${HOW[g.status] ?: ""}".trim()
    g.status == "stalemate" -> "Drawn by stalemate"
    else -> "Drawn"
}

/** m:ss, with tenths under ten seconds; h:mm:ss for long clocks. As the web. */
fun formatClock(ms: Long): String {
    val total = ms.coerceAtLeast(0)
    if (total < 10_000) {
        val tenths = total / 100
        return "0:0${tenths / 10}.${tenths % 10}"
    }
    val s = total / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = (s % 60).toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$sec" else "$m:$sec"
}

/** Whose turn it is from the number of moves: white moves first. */
fun sideToMove(moves: List<String>) = if (moves.size % 2 == 0) "white" else "black"


/** What the board shows and what the player is doing. */
data class ChessGameState(
    val game: ChessGame? = null,
    val failed: Boolean = false,
    val gone: Boolean = false,
    /** A move sent to Lichess and not back yet: shown on the board meanwhile. */
    val pending: String? = null,
    /** How many moves the game had when [pending] was made. */
    val pendingAt: Int = 0,
    /** A draw, resignation or abort on its way. */
    val busy: Boolean = false,
)

/**
 * One game, kept up to date by asking the API for the next change: it answers at
 * once if the game moved on since the version the screen has, else waits up to 25
 * seconds. A reply that is late was lost on the way, and the screen asks again
 * (ChessWait.kt). Only while the screen is in front, and never after the game is over.
 * The player's own moves show at once and go back if Lichess refuses them.
 */
class ChessGameViewModel(private val app: AppState, private val id: String) : ViewModel() {
    private val _state = MutableStateFlow(ChessGameState())
    val state: StateFlow<ChessGameState> = _state.asStateFlow()
    private var following: Job? = null

    fun start() {
        if (following?.isActive == true) return
        following = viewModelScope.launch {
            var failures = 0
            while (true) {
                val have = _state.value.game
                if (have?.finished == true) return@launch
                try {
                    // A reply that is late was lost on the way (patchy data drops them): give up on it and ask again (ChessWait.kt).
                    val game = withTimeoutOrNull(waitLimit(have)) { app.api.chessGame(id, after = have?.version ?: -1) } ?: continue
                    _state.update {
                        // The move sent is in the game now (or the game ended): it no longer needs showing on its own.
                        val settled = it.pending != null && (game.moves.size > it.pendingAt || game.finished)
                        it.copy(game = game, failed = false, pending = if (settled) null else it.pending)
                    }
                    failures = 0
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ApiException) {
                    if (e.status == 404) {
                        // Gone, never there, or Games switched off for this member.
                        _state.value = ChessGameState(gone = true)
                        app.gamesGone()
                        return@launch
                    }
                    _state.update { it.copy(failed = it.game == null) }
                    delay(retryPause(have, ++failures))
                } catch (e: Exception) {
                    _state.update { it.copy(failed = it.game == null) }
                    delay(retryPause(have, ++failures))
                }
            }
        }
    }

    fun stop() {
        following?.cancel()
        following = null
    }

    fun retry() {
        _state.update { it.copy(failed = false) }
        stop()
        start()
    }

    /** Lichess's refusal in words a player can act on. */
    private fun refusal(e: Exception): String = when {
        e is ApiException && e.status == 429 -> "That was a lot of moves at once. Wait a moment."
        // The server cannot use Lichess at all (no token key): nothing a player can do about it.
        e is ApiException && e.code == "not_available" -> "Games cannot be played from HeyNaija here yet. Play this one on lichess.org."
        e is ApiException -> chessNotice(e.code) ?: e.message?.takeIf { e.status in 400..499 } ?: "That did not go through. Try again."
        else -> "That did not go through. Check your connection and try again."
    }

    fun move(move: Move) {
        val s = _state.value
        val game = s.game ?: return
        if (s.pending != null || game.finished) return
        _state.update { it.copy(pending = move.uci, pendingAt = game.moves.size) }
        viewModelScope.launch {
            try {
                app.api.chessMove(id, move.uci)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Lichess said no, or it never got there: the piece goes back.
                _state.update { it.copy(pending = null) }
                app.say(refusal(e))
                return@launch
            }
            expectBack(game.version)
        }
    }

    private fun act(work: suspend () -> Unit) {
        if (_state.value.busy) return
        val before = _state.value.game?.version
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                app.say(refusal(e))
                return@launch
            } finally {
                _state.update { it.copy(busy = false) }
            }
            if (before != null) expectBack(before)
        }
    }

    /**
     * Lichess took the player's move or decision and sends it back within a moment. If no
     * reply has brought anything newer than [before] by [LATE_MS] later, that reply was lost
     * on the way (ChessWait.kt): give up on the request and ask again now.
     */
    private suspend fun expectBack(before: Long) {
        delay(LATE_MS)
        if (_state.value.game?.version != before || following?.isActive != true) return
        following?.cancel()
        start()
    }

    fun draw(answer: String) = act { app.api.chessDraw(id, answer) }
    fun resign() = act { app.api.chessResign(id) }
    fun abort() = act { app.api.chessAbort(id) }
}

@Composable
fun ChessGameScreen(id: String) {
    val app = LocalApp.current
    val viewModel = viewModel(key = "chess-game-$id") { ChessGameViewModel(app, id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.start()
        onPauseOrDispose { viewModel.stop() }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("Chess game", onBack = { app.navigator.pop() })
        val game = state.game
        when {
            state.gone -> Problem("This game is not here. It may have been removed, or you may not be able to see it.", null)
            game == null && state.failed -> Problem("The game could not be loaded. Check your connection and try again.", viewModel::retry)
            game == null -> Loading()
            else -> GamePage(game, state, viewModel)
        }
    }
}

@Composable
private fun GamePage(game: ChessGame, state: ChessGameState, viewModel: ChessGameViewModel) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val uri = LocalUriHandler.current
    val you = Side.of(game.you)
    var flipped by remember { mutableStateOf(false) }
    // The player's colour at the bottom; white for a spectator, who may turn the board.
    val bottom = (you ?: Side.White).let { if (flipped) it.other else it }
    val shown = state.pending?.let { game.moves + it } ?: game.moves
    val replay = remember(shown) { Replay(shown) }
    // Which move the board shows: the newest unless the viewer stepped back.
    var ply by remember { mutableStateOf<Int?>(null) }
    val showing = (ply ?: replay.moves.size).coerceIn(0, replay.moves.size)
    val live = showing == replay.moves.size
    val position = replay.positions[showing]
    val mover = you.takeIf { live && !game.finished && state.pending == null }
    var resigning by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text(
            "${if (game.rated) "Rated" else "Casual"} ${speedName(game.speed)} ${game.time}" +
                (game.days?.let { " · correspondence: $it ${if (it == 1) "day" else "days"} a move" } ?: ""),
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = colors.muted,
        )
        PlayerRow(game, bottom.other.key, turn = !game.finished && replay.current.turn == bottom.other)
        ChessBoard(
            position = position,
            orientation = bottom,
            lastMove = replay.moves.getOrNull(showing - 1),
            mover = mover,
            onMove = viewModel::move,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        PlayerRow(game, bottom.key, turn = !game.finished && replay.current.turn == bottom)

        Text(
            statusLine(game, replay, you, state.pending != null),
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.titleMedium,
            color = colors.ink,
        )

        // Stepping through the game: the first, previous, next and newest position.
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            StepButton(Icons.First, "The start", enabled = showing > 0) { ply = 0 }
            StepButton(Icons.Back, "The move before", enabled = showing > 0) { ply = showing - 1 }
            StepButton(Icons.Next, "The move after", enabled = !live) { ply = (showing + 1).takeIf { it < replay.moves.size } }
            StepButton(Icons.Last, "The newest move", enabled = !live) { ply = null }
            Spacer(Modifier.weight(1f))
            if (you == null || game.finished) {
                TextButton(onClick = { flipped = !flipped }) { Text("Turn the board", color = colors.accentStrong) }
            }
        }
        if (!live) {
            Text(
                "Showing move $showing of ${replay.moves.size}. " + if (mover == null && you != null && !game.finished) "Go to the newest move to play." else "",
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
        }

        if (you != null && !game.finished) {
            val drawOffer = Side.of(game.drawOffer)
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    drawOffer != null && drawOffer != you -> {
                        PrimaryButton("Accept draw", { viewModel.draw("accept") }, busy = state.busy)
                        SecondaryButton("Decline draw", { viewModel.draw("decline") }, enabled = !state.busy)
                    }
                    else -> SecondaryButton(
                        if (drawOffer == you) "Draw offered" else "Offer a draw",
                        { viewModel.draw("offer") },
                        enabled = !state.busy && drawOffer == null,
                    )
                }
                if (game.moves.size < 2) {
                    SecondaryButton("Abort", { viewModel.abort() }, enabled = !state.busy)
                } else {
                    SecondaryButton("Resign", { resigning = true }, enabled = !state.busy, danger = true)
                }
            }
        }

        if (replay.san.isNotEmpty()) {
            Text("Moves", Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.labelMedium, color = colors.muted)
            MoveList(replay.san, showing, onShow = { ply = if (it == replay.moves.size) null else it }, modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (replay.moves.size < shown.size) {
            Text(
                "This game has a move the app cannot show. It is played on Lichess as usual; open it there.",
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.danger,
            )
        }

        if (you == null && !game.finished && game.threadPostId != null) {
            Text(
                "Spectators’ replies in the thread are hidden from the players until the game ends. This is a courtesy, not anti-cheat: anyone can watch on lichess.org.",
                Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
            Card {
                Column {
                    if (game.threadPostId != null) {
                        MenuRow(
                            if (game.finished) "Talk about it in Chess Arena" else "The thread in Chess Arena",
                            icon = Icons.Bubble,
                            detail = if (you != null && !game.finished) "You see the replies once the game is over." else null,
                        ) { app.navigator.push(Route.Thread(game.resultPostId ?: game.threadPostId)) }
                        HorizontalDivider(color = colors.line)
                    }
                    MenuRow("On lichess.org", icon = Icons.ExternalLink) { runCatching { uri.openUri(game.lichessUrl) } }
                }
            }
        }
    }

    if (resigning) {
        AlertDialog(
            onDismissRequest = { resigning = false },
            title = { Text("Resign this game?") },
            text = { Text("It counts as a loss.") },
            confirmButton = { TextButton(onClick = { resigning = false; viewModel.resign() }) { Text("Resign", color = colors.danger) } },
            dismissButton = { TextButton(onClick = { resigning = false }) { Text("Keep playing") } },
        )
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = HeyNaija.colors
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, label, Modifier.size(22.dp), tint = if (enabled) colors.ink else colors.faint)
    }
}

private fun statusLine(game: ChessGame, replay: Replay, you: Side?, sending: Boolean): String {
    if (game.finished) return resultText(game)
    val turn = replay.current.turn
    val mover = if (turn == Side.White) game.white else game.black
    return when {
        sending -> "Sending your move…"
        game.drawOffer != null && Side.of(game.drawOffer) != you -> "${nameOf(if (game.drawOffer == "white") game.white else game.black)} offers a draw"
        turn == you -> if (replay.current.inCheck()) "Your move. You are in check." else "Your move"
        you != null -> "Waiting for ${nameOf(mover)}"
        else -> "${nameOf(mover)} to move"
    }
}

@Composable
private fun PlayerRow(game: ChessGame, side: String, turn: Boolean) {
    val colors = HeyNaija.colors
    val player = if (side == "white") game.white else game.black
    val change = game.ratingsAfter?.get(side)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(Radii.control)
            .background(if (turn) colors.panel else colors.bg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(player.handle, player.avatar, size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(nameOf(player), style = MaterialTheme.typography.bodyLarge, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val rating = when {
                change?.before != null && change.after != null -> {
                    val diff = change.after - change.before
                    "${change.after} (${if (diff >= 0) "+" else ""}$diff)"
                }
                player.rating != null -> "${player.rating}${if (player.provisional) "?" else ""}"
                else -> null
            }
            Text(
                listOfNotNull(side.replaceFirstChar { it.uppercase() }, rating).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
        }
        Clock(game, side)
    }
}

/** The side's clock, counting down on screen while it is their move. */
@Composable
private fun Clock(game: ChessGame, side: String) {
    val colors = HeyNaija.colors
    val base = (if (side == "white") game.whiteMs else game.blackMs) ?: return
    val running = !game.finished && game.moves.size >= 2 && sideToMove(game.moves) == side
    val since = game.clockAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
    // A tenth of a second at a time while it runs; still otherwise.
    val now by produceState(kotlin.time.Clock.System.now(), running, game.version) {
        while (running) {
            value = kotlin.time.Clock.System.now()
            delay(100)
        }
    }
    val left = if (running && since != null) base - (now - since).inWholeMilliseconds.coerceAtLeast(0) else base
    Text(
        formatClock(left),
        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
        color = if (running) colors.ink else colors.muted,
        fontWeight = if (running) FontWeight.SemiBold else FontWeight.Normal,
    )
}

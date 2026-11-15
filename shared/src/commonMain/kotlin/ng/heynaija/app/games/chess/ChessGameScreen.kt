package ng.heynaija.app.games.chess

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
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
import ng.heynaija.app.api.ChessGame
import ng.heynaija.app.api.ChessPlayer
import ng.heynaija.app.api.speedName
import ng.heynaija.app.games.gamesGone
import ng.heynaija.app.nav.Route
import ng.heynaija.app.posts.ListGutter
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

data class ChessGameState(val game: ChessGame? = null, val failed: Boolean = false, val gone: Boolean = false)

/**
 * One game, kept up to date by asking the API for the next change: it answers at
 * once if the game moved on since the version the screen has, else waits up to 25
 * seconds. Only while the screen is in front, and never after the game is over.
 */
class ChessGameViewModel(private val app: AppState, private val id: String) : ViewModel() {
    private val _state = MutableStateFlow(ChessGameState())
    val state: StateFlow<ChessGameState> = _state.asStateFlow()
    private var following: Job? = null

    fun start() {
        if (following?.isActive == true) return
        following = viewModelScope.launch {
            var wait = 1_000L
            while (true) {
                val have = _state.value.game
                if (have?.finished == true) return@launch
                try {
                    val game = app.api.chessGame(id, after = have?.version ?: -1)
                    _state.value = ChessGameState(game)
                    wait = 1_000L
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
                    delay(wait)
                    wait = min(wait * 2, 30_000L)
                } catch (e: Exception) {
                    _state.update { it.copy(failed = it.game == null) }
                    delay(wait)
                    wait = min(wait * 2, 30_000L)
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
            else -> GamePage(game)
        }
    }
}

@Composable
private fun GamePage(game: ChessGame) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val uri = LocalUriHandler.current
    // The player whose view it is at the bottom, as on a board; white there for a spectator.
    val bottom = game.you ?: "white"
    val top = if (bottom == "white") "black" else "white"
    LazyColumn(contentPadding = PaddingValues(ListGutter), verticalArrangement = Arrangement.spacedBy(ListGutter)) {
        item {
            Text(
                "${if (game.rated) "Rated" else "Casual"} ${speedName(game.speed)} ${game.time}",
                Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = colors.muted,
            )
        }
        item {
            Card {
                Column {
                    PlayerRow(game, top)
                    HorizontalDivider(color = colors.line)
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(statusLine(game), style = MaterialTheme.typography.titleMedium, color = colors.ink)
                        Text(
                            "${game.moves.size} ${if (game.moves.size == 1) "move" else "moves"}",
                            Modifier.padding(top = 2.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                    }
                    HorizontalDivider(color = colors.line)
                    PlayerRow(game, bottom)
                }
            }
        }
        item {
            val playing = game.you != null && !game.finished
            PrimaryButton(
                if (playing) "Play on lichess.org" else "Watch on lichess.org",
                { runCatching { uri.openUri(game.lichessUrl) } },
                Modifier.fillMaxWidth(),
                enabled = game.lichessUrl.isNotBlank(),
            )
            Text(
                if (playing) "Moves are made on Lichess for now. This page follows the game and shows the result."
                else "The board is on Lichess for now. This page follows the game and shows the result.",
                Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
        }
        val thread = game.resultPostId ?: game.threadPostId
        if (game.threadPostId != null) {
            item {
                Card {
                    MenuRow(
                        "Talk about it in Chess Arena",
                        icon = Icons.Bubble,
                        detail = if (game.you != null && !game.finished) "You see the replies once the game is over." else null,
                    ) { app.navigator.push(Route.Thread(thread ?: game.threadPostId)) }
                }
            }
        }
    }
}

private fun statusLine(game: ChessGame): String = when {
    game.finished -> resultText(game)
    game.drawOffer != null -> "${if (game.drawOffer == "white") nameOf(game.white) else nameOf(game.black)} offers a draw"
    else -> if (sideToMove(game.moves) == game.you) "Your move" else "${nameOf(if (sideToMove(game.moves) == "white") game.white else game.black)} to move"
}

@Composable
private fun PlayerRow(game: ChessGame, side: String) {
    val colors = HeyNaija.colors
    val player = if (side == "white") game.white else game.black
    val change = game.ratingsAfter?.get(side)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(player.handle, player.avatar, size = 36.dp)
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

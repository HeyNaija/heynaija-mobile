package ng.heynaija.app.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ng.heynaija.app.AppState
import ng.heynaija.app.LocalApp
import ng.heynaija.app.api.CommunityRef
import ng.heynaija.app.api.GameBoard
import ng.heynaija.app.api.GameBoardRow
import ng.heynaija.app.nav.Route
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.ui.Avatar
import ng.heynaija.app.ui.Card
import ng.heynaija.app.ui.Empty
import ng.heynaija.app.ui.Loading
import ng.heynaija.app.ui.Problem
import ng.heynaija.app.ui.TopBar

/** What the board shows: which board, which season ("all" for all time), and whose. */
data class BoardChoice(val board: String, val period: String? = null, val filter: String = "everyone", val community: CommunityRef? = null)

data class LeaderboardState(
    val choice: BoardChoice,
    val board: GameBoard? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val off: Boolean = false,
    val current: String? = null,
    /** Seasons with scores, newest first; the current one may not have any yet. */
    val seasons: List<String> = emptyList(),
    /** Communities the member joined, for "a community". */
    val communities: List<CommunityRef> = emptyList(),
)

class LeaderboardViewModel(private val app: AppState, board: String) : ViewModel() {
    private val _state = MutableStateFlow(LeaderboardState(BoardChoice(board)))
    val state: StateFlow<LeaderboardState> = _state.asStateFlow()
    private var loading: Job? = null

    init {
        viewModelScope.launch {
            try {
                val seasons = app.api.gameSeasons()
                _state.update { it.copy(current = seasons.current, seasons = seasons.seasons) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The board still shows this season and all time.
            }
        }
        viewModelScope.launch {
            try {
                val joined = app.api.communities().filter { it.viewerIsMember }.map { CommunityRef(it.slug, it.name) }
                _state.update { it.copy(communities = joined) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Without them, "A community" is not offered.
            }
        }
        load()
    }

    fun choose(choice: BoardChoice) {
        _state.update { it.copy(choice = choice) }
        load()
    }

    fun load() {
        val choice = _state.value.choice
        _state.update { it.copy(loading = true, failed = false) }
        loading?.cancel()
        loading = viewModelScope.launch {
            try {
                val board = app.api.gameBoard(choice.board, choice.period, choice.filter, choice.community?.slug)
                _state.update { it.copy(board = board, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (notAvailable(e)) {
                    _state.update { it.copy(off = true, loading = false) }
                    app.gamesGone()
                } else {
                    _state.update { it.copy(failed = true, loading = false) }
                }
            }
        }
    }
}

@Composable
fun LeaderboardScreen(board: String) {
    val app = LocalApp.current
    val boards = app.boardsInApp()
    val start = board.takeIf { b -> boards.any { it.first == b } } ?: "score"
    val viewModel = viewModel(key = "games-board") { LeaderboardViewModel(app, start) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val choice = state.choice
    Column(Modifier.fillMaxSize()) {
        TopBar("Leaderboards", onBack = { app.navigator.pop() })
        if (state.off) {
            Problem(GAMES_OFF, null)
            return@Column
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceRow {
                for ((key, name) in boards) Choice(name, choice.board == key, { viewModel.choose(choice.copy(board = key)) })
            }
            ChoiceRow {
                val current = state.current
                Choice(current?.let(::seasonName) ?: "This season", choice.period == null || choice.period == current, { viewModel.choose(choice.copy(period = null)) })
                Choice("All time", choice.period == "all", { viewModel.choose(choice.copy(period = "all")) })
                for (season in state.seasons.filter { it != current }) {
                    Choice(seasonName(season), choice.period == season, { viewModel.choose(choice.copy(period = season)) })
                }
            }
            ChoiceRow {
                Choice("Everyone", choice.filter == "everyone", { viewModel.choose(choice.copy(filter = "everyone", community = null)) })
                Choice("People you follow", choice.filter == "following", { viewModel.choose(choice.copy(filter = "following", community = null)) })
                if (state.communities.isNotEmpty()) {
                    Box {
                        var open by remember { mutableStateOf(false) }
                        Choice(choice.community?.let { it.name + " ▾" } ?: "A community ▾", choice.filter == "community", { open = true })
                        DropdownMenu(open, onDismissRequest = { open = false }) {
                            for (community in state.communities) {
                                DropdownMenuItem(
                                    text = { Text(community.name) },
                                    onClick = {
                                        open = false
                                        viewModel.choose(choice.copy(filter = "community", community = community))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = HeyNaija.colors.line)
        val shown = state.board
        when {
            shown == null && state.failed -> Problem("The leaderboard could not be loaded. Check your connection and try again.", viewModel::load)
            shown == null || state.loading && shown.board != choice.board -> Loading()
            else -> Board(shown, choice)
        }
    }
}

@Composable
private fun ChoiceRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun Board(board: GameBoard, choice: BoardChoice) {
    val inTop = board.rows.any { it.you }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (board.rows.isEmpty()) {
            item {
                Empty(
                    when {
                        choice.filter == "following" -> "Nobody you follow has a score here yet."
                        choice.filter == "community" -> "Nobody in this community has a score here yet."
                        board.period == "all" -> "Nobody has a score here yet."
                        else -> "Nobody has a score this season yet. Be the first."
                    },
                )
            }
        } else {
            item {
                Card {
                    Column {
                        board.rows.forEachIndexed { i, row ->
                            if (i > 0) HorizontalDivider(color = HeyNaija.colors.line)
                            BoardRow(row, board.unit)
                        }
                    }
                }
            }
        }
        // Your own row, wherever you stand, as on the web.
        val you = board.you
        if (you != null && !inTop) {
            item {
                Text("You", Modifier.padding(start = 4.dp, top = 8.dp), style = MaterialTheme.typography.labelMedium, color = HeyNaija.colors.muted)
            }
            item { Card { BoardRow(you, board.unit) } }
        } else if (you == null && board.rows.isNotEmpty()) {
            item {
                Text(
                    "You are not on this board yet.",
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HeyNaija.colors.muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun BoardRow(row: GameBoardRow, unit: String) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (row.you) colors.accentSoft else colors.surface)
            .clickable { app.navigator.push(Route.Profile(row.handle)) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            grouped(row.rank),
            Modifier.widthIn(min = 32.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (row.rank <= 3) colors.accentStrong else colors.muted,
            fontWeight = FontWeight.SemiBold,
        )
        Avatar(row.handle, row.avatar, size = 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            val name = row.displayName?.takeIf { it.isNotBlank() }
            Text(name ?: "@${row.handle}", style = MaterialTheme.typography.bodyLarge, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // The handle under a name; under a handle, only "you".
            listOfNotNull(name?.let { "@${row.handle}" }, "you".takeIf { row.you }).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.muted, maxLines = 1)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(scoreText(row.value, unit), style = MaterialTheme.typography.labelLarge, color = colors.ink)
    }
}

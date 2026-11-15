package ng.heynaija.app.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ng.heynaija.app.AppState
import ng.heynaija.app.LocalApp
import ng.heynaija.app.api.ApiException
import ng.heynaija.app.api.GameBoardRow
import ng.heynaija.app.api.GameManifest
import ng.heynaija.app.api.GameScoring
import ng.heynaija.app.nav.Route
import ng.heynaija.app.posts.ListGutter
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii
import ng.heynaija.app.ui.Card
import ng.heynaija.app.ui.Icons
import ng.heynaija.app.ui.Loading
import ng.heynaija.app.ui.MenuRow
import ng.heynaija.app.ui.Problem
import ng.heynaija.app.ui.SectionTitle
import ng.heynaija.app.ui.TopBar

// Games (ADR 0028 and docs/games/GAMES.md in the web repository), for members the
// `games` flag is on for. Every Games route answers 404 to anyone else, so a 404 here
// means Games has been switched off for this member: the screen says so and the
// app asks /v1/me again, which takes the way in away.

private val MONTH_NAMES = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

/** "November 2026" for the season "2026-11", "All time" for "all". */
fun seasonName(period: String): String {
    if (period == "all") return "All time"
    val year = period.substringBefore('-')
    val month = period.substringAfter('-').toIntOrNull() ?: return period
    return MONTH_NAMES.getOrNull(month - 1)?.let { "$it $year" } ?: period
}

/** 1240 → "1,240", as the web writes numbers (en-NG). */
fun grouped(n: Int): String {
    val digits = kotlin.math.abs(n).toString()
    val out = StringBuilder()
    digits.forEachIndexed { i, c ->
        if (i > 0 && (digits.length - i) % 3 == 0) out.append(',')
        out.append(c)
    }
    return if (n < 0) "-$out" else out.toString()
}

/** "1,240 XP" or "1,240 points". */
fun scoreText(value: Int, unit: String) = if (unit == "points") "${grouped(value)} ${if (value == 1) "point" else "points"}" else "${grouped(value)} XP"

/** The games this app shows: Keno only where the build has it ([ng.heynaija.app.platform.Platform.keno]). */
fun AppState.gamesInApp(games: List<GameManifest>) = games.filter { it.slug != "keno" || platform.keno }

/** The leaderboards this app shows, with their names. */
fun AppState.boardsInApp() = listOfNotNull("score" to "Game Score", "chess" to "Chess", ("keno" to "Keno").takeIf { platform.keno })

/** Games was switched off for this member while a screen was open: the way in goes once /v1/me says so. */
fun AppState.gamesGone() {
    scope.launch { refreshMe() }
}

/** Whether [e] says Games is not on for this member. */
fun notAvailable(e: Throwable) = e is ApiException && e.status == 404

const val GAMES_OFF = "Games is not available to you. If it was, it may have been switched off for now."

/** A small choice among a few, as the web's segmented buttons: green when chosen. */
@Composable
fun Choice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HeyNaija.colors
    Box(
        modifier
            .clip(Radii.control)
            .background(if (selected) colors.accentSoft else colors.surface)
            .border(1.dp, if (selected) colors.accent.copy(alpha = 0.4f) else colors.lineStrong, Radii.control)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.accentStrong else colors.ink,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** A status chip: "Beta", "Coming soon". */
@Composable
fun StatusChip(text: String, modifier: Modifier = Modifier) {
    val colors = HeyNaija.colors
    Text(
        text,
        modifier.clip(Radii.chip).background(colors.panel).padding(horizontal = 6.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = colors.muted,
    )
}

fun statusLabel(status: String) = when (status) {
    "beta" -> "Beta"
    "coming_soon" -> "Coming soon"
    else -> null
}

/** A game's mark: its icon on the sage wash, as the web shows a game until approved art exists. */
@Composable
fun GameMark(slug: String, size: Int = 44) {
    val colors = HeyNaija.colors
    Box(Modifier.size(size.dp).clip(Radii.control).background(colors.accentSoft), contentAlignment = Alignment.Center) {
        Icon(iconFor(slug), null, Modifier.size((size * 0.55f).dp), tint = colors.accentStrong)
    }
}

fun iconFor(slug: String): ImageVector = when (slug) {
    "chess" -> Icons.Chess
    else -> Icons.Games
}

/** Where a game opens; null for one the apps have no screen for yet. */
fun routeFor(game: GameManifest): Route? = when {
    game.comingSoon -> null
    game.slug == "chess" -> Route.Chess()
    else -> null
}

// Games home

data class GamesHomeState(
    val loaded: Boolean = false,
    val failed: Boolean = false,
    val off: Boolean = false,
    val games: List<GameManifest> = emptyList(),
    val season: String = "",
    val seasonRow: GameBoardRow? = null,
    val allTime: GameBoardRow? = null,
)

class GamesHomeViewModel(private val app: AppState) : ViewModel() {
    private val _state = MutableStateFlow(GamesHomeState())
    val state: StateFlow<GamesHomeState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = _state.value.copy(failed = false)
        viewModelScope.launch {
            try {
                val games = async { app.api.games() }
                val season = async { app.api.gameBoard("score", limit = 1) }
                val allTime = async { app.api.gameBoard("score", period = "all", limit = 1) }
                val now = season.await()
                _state.value = GamesHomeState(
                    loaded = true,
                    games = app.gamesInApp(games.await()),
                    season = now.period,
                    seasonRow = now.you,
                    allTime = allTime.await().you,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (notAvailable(e)) {
                    _state.value = GamesHomeState(off = true)
                    app.gamesGone()
                } else {
                    _state.value = _state.value.copy(failed = true)
                }
            }
        }
    }
}

@Composable
fun GamesHomeScreen() {
    val app = LocalApp.current
    val viewModel = viewModel(key = "games-home") { GamesHomeViewModel(app) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TopBar("Games", onBack = { app.navigator.pop() })
        when {
            state.off -> Problem(GAMES_OFF, null)
            !state.loaded && state.failed -> Problem("Games could not be loaded. Check your connection and try again.", viewModel::load)
            !state.loaded -> Loading()
            else -> LazyColumn(contentPadding = PaddingValues(ListGutter), verticalArrangement = Arrangement.spacedBy(ListGutter)) {
                item {
                    Text(
                        "Play, climb the leaderboards and take part in competitions. Every game adds to your Game Score.",
                        Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = HeyNaija.colors.muted,
                    )
                }
                item { ScoreCard(state) }
                item { SectionTitle("All games", Modifier.padding(top = 4.dp)) }
                state.games.forEach { game -> item(key = game.slug) { GameCard(game) } }
                item {
                    Card {
                        Column {
                            MenuRow("Leaderboards", icon = Icons.Trophy, detail = "Game Score and each game, this season or all time") {
                                app.navigator.push(Route.GamesLeaderboard())
                            }
                            HorizontalDivider(color = HeyNaija.colors.line)
                            MenuRow("Competitions", icon = Icons.Chess, detail = "Live, coming up and recently finished") {
                                app.navigator.push(Route.Competitions)
                            }
                            HorizontalDivider(color = HeyNaija.colors.line)
                            MenuRow("How games scoring works", icon = Icons.Info, detail = "Seasons, XP and competitions") {
                                app.navigator.push(Route.GamesScoring)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Your Game Score: this season's XP and rank, and all time. An empty state before any. */
@Composable
private fun ScoreCard(state: GamesHomeState) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    Card(onClick = { app.navigator.push(Route.GamesLeaderboard()) }) {
        Column(Modifier.padding(16.dp)) {
            Text("Your Game Score · ${seasonName(state.season)}", style = MaterialTheme.typography.labelMedium, color = colors.muted)
            val row = state.seasonRow
            if (row == null || row.value == 0) {
                Text("No XP yet this season", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleMedium, color = colors.ink)
                Text(
                    "Finish a chess game to get your first XP. Your rank shows here once you have some.",
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
            } else {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                    Text(scoreText(row.value, "xp"), style = MaterialTheme.typography.headlineSmall, color = colors.ink, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    Text("Rank ${grouped(row.rank)}", Modifier.padding(bottom = 3.dp), style = MaterialTheme.typography.bodyLarge, color = colors.accentStrong)
                }
            }
            state.allTime?.takeIf { it.value > 0 }?.let {
                Text("All time: ${scoreText(it.value, "xp")}", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
        }
    }
}

@Composable
private fun GameCard(game: GameManifest) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val route = routeFor(game)
    Card(onClick = route?.let { { app.navigator.push(it) } }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            GameMark(game.slug)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(game.name, style = MaterialTheme.typography.titleMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    statusLabel(game.status)?.let {
                        Spacer(Modifier.width(8.dp))
                        StatusChip(it)
                    }
                }
                if (game.tagline.isNotBlank()) {
                    Text(game.tagline, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyMedium, color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (route != null) Icon(Icons.Next, null, Modifier.size(18.dp), tint = colors.faint)
        }
    }
}

// How games scoring works

data class ScoringState(val rules: GameScoring? = null, val failed: Boolean = false, val off: Boolean = false)

class ScoringViewModel(private val app: AppState) : ViewModel() {
    private val _state = MutableStateFlow(ScoringState())
    val state: StateFlow<ScoringState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = _state.value.copy(failed = false)
        viewModelScope.launch {
            try {
                _state.value = ScoringState(rules = app.api.gameScoring())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (notAvailable(e)) {
                    _state.value = ScoringState(off = true)
                    app.gamesGone()
                } else {
                    _state.value = _state.value.copy(failed = true)
                }
            }
        }
    }
}

/** The web's "How games scoring works", with the numbers from the API, so they cannot drift from the scoring. */
@Composable
fun ScoringScreen() {
    val app = LocalApp.current
    val viewModel = viewModel(key = "games-scoring") { ScoringViewModel(app) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TopBar("How games scoring works", onBack = { app.navigator.pop() })
        val rules = state.rules
        when {
            state.off -> Problem(GAMES_OFF, null)
            rules == null && state.failed -> Problem("This page could not be loaded. Check your connection and try again.", viewModel::load)
            rules == null -> Loading()
            else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp)) {
                item {
                    Paragraph("Your Game Score is the XP you earn across every game, season by season. These are all the rules, the same for everyone.")
                }
                item {
                    Heading("Seasons")
                    Paragraph(
                        "A season is a calendar month, Lagos time: this one is ${seasonName(rules.season)}. Each season’s leaderboards start " +
                            "from nothing on the 1st. Your all-time totals are kept beside them and never reset.",
                    )
                }
                item {
                    val x = rules.xp
                    Heading("Game Score XP")
                    Rule("A chess game played to the end", "${x.chess.finished} XP")
                    Rule("Winning it", "+${x.chess.win} XP")
                    Rule("A draw", "+${x.chess.draw} XP")
                    Rule("An aborted game", "0 XP")
                    if (app.platform.keno) {
                        Rule("Each Keno round you have a ticket in", "${x.keno.round} XP")
                        Rule("Keno points in a round", "+1 XP per ${x.keno.pointsPerXp}, at most +${x.keno.pointsXpMax}")
                    }
                    Rule("Taking part in a competition", "${x.competition.entered} XP")
                    x.competition.places.forEachIndexed { i, xp -> Rule(listOf("1st", "2nd", "3rd").getOrElse(i) { "${i + 1}th" } + " place", "+$xp XP") }
                    Rule("4th to 10th place", "+${x.competition.topTen} XP")
                    Paragraph(
                        "Chess games count rated or casual alike; Lichess decides who won. XP is added the moment a game ends or a competition finishes.",
                        Modifier.padding(top = 8.dp),
                        muted = true,
                    )
                }
                item {
                    Heading("Leaderboards")
                    Paragraph("Game Score ranks by XP from every game. Chess ranks by XP from chess; ratings stay on Lichess and on your profile.")
                    Paragraph("Ties share a place. You can narrow any board to the people you follow, or to a community you joined.")
                    Paragraph(
                        "Members you have blocked or who blocked you, and members you muted, are left out, as they are from your feeds. " +
                            "Suspended members do not appear on any board.",
                    )
                }
                item {
                    Heading("Competitions")
                    Paragraph(
                        "Chess competitions are tournaments on Lichess, Arena or Swiss, made by a community’s moderators for its Lichess team. " +
                            "Places follow Lichess’s order, and games played in a tournament give no XP of their own.",
                    )
                    Paragraph("When a competition ends, The Arbiter posts the top ten in Game Night, or in the competition’s own community.")
                }
                item {
                    Heading("No money, ever")
                    Paragraph("XP and points cannot be bought, sold, sent to anyone or swapped for anything. They count for the leaderboards and nothing else.")
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, Modifier.padding(top = 20.dp, bottom = 6.dp), style = MaterialTheme.typography.titleMedium, color = HeyNaija.colors.ink)
}

@Composable
private fun Paragraph(text: String, modifier: Modifier = Modifier, muted: Boolean = false) {
    Text(
        text,
        modifier.padding(bottom = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = if (muted) HeyNaija.colors.muted else HeyNaija.colors.ink,
    )
}

@Composable
private fun Rule(what: String, xp: String) {
    val colors = HeyNaija.colors
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(what, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = colors.ink)
            Spacer(Modifier.width(12.dp))
            Text(xp, style = MaterialTheme.typography.labelLarge, color = colors.accentStrong)
        }
        HorizontalDivider(color = colors.line)
    }
}

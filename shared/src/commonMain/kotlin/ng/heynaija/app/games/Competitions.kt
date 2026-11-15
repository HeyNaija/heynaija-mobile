package ng.heynaija.app.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ng.heynaija.app.AppState
import ng.heynaija.app.LocalApp
import ng.heynaija.app.api.ApiException
import ng.heynaija.app.api.Competition
import ng.heynaija.app.api.CompetitionCard
import ng.heynaija.app.api.CompetitionList
import ng.heynaija.app.api.CompetitionStanding
import ng.heynaija.app.api.CompetitionTournament
import ng.heynaija.app.nav.Route
import ng.heynaija.app.posts.ListGutter
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.ui.Avatar
import ng.heynaija.app.ui.Card
import ng.heynaija.app.ui.Empty
import ng.heynaija.app.ui.Icons
import ng.heynaija.app.ui.Loading
import ng.heynaija.app.ui.MenuRow
import ng.heynaija.app.ui.PrimaryButton
import ng.heynaija.app.ui.Problem
import ng.heynaija.app.ui.SecondaryButton
import ng.heynaija.app.ui.SectionTitle
import ng.heynaija.app.ui.TopBar
import ng.heynaija.app.ui.clock

// Competitions (ADR 0028, phases 5 and 6, in the web repository), as the web shows
// them (apps/web/app/(main)/games/competitions). Making, changing and cancelling them
// stays on the website: they are moderators' tools. Keno's are left out with Keno.

private val STATUS = mapOf("draft" to "Draft", "open" to "Coming up", "live" to "Live", "finished" to "Finished", "cancelled" to "Cancelled")
private val ELIGIBILITY = mapOf("open" to "Open to everyone with Games", "community" to "For members of a community", "invite" to "By invitation")
private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

fun statusText(status: String) = STATUS[status] ?: status

/** What is played, in a word or two: "Chess · Arena", "Keno". */
fun gameLabel(game: String, format: String): String = when (format) {
    "lichess_arena" -> "Chess · Arena"
    "lichess_swiss" -> "Chess · Swiss"
    else -> when (game) {
        "keno" -> "Keno"
        "chess" -> "Chess"
        else -> game.replaceFirstChar { it.uppercase() }
    }
}

/** A score as a competition counts it: Swiss points are kept doubled, so 9 reads "4½ pts". */
fun competitionScore(format: String, score: Int): String {
    if (format != "lichess_swiss") return "${grouped(score)} pts"
    val whole = score / 2
    return if (score % 2 == 1) "${if (whole > 0) "$whole" else ""}½ pts" else "$whole pts"
}

/** "8+3" for a clock of eight minutes and three seconds a move. */
fun clockText(limitSeconds: Int, increment: Int) = "${limitSeconds / 60}+$increment"

/** "22 Nov, 8:00 pm", in the reader's time zone. */
fun dayAndTime(at: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val local = at.toLocalDateTime(zone)
    return "${local.date.day} ${MONTHS[local.date.month.ordinal]}, ${clock(local.hour, local.minute)}"
}

/** When a competition runs: "22 Nov, 8:00 pm to 10:00 pm", or across days "22 Nov, 8:00 pm to 23 Nov, 2:00 am". */
fun whenText(startsAt: String, endsAt: String, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val start = runCatching { Instant.parse(startsAt) }.getOrNull() ?: return ""
    val end = runCatching { Instant.parse(endsAt) }.getOrNull() ?: return dayAndTime(start, zone)
    val sameDay = start.toLocalDateTime(zone).date == end.toLocalDateTime(zone).date
    val endLocal = end.toLocalDateTime(zone)
    return "${dayAndTime(start, zone)} to ${if (sameDay) clock(endLocal.hour, endLocal.minute) else dayAndTime(end, zone)}"
}

/** The competitions this app shows: Keno's only where the build has Keno. */
fun AppState.competitionsInApp(items: List<CompetitionCard>) = items.filter { it.game != "keno" || platform.keno }

/** A competition in a list: its name and state, what is played, when, and how many are in it. */
@Composable
fun CompetitionRow(c: CompetitionCard) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    Column(Modifier.fillMaxWidth().clickable { app.navigator.push(Route.Competition(c.id)) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            StatusChip(statusText(c.status))
        }
        val playing = if (c.game == "chess") (if (c.entrants == 1) "member playing" else "members playing") else (if (c.entrants == 1) "entrant" else "entrants")
        Text(
            "${gameLabel(c.game, c.format)} · ${whenText(c.startsAt, c.endsAt)} · ${c.entrants} $playing",
            Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted,
        )
    }
}

/** A titled card of competitions, or nothing when there are none. */
fun LazyListScope.competitionSection(title: String, items: List<CompetitionCard>) {
    if (items.isEmpty()) return
    item(key = "section-$title") { SectionTitle(title) }
    item(key = "items-$title") {
        Card {
            Column {
                items.forEachIndexed { i, c ->
                    if (i > 0) HorizontalDivider(color = HeyNaija.colors.line)
                    CompetitionRow(c)
                }
            }
        }
    }
}

// The list of competitions: every game's, or one game's on its Competitions tab.

data class CompetitionListState(val list: CompetitionList? = null, val failed: Boolean = false, val off: Boolean = false)

class CompetitionListViewModel(private val app: AppState) : ViewModel() {
    private val _state = MutableStateFlow(CompetitionListState())
    val state: StateFlow<CompetitionListState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(failed = false) }
        viewModelScope.launch {
            try {
                _state.value = CompetitionListState(app.api.competitions())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (notAvailable(e)) {
                    _state.value = CompetitionListState(off = true)
                    app.gamesGone()
                } else {
                    _state.update { it.copy(failed = true) }
                }
            }
        }
    }
}

/**
 * Competitions, live and coming up first, then recently finished: [game]'s only on
 * its tab, or every game's the app shows. [none] is what the list says when nothing
 * is coming up. Drafts are moderators' and stay on the website.
 */
@Composable
fun CompetitionsList(game: String?, none: String, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val viewModel = viewModel(key = "competitions-${game ?: "all"}") { CompetitionListViewModel(app) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val list = state.list
    when {
        state.off -> Problem(GAMES_OFF, null, modifier)
        list == null && state.failed -> Problem("Competitions could not be loaded. Check your connection and try again.", viewModel::load, modifier)
        list == null -> Loading(modifier)
        else -> {
            fun mine(items: List<CompetitionCard>) = app.competitionsInApp(items).filter { game == null || it.game == game }
            val current = mine(list.current)
            val recent = mine(list.recent)
            LazyColumn(modifier, contentPadding = PaddingValues(start = ListGutter, end = ListGutter, top = ListGutter, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(ListGutter)) {
                if (current.isEmpty()) {
                    item { Card { Empty(none) } }
                } else {
                    competitionSection("Live and coming up", current)
                }
                competitionSection("Recently finished", recent)
                item {
                    Card {
                        Column {
                            if (game != null) {
                                MenuRow("All competitions", icon = Icons.Trophy) { app.navigator.push(Route.Competitions) }
                                HorizontalDivider(color = HeyNaija.colors.line)
                            }
                            MenuRow("How games scoring works", icon = Icons.Info) { app.navigator.push(Route.GamesScoring) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompetitionsScreen() {
    val app = LocalApp.current
    Column(Modifier.fillMaxSize()) {
        TopBar("Competitions", onBack = { app.navigator.pop() })
        CompetitionsList(game = null, none = "No competitions coming up. Communities that link a Lichess team run chess tournaments, and they show here.")
    }
}

// One competition

data class CompetitionState(val competition: Competition? = null, val failed: Boolean = false, val missing: Boolean = false, val busy: Boolean = false)

class CompetitionViewModel(private val app: AppState, private val id: String) : ViewModel() {
    private val _state = MutableStateFlow(CompetitionState())
    val state: StateFlow<CompetitionState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(failed = false) }
        viewModelScope.launch {
            try {
                _state.value = CompetitionState(app.api.competition(id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A 404 is a competition that is not there, or Games switched off: either way there is nothing to show.
                if (notAvailable(e)) _state.value = CompetitionState(missing = true)
                else _state.update { it.copy(failed = true) }
            }
        }
    }

    private fun act(failure: String, done: String, work: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                work()
                app.say(done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                app.say(e.message?.takeIf { e.status in 400..499 && e.status != 404 } ?: failure)
            } catch (e: Exception) {
                app.say("$failure Check your connection and try again.")
            } finally {
                _state.update { it.copy(busy = false) }
                load()
            }
        }
    }

    fun enter() = act("You could not enter.", "You’re in.") { app.api.enterCompetition(id) }
    fun withdraw() = act("You could not withdraw.", "You withdrew.") { app.api.withdrawFromCompetition(id) }
}

@Composable
fun CompetitionScreen(id: String) {
    val app = LocalApp.current
    val viewModel = viewModel(key = "competition-$id") { CompetitionViewModel(app, id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TopBar("Competition", onBack = { app.navigator.pop() })
        val c = state.competition
        when {
            state.missing -> Problem("There is no such competition, or it is not open to you.", null)
            c == null && state.failed -> Problem("This competition could not be loaded. Check your connection and try again.", viewModel::load)
            c == null -> Loading()
            // Keno stays out of the apps until the Apple account is an organisation's.
            c.game == "keno" && !app.platform.keno -> Problem("This competition is not in the app. You can see it on the HeyNaija website.", null)
            else -> CompetitionPage(c, state.busy, viewModel)
        }
    }
}

@Composable
private fun CompetitionPage(c: Competition, busy: Boolean, viewModel: CompetitionViewModel) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val shown = c.standings.any { it.you }
    LazyColumn(contentPadding = PaddingValues(start = ListGutter, end = ListGutter, top = ListGutter, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(ListGutter)) {
        item {
            Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                Text(c.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = colors.ink)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(statusText(c.status))
                    Spacer(Modifier.width(8.dp))
                    Text("${gameLabel(c.game, c.format)} · ${whenText(c.startsAt, c.endsAt)}", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }
            }
        }
        item {
            val tournament = c.chess
            if (tournament != null) TournamentRules(c, tournament) else Rules(c, busy, viewModel)
        }
        item {
            SectionTitle(if (c.status == "finished") "Final standings" else "Standings")
        }
        item {
            Card {
                Column {
                    val counted = c.chess?.let { t -> "${c.entrants} from HeyNaija" + (t.players?.let { " · $it on Lichess" } ?: "") }
                        ?: "${c.entrants} ${if (c.entrants == 1) "entrant" else "entrants"}"
                    Text(counted, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    if (c.chess != null && c.status != "finished" && c.status != "cancelled") {
                        Text("Read from Lichess every couple of minutes. Lichess decides the results.", Modifier.padding(horizontal = 16.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    }
                    c.resultPostId?.let { post ->
                        Text(
                            "The Arbiter’s result post",
                            Modifier.padding(horizontal = 16.dp, vertical = 6.dp).clickable { app.navigator.push(Route.Thread(post)) },
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.accentStrong,
                        )
                    }
                    if (c.standings.isEmpty()) {
                        Empty(
                            when {
                                c.chess != null -> "No HeyNaija members have played yet. Members of the community who have connected Lichess show here once they play in it."
                                c.you.canEnter -> "Nobody has entered yet. Be the first."
                                else -> "Nobody has entered yet. Entrants will be listed here."
                            },
                        )
                    } else {
                        Spacer(Modifier.padding(top = 6.dp))
                        c.standings.forEach { StandingRow(it, c.format) }
                        val you = c.yourStanding
                        if (you != null && !shown) {
                            Text("…", Modifier.padding(horizontal = 16.dp), color = colors.muted)
                            StandingRow(you, c.format)
                        }
                        Spacer(Modifier.padding(bottom = 6.dp))
                    }
                }
            }
        }
    }
}

/** How a competition scored from play here works, and entering it. */
@Composable
private fun Rules(c: Competition, busy: Boolean, viewModel: CompetitionViewModel) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    var withdrawing by remember { mutableStateOf(false) }
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("How it works", style = MaterialTheme.typography.titleMedium, color = colors.ink)
            Fact("Scoring", "${c.formatText} Ties share a place.")
            Fact("Who can enter", ELIGIBILITY[c.eligibility].orEmpty() + (c.community?.let { ": ${it.name}" } ?: "") + ".")
            Fact(
                "Entry",
                "Opens ${runCatching { dayAndTime(Instant.parse(c.entryOpensAt)) }.getOrDefault("")}, closes " +
                    "${runCatching { dayAndTime(Instant.parse(c.entryClosesAt)) }.getOrDefault("")}. You can withdraw until entry closes.",
            )
            Fact("Game Score", "Everyone who plays in it gets XP for taking part, and more for a top-ten place.")
            c.community?.let { community ->
                Text(
                    community.name,
                    Modifier.padding(top = 6.dp).clickable { app.navigator.push(Route.Community(community.slug)) },
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.accentStrong,
                )
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (c.you.canEnter) PrimaryButton("Enter", viewModel::enter, busy = busy)
                if (c.you.canWithdraw) SecondaryButton("Withdraw", { withdrawing = true }, enabled = !busy)
                if (c.you.entered) Text("You’re in.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.ink)
            }
            val reason = c.you.reason
            if (!c.you.entered && !c.you.canEnter && reason != null && c.status != "finished" && c.status != "cancelled") {
                Text(reason, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
        }
    }
    if (withdrawing) {
        AlertDialog(
            onDismissRequest = { withdrawing = false },
            title = { Text("Withdraw from this competition?") },
            text = { Text("Points you scored in it go too.") },
            confirmButton = { TextButton(onClick = { withdrawing = false; viewModel.withdraw() }) { Text("Withdraw", color = colors.danger) } },
            dismissButton = { TextButton(onClick = { withdrawing = false }) { Text("Stay in") } },
        )
    }
}

/** A Lichess tournament's rules, and where to join and play it: on lichess.org. */
@Composable
private fun TournamentRules(c: Competition, t: CompetitionTournament) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val uri = LocalUriHandler.current
    val open = c.status != "finished" && c.status != "cancelled"
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("How it works", style = MaterialTheme.typography.titleMedium, color = colors.ink)
            Fact("Format", c.formatText)
            Fact(
                "Games",
                "${clockText(t.clock.limit, t.clock.increment)}, ${if (t.rated) "rated" else "casual"}, " +
                    (if (t.kind == "arena") "for ${t.minutes ?: 0} minutes" else "${t.rounds ?: 0} rounds") + ". Played on lichess.org.",
            )
            Fact(
                "Who can play",
                "Members of the Lichess team${t.teamName?.let { " $it" } ?: ""}. HeyNaija members of ${c.community?.name ?: "the community"} who have " +
                    "connected Lichess show in the standings here, in Lichess’s order.",
            )
            Fact("Game Score", "XP for taking part, and more for a top-ten place among HeyNaija members.")
            if (t.unfinished) Fact("Note", "Lichess had not finished it a day after it was due to end, so it was finished from the last standings read.")
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(if (open) "Join on Lichess" else "See it on Lichess", { runCatching { uri.openUri(t.url) } })
                if (open && t.teamUrl.isNotBlank()) SecondaryButton("Join the team", { runCatching { uri.openUri(t.teamUrl) } })
            }
            c.community?.let { community ->
                Text(
                    "Chess for ${community.name}",
                    Modifier.padding(top = 10.dp).clickable { app.navigator.push(Route.ChessTeam(community.slug)) },
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.accentStrong,
                )
            }
        }
    }
}

@Composable
private fun Fact(label: String, text: String) {
    val colors = HeyNaija.colors
    Column(Modifier.padding(top = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.muted)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.ink)
    }
}

@Composable
private fun StandingRow(s: CompetitionStanding, format: String) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (s.you) colors.accentSoft else colors.surface)
            .clickable { app.navigator.push(Route.Profile(s.handle)) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(s.place?.let { grouped(it) } ?: "–", Modifier.widthIn(min = 32.dp), style = MaterialTheme.typography.labelLarge, color = if ((s.place ?: 99) <= 3) colors.accentStrong else colors.muted)
        Avatar(s.handle, s.avatar, size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (s.displayName?.takeIf { it.isNotBlank() } ?: "@${s.handle}") + if (s.you) " (you)" else "",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            s.lichess?.let { l ->
                Text(
                    (l.rank?.let { "#$it on Lichess" } ?: "On Lichess") + (l.username?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    maxLines = 1,
                )
            }
        }
        s.score?.let { Text(competitionScore(format, it), style = MaterialTheme.typography.labelLarge, color = colors.ink) }
    }
}

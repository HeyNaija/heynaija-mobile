package ng.heynaija.app.games.trivia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Clock
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
import ng.heynaija.app.api.TriviaCurrent
import ng.heynaija.app.api.TriviaPicture
import ng.heynaija.app.api.TriviaList
import ng.heynaija.app.api.TriviaReveal
import ng.heynaija.app.api.TriviaRound
import ng.heynaija.app.api.TriviaRoundCard
import ng.heynaija.app.api.TriviaStanding
import ng.heynaija.app.api.TriviaYourAnswer
import ng.heynaija.app.api.mediaUrl
import ng.heynaija.app.games.GAMES_OFF
import ng.heynaija.app.games.GameMark
import ng.heynaija.app.games.GamePosts
import ng.heynaija.app.games.GameTabRow
import ng.heynaija.app.games.LeaderboardPanel
import ng.heynaija.app.games.StatusChip
import ng.heynaija.app.games.dayAndTime
import ng.heynaija.app.games.gamesGone
import ng.heynaija.app.games.grouped
import ng.heynaija.app.games.notAvailable
import ng.heynaija.app.nav.Route
import ng.heynaija.app.posts.ListGutter
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii
import ng.heynaija.app.ui.Avatar
import ng.heynaija.app.ui.Card
import ng.heynaija.app.ui.Empty
import ng.heynaija.app.ui.Field
import ng.heynaija.app.ui.Icons
import ng.heynaija.app.ui.Loading
import ng.heynaija.app.ui.MenuRow
import ng.heynaija.app.ui.PrimaryButton
import ng.heynaija.app.ui.Problem
import ng.heynaija.app.ui.SectionTitle
import ng.heynaija.app.ui.TopBar

// Trivia (ADR 0028 phase 7, docs/games/GAMES.md in the web repository): live rounds
// run by a host, who writes and runs them on the website. Here members play: each
// question appears on every screen at the same moment, by the server's clock.

private val STATUS = mapOf(
    "draft" to "Draft",
    "scheduled" to "Coming up",
    "live" to "Live",
    "reviewing" to "Checking answers",
    "published" to "Results out",
    "cancelled" to "Called off",
)

/** "1st", "2nd", "3rd", "4th" … */
fun ordinal(n: Int): String {
    val tens = n % 100
    if (tens in 11..13) return "${n}th"
    return n.toString() + when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
}

/** "2.41 s" */
fun seconds(ms: Int): String {
    val hundredths = max(0, ms) / 10
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')} s"
}

private fun millisOf(iso: String): Long? = runCatching { Instant.parse(iso).toEpochMilliseconds() }.getOrNull()

private fun nowMillis() = Clock.System.now().toEpochMilliseconds()

private fun pointsText(n: Int) = "${grouped(n)} ${if (n == 1) "point" else "points"}"

// The rounds

data class TriviaListState(val list: TriviaList? = null, val failed: Boolean = false, val off: Boolean = false)

class TriviaListViewModel(private val app: AppState) : ViewModel() {
    private val _state = MutableStateFlow(TriviaListState())
    val state: StateFlow<TriviaListState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(failed = false) }
        viewModelScope.launch {
            try {
                _state.value = TriviaListState(app.api.triviaList())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (notAvailable(e)) {
                    _state.value = TriviaListState(off = true)
                    app.gamesGone()
                } else {
                    _state.update { it.copy(failed = true) }
                }
            }
        }
    }
}

/** Trivia, on one of the tabs every game has. */
@Composable
fun TriviaScreen(startTab: String = "play") {
    val app = LocalApp.current
    val viewModel = viewModel(key = "trivia") { TriviaListViewModel(app) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(startTab) }
    // Back from a round, or from the background: rounds may have moved on.
    val returns = remember { intArrayOf(0) }
    LifecycleResumeEffect(viewModel) {
        if (returns[0]++ > 0) viewModel.load()
        onPauseOrDispose { }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("Trivia", onBack = { app.navigator.pop() })
        GameTabRow(tab) { tab = it }
        when (tab) {
            "leaderboard" -> LeaderboardPanel("trivia", fixed = true)
            "competitions" -> Empty("Trivia is not in competitions: every round already has its own results. Rounds are on the Play tab.")
            "posts" -> GamePosts("trivia", "Trivia", "game-night" to "Game Night")
            else -> {
                val list = state.list
                when {
                    state.off -> Problem(GAMES_OFF, null)
                    list == null && state.failed -> Problem("Trivia could not be loaded. Check your connection and try again.", viewModel::load)
                    list == null -> Loading()
                    else -> RoundsPage(list)
                }
            }
        }
    }
}

@Composable
private fun RoundsPage(list: TriviaList) {
    val colors = HeyNaija.colors
    LazyColumn(contentPadding = PaddingValues(start = ListGutter, end = ListGutter, top = ListGutter, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(ListGutter)) {
        item {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                GameMark("trivia", 40)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Live quiz rounds, run by a host. Each question appears on everyone’s screen at the same moment; the fastest right answers score most.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
            }
        }
        roundSection("Live now", list.live)
        roundSection("Coming up", list.upcoming)
        if (list.live.isEmpty() && list.upcoming.isEmpty() && list.recent.isEmpty()) {
            item {
                Card {
                    Text(
                        "No rounds yet. When a host schedules one, it shows here and The Arbiter posts it in Game Night. Open it a little before it starts.",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                }
            }
        }
        roundSection("Recent results", list.recent)
        if (list.mayHost) {
            item {
                Text(
                    "You can host rounds. Write and run them on the website, from Games, Trivia.",
                    Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
        }
        item { HowPoints() }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.roundSection(title: String, items: List<TriviaRoundCard>) {
    if (items.isEmpty()) return
    item(key = "title-$title") { SectionTitle(title) }
    item(key = "items-$title") {
        Card {
            Column {
                items.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider(color = HeyNaija.colors.line)
                    RoundRow(r)
                }
            }
        }
    }
}

@Composable
private fun RoundRow(r: TriviaRoundCard) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    Column(Modifier.fillMaxWidth().clickable { app.navigator.push(Route.TriviaRound(r.id)) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            StatusChip(STATUS[r.status] ?: r.status)
        }
        val start = runCatching { dayAndTime(Instant.parse(r.startsAt)) }.getOrDefault("")
        val players = if (r.players > 0) " · ${r.players} ${if (r.players == 1) "player" else "players"}" else ""
        Text(
            "$start · ${r.questions} ${if (r.questions == 1) "question" else "questions"}$players · hosted by ${r.host.name}",
            Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted,
        )
        r.yourPlace?.let {
            Text("You came ${ordinal(it)}", Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = colors.accentStrong, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HowPoints() {
    val colors = HeyNaija.colors
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("How points work", style = MaterialTheme.typography.titleSmall, color = colors.ink)
            for (line in listOf(
                "A right answer scores 10 points. The first three right answers score 5, 3 and 2 more, decided by HeyNaija’s server as each answer arrives, not by your phone’s clock.",
                "You get one answer a question, and it cannot be changed. Answers stay hidden until time is up.",
                "Typed answers ignore capitals, accents and punctuation, and forgive a small typo in a longer answer.",
                "Points count for the Trivia leaderboard and your Game Score, and nothing else. No prizes.",
            )) Text(line, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        }
    }
}

// A round

data class TriviaRoundState(
    val round: TriviaRound? = null,
    /** The server's clock minus this phone's, from the last answer. */
    val offsetMs: Long = 0,
    val failed: Boolean = false,
    val gone: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
)

class TriviaRoundViewModel(private val app: AppState, private val id: String) : ViewModel() {
    private val _state = MutableStateFlow(TriviaRoundState())
    val state: StateFlow<TriviaRoundState> = _state.asStateFlow()
    private var following: Job? = null

    private fun take(next: TriviaRound) {
        val offset = (millisOf(next.serverTime) ?: nowMillis()) - nowMillis()
        _state.update {
            val have = it.round
            // An answer sent is kept until the server's view has it too.
            val keep = have != null && next.version == have.version && next.current?.prompt == have.current?.prompt
            it.copy(round = if (keep) have else next, offsetMs = offset, failed = false)
        }
    }

    /** Asks for each change, as long as the screen is showing and the round goes on. */
    fun start() {
        if (following?.isActive == true) return
        following = viewModelScope.launch {
            var wait = 1_000L
            // The Arbiter's results post is written a moment after the results: once they are out, look again a few times for it.
            var lookedForPost = 0
            while (true) {
                val have = _state.value.round
                if (have?.status == "published") {
                    if (have.resultPostId != null || lookedForPost >= 3) return@launch
                    lookedForPost++
                    delay(2_000)
                    try {
                        take(app.api.triviaRound(id))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // The results are on the screen already; the post's link is a nicety.
                    }
                    continue
                }
                try {
                    take(app.api.triviaRound(id, after = have?.version ?: -1))
                    wait = 1_000L
                    if (_state.value.round?.status == "cancelled") return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ApiException) {
                    if (e.status == 404) {
                        // Gone, never there, or Games or Trivia switched off for this member.
                        _state.value = TriviaRoundState(gone = true)
                        app.gamesGone()
                        return@launch
                    }
                    _state.update { it.copy(failed = it.round == null) }
                    delay(wait)
                    wait = min(wait * 2, 30_000L)
                } catch (e: Exception) {
                    _state.update { it.copy(failed = it.round == null) }
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

    fun answer(question: TriviaCurrent, choice: Int? = null, text: String? = null) {
        val s = _state.value
        if (s.busy || question.yourAnswer != null) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val sent = app.api.triviaAnswer(id, question.id, choice = choice, text = text)
                _state.update {
                    val r = it.round
                    val current = r?.current?.takeIf { c -> c.id == question.id }
                    it.copy(
                        busy = false,
                        round = if (r != null && current != null) r.copy(current = current.copy(yourAnswer = TriviaYourAnswer(choice, text, sent.ms))) else r,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = when {
                    e is ApiException && e.status == 429 -> "That was a lot at once. Wait a moment and try again."
                    e is ApiException && e.status in 400..499 -> e.message ?: "That answer did not count."
                    else -> "That did not go through. Check your connection and try again."
                }
                _state.update { it.copy(busy = false, error = message) }
            }
        }
    }
}

@Composable
fun TriviaRoundScreen(id: String) {
    val app = LocalApp.current
    val viewModel = viewModel(key = "trivia-round-$id") { TriviaRoundViewModel(app, id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.start()
        onPauseOrDispose { viewModel.stop() }
    }
    val round = state.round
    Column(Modifier.fillMaxSize()) {
        TopBar(round?.title ?: "Trivia", onBack = { app.navigator.pop() })
        when {
            state.gone -> Problem("This round is not available. It may not exist, or Games may be switched off for you for now.", null)
            round == null && state.failed -> Problem("This round could not be loaded. Check your connection and try again.", viewModel::retry)
            round == null -> Loading()
            else -> RoundPage(round, state, viewModel)
        }
    }
}

@Composable
private fun RoundPage(round: TriviaRound, state: TriviaRoundState, viewModel: TriviaRoundViewModel) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val current = round.current
    // The countdown and the time left run here between the server's answers.
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(current?.id, current?.prompt) {
        while (current != null) {
            now = nowMillis()
            delay(100)
        }
    }
    val serverNow = now + state.offsetMs
    val opensIn = current?.let { (millisOf(it.opensAt) ?: 0) - serverNow } ?: 0
    val closesIn = current?.let { (millisOf(it.closesAt) ?: 0) - serverNow } ?: 0
    val open = current != null && current.prompt != null && opensIn <= 0 && closesIn > 0

    LazyColumn(contentPadding = PaddingValues(start = ListGutter, end = ListGutter, top = ListGutter, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(ListGutter)) {
        if (!round.you.canAnswer) {
            item {
                Card {
                    Text(
                        if (round.you.host) "You are hosting this round, so you watch it here without answering. Run it from the website."
                        else "As a Trivia host you can see this round’s answers, so you watch it here without answering.",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink,
                    )
                }
            }
        }
        item(key = "stage") {
            Card {
                Box(Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                    when {
                        round.status == "cancelled" -> Line("This round was called off. Nobody scores anything from it.")
                        round.status == "scheduled" || round.status == "draft" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val start = runCatching { dayAndTime(Instant.parse(round.startsAt)) }.getOrDefault("")
                            Text("Starts $start", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                            Line(
                                "${round.questions} ${if (round.questions == 1) "question" else "questions"}, ${round.secondsPerQuestion} seconds each, hosted by ${round.host.name}. " +
                                    "Keep this screen open: each question appears here when the host opens it.",
                                muted = true,
                            )
                        }
                        current != null && !open && opensIn > 0 -> Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Line("Question ${current.position} of ${round.questions}", muted = true)
                            Text(
                                ceil(opensIn / 1000.0).toInt().toString(),
                                style = MaterialTheme.typography.displayMedium.copy(fontSize = 56.sp),
                                fontWeight = FontWeight.ExtraBold,
                                color = colors.accentStrong,
                            )
                            Line("Get ready…", muted = true)
                        }
                        current != null && open -> QuestionPanel(round, current, closesIn, state, viewModel)
                        current != null -> Line(if (closesIn <= 0) "Time is up. Checking the answers…" else "Here it comes…", muted = true, center = true)
                        round.status == "live" -> Line(
                            if (round.last != null) "Waiting for the host to open the next question." else "The round has started. Waiting for the host to open the first question.",
                            muted = true,
                        )
                        round.status == "reviewing" -> Line("That was the last question. The host is checking typed answers; the results are out within 15 minutes.", muted = true)
                        else -> Line(
                            "The results are out." + (round.yourStanding?.let { " You came ${ordinal(it.rank)} with ${pointsText(it.points)}." } ?: ""),
                        )
                    }
                }
            }
        }
        val last = round.last
        if (last != null && !open) item(key = "reveal-${last.id}") { RevealCard(last, round.questions, round.you.canAnswer) }
        if (round.status != "scheduled" && round.status != "draft" && round.status != "cancelled") {
            item(key = "standings") { StandingsCard(round.standings, round.yourStanding, round.final) }
        }
        item(key = "links") {
            Card {
                Column {
                    if (round.resultPostId != null) {
                        MenuRow("The Arbiter’s results", icon = Icons.Trophy) { app.navigator.push(Route.Thread(round.resultPostId)) }
                        HorizontalDivider(color = colors.line)
                    }
                    if (round.threadPostId != null) {
                        MenuRow("Talk about it in the thread", icon = Icons.Bubble) { app.navigator.push(Route.Thread(round.threadPostId)) }
                        HorizontalDivider(color = colors.line)
                    }
                    MenuRow("All rounds", icon = Icons.Trivia) { app.navigator.push(Route.Trivia()) }
                }
            }
        }
    }
}

@Composable
private fun Line(text: String, muted: Boolean = false, center: Boolean = false) {
    Text(
        text,
        Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        color = if (muted) HeyNaija.colors.muted else HeyNaija.colors.ink,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
    )
}

/** The question while it is open: tap a choice, or type, once. */
@Composable
private fun QuestionPanel(round: TriviaRound, q: TriviaCurrent, closesIn: Long, state: TriviaRoundState, viewModel: TriviaRoundViewModel) {
    val colors = HeyNaija.colors
    val mine = q.yourAnswer
    val locked = mine != null || !round.you.canAnswer || state.busy
    val left = max(0L, closesIn)
    val share = min(1f, left / (round.secondsPerQuestion * 1000f))
    var text by rememberSaveable(q.id) { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Question ${q.position} of ${round.questions}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = colors.muted)
            Text("${ceil(left / 1000.0).toInt()} s", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.ink)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.line)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(share).background(colors.accent))
        }
        Text(q.prompt ?: "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.ink)
        q.image?.let { QuestionPicture(it) }
        if (q.kind == "choice") {
            for (c in q.choices.orEmpty()) {
                val picked = mine?.choice == c.id
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(Radii.control)
                        .background(if (picked) colors.accent else colors.surface)
                        .border(1.dp, if (picked) colors.accent else colors.lineStrong, Radii.control)
                        .clickable(enabled = !locked) { viewModel.answer(q, choice = c.id) }
                        .semantics {
                            role = Role.Button
                            selected = picked
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        c.text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            picked -> colors.accentInk
                            locked -> colors.muted
                            else -> colors.ink
                        },
                    )
                }
            }
        } else if (mine != null) {
            Box(Modifier.fillMaxWidth().clip(Radii.control).border(1.dp, colors.accent, Radii.control).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(mine.text ?: "", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = colors.ink)
            }
        } else if (round.you.canAnswer) {
            val send = { if (text.isNotBlank()) viewModel.answer(q, text = text.trim()) }
            Field(text, { if (it.length <= 80) text = it }, "Your answer", placeholder = "Type your answer", onDone = send, enabled = !locked)
            PrimaryButton("Answer", send, enabled = text.isNotBlank() && !locked, busy = state.busy)
        }
        when {
            mine != null -> Text("Answered in ${seconds(mine.ms)}. You see whether it was right when time is up.", style = MaterialTheme.typography.bodySmall, color = colors.muted)
            !round.you.canAnswer -> Text("You watch without answering.", style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
        state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.danger) }
    }
}

/** The last question with its answer, how many got it, and who was quickest. */
@Composable
private fun RevealCard(reveal: TriviaReveal, total: Int, plays: Boolean) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val you = reveal.you
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Question ${reveal.position} of $total", style = MaterialTheme.typography.bodySmall, color = colors.muted)
            Text(reveal.prompt, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.ink)
            reveal.image?.let { QuestionPicture(it) }
            if (reveal.voided) {
                Text("The host cancelled this question. Nobody scores from it.", style = MaterialTheme.typography.bodyMedium, color = colors.ink)
                return@Column
            }
            val choices = reveal.choices
            if (choices != null) {
                for (c in choices) {
                    val yours = you?.choice == c.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(Radii.control)
                            .background(if (c.right) colors.accentSoft else colors.surface)
                            .border(1.dp, if (c.right) colors.accent else if (yours) colors.danger else colors.lineStrong, Radii.control)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = if (c.right) colors.accentStrong else colors.ink)
                        Text(
                            buildString {
                                append(c.count)
                                if (c.right) append(" · right")
                                if (yours) append(" · yours")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.muted,
                        )
                    }
                }
            } else {
                Text(
                    "Answer: ${reveal.answer}" + if (reveal.alsoAccepted.isNotEmpty()) " (also accepted: ${reveal.alsoAccepted.take(3).joinToString(", ")})" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink,
                )
            }
            reveal.explanation?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.muted) }
            val mine = when {
                you == null -> if (plays) "You did not answer this one." else null
                you.correct -> "Right" + (you.place?.takeIf { it <= 3 }?.let { ", and ${ordinal(it)}" } ?: "") + ": +${pointsText(you.points)}"
                else -> "Not this time" + (you.text?.let { ": you answered “$it”" } ?: "") + "."
            }
            mine?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = if (you?.correct == true) FontWeight.Bold else FontWeight.Normal, color = colors.ink) }
            Text("${reveal.rightCount} of ${reveal.answered} got it right.", style = MaterialTheme.typography.bodySmall, color = colors.muted)
            reveal.firsts.forEachIndexed { i, f ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { app.navigator.push(Route.Profile(f.handle)) }) {
                    Text("${ordinal(i + 1)}: ", style = MaterialTheme.typography.bodyMedium, color = colors.ink)
                    Text(f.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.ink)
                    Text("  ${seconds(f.ms)}", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }
            }
        }
    }
}

@Composable
private fun StandingsCard(rows: List<TriviaStanding>, you: TriviaStanding?, final: Boolean) {
    val colors = HeyNaija.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(if (final) "Results" else "Standings so far")
        Card {
            Column {
                if (rows.isEmpty()) {
                    Text("Nobody has scored yet. Points appear here as each question closes.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                }
                rows.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider(color = colors.line)
                    StandingRow(r)
                }
                if (you != null && rows.none { it.you }) {
                    HorizontalDivider(color = colors.line)
                    StandingRow(you)
                }
            }
        }
        if (!final && rows.isNotEmpty()) {
            Text("Places are final once the host publishes the results.", Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
    }
}

@Composable
private fun StandingRow(r: TriviaStanding) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    Row(
        Modifier.fillMaxWidth().clickable { app.navigator.push(Route.Profile(r.handle)) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(r.rank.toString(), Modifier.width(28.dp), style = MaterialTheme.typography.labelLarge, color = colors.muted, fontWeight = FontWeight.Bold)
        Avatar(r.handle, r.avatar, size = 28.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            r.name + if (r.you) " (you)" else "",
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (r.you) FontWeight.SemiBold else FontWeight.Normal,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(pointsText(r.points), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.ink)
    }
}

/**
 * A question's picture, whole and in its own shape, no taller than a third of a phone
 * screen so the choices stay in view (as on the web, `.trivia-picture`). Always the 640
 * version: while the question is open the clock is running, and a slow connection
 * should not also be fetching the large one.
 */
@Composable
private fun QuestionPicture(picture: TriviaPicture) {
    val colors = HeyNaija.colors
    val w = picture.width ?: 0
    val h = picture.height ?: 0
    val ratio = (if (w > 0 && h > 0) w.toFloat() / h else 4f / 3f).coerceIn(0.5f, 3f)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AsyncImage(
            // A GIF moves, as on the web.
            model = mediaUrl(picture.src, "640"),
            contentDescription = picture.alt.ifBlank { null },
            contentScale = ContentScale.Fit,
            modifier = Modifier.heightIn(max = 280.dp).aspectRatio(ratio).clip(Radii.control).background(colors.panel),
        )
    }
}

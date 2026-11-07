package ng.heynaija.app.search

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ng.heynaija.app.AppState
import ng.heynaija.app.LocalApp
import ng.heynaija.app.api.Community
import ng.heynaija.app.api.MemberSummary
import ng.heynaija.app.api.SearchResults
import ng.heynaija.app.api.TrendingTopic
import ng.heynaija.app.communities.CommunityMark
import ng.heynaija.app.nav.Route
import ng.heynaija.app.posts.ListGutter
import ng.heynaija.app.posts.PostList
import ng.heynaija.app.posts.PostListViewModel
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii
import ng.heynaija.app.ui.Avatar
import ng.heynaija.app.ui.Card
import ng.heynaija.app.ui.FounderBadge
import ng.heynaija.app.ui.Empty
import ng.heynaija.app.ui.Icons
import ng.heynaija.app.ui.PostCard
import ng.heynaija.app.ui.SectionTitle
import ng.heynaija.app.ui.TopBar
import ng.heynaija.app.ui.plural
import ng.heynaija.app.ui.rememberNow

data class SearchState(
    val query: String = "",
    val results: SearchResults? = null,
    val searching: Boolean = false,
    val failed: Boolean = false,
    val topics: List<TrendingTopic> = emptyList(),
)

class SearchViewModel(private val app: AppState) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            runCatching { app.api.trendingTopics() }.onSuccess { t -> _state.update { it.copy(topics = t.topics) } }
        }
    }

    /** Searches a moment after typing stops; the API wants 2 to 80 characters. */
    fun search(query: String) {
        _state.update { it.copy(query = query) }
        job?.cancel()
        val q = query.trim()
        if (q.length < 2) {
            _state.update { it.copy(results = null, searching = false, failed = false) }
            return
        }
        job = viewModelScope.launch {
            delay(300)
            _state.update { it.copy(searching = true, failed = false) }
            try {
                val results = app.api.search(q.take(80))
                _state.update { it.copy(results = results, searching = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(searching = false, failed = true) }
            }
        }
    }
}

@Composable
fun SearchScreen() {
    val app = LocalApp.current
    val viewModel = viewModel(key = "search") { SearchViewModel(app) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(state.query) }
    val now = rememberNow()
    val focus = LocalFocusManager.current
    val field = remember { FocusRequester() }
    val colors = HeyNaija.colors

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.background(colors.bg).statusBarsPadding()) {
            Row(
                Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .background(colors.surface, Radii.control)
                    .border(1.dp, colors.lineStrong, Radii.control)
                    // The whole box is the field, not only its line of text.
                    .clickable { field.requestFocus() }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Search, null, Modifier.size(18.dp), tint = colors.muted)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search people, communities and posts", style = MaterialTheme.typography.bodyLarge, color = colors.faint, maxLines = 1)
                    BasicTextField(
                        query,
                        { query = it; viewModel.search(it) },
                        Modifier.fillMaxWidth().focusRequester(field).semantics { contentDescription = "Search" },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.ink),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(Icons.Close, "Clear", Modifier.size(18.dp).clickable { query = ""; viewModel.search("") }, tint = colors.muted)
                }
            }
            HorizontalDivider(color = colors.line)
        }
        val results = state.results
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(ListGutter), verticalArrangement = Arrangement.spacedBy(ListGutter)) {
            when {
                state.searching && results == null -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                state.failed -> item { Empty("Search is not working right now. Try again in a moment.") }
                results == null -> {
                    if (state.topics.isNotEmpty()) {
                        item { SectionTitle("TRENDING THIS WEEK") }
                        items(state.topics, key = { "t-${it.tag}" }) { TopicRow(it) }
                    } else {
                        item { Empty("Find people by name or handle, communities by subject, and posts by what they say.") }
                    }
                }
                results.members.isEmpty() && results.communities.isEmpty() && results.posts.isEmpty() ->
                    item { Empty("Nothing matches \"${state.query.trim()}\".") }
                else -> {
                    if (results.members.isNotEmpty()) {
                        item { SectionTitle("PEOPLE") }
                        item { Card { Column { results.members.forEachIndexed { i, m -> if (i > 0) HorizontalDivider(color = colors.line); MemberRow(m) } } } }
                    }
                    if (results.communities.isNotEmpty()) {
                        item { SectionTitle("COMMUNITIES") }
                        item { Card { Column { results.communities.forEachIndexed { i, c -> if (i > 0) HorizontalDivider(color = colors.line); CommunityRow(c) } } } }
                    }
                    if (results.posts.isNotEmpty()) {
                        item { SectionTitle("POSTS") }
                        items(results.posts, key = { "p-${it.id}" }) { PostCard(it, now) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicRow(topic: TrendingTopic) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    Card(onClick = { app.navigator.push(Route.Hashtag(topic.tag)) }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Hash, null, Modifier.size(18.dp), tint = colors.accentStrong)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("#${topic.label}", style = MaterialTheme.typography.titleSmall, color = colors.ink)
                Text("${plural(topic.members, "person")} · ${plural(topic.posts, "post")}".replace("persons", "people"), style = MaterialTheme.typography.labelSmall, color = colors.muted)
            }
        }
    }
}

@Composable
fun MemberRow(member: MemberSummary) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    Row(
        Modifier.fillMaxWidth().clickable { app.navigator.push(Route.Profile(member.handle)) }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(member.handle, member.avatar, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(member.displayName ?: "@${member.handle}", style = MaterialTheme.typography.titleSmall, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (member.founder) {
                    Spacer(Modifier.width(6.dp))
                    FounderBadge(plain = true)
                }
            }
            if (member.displayName != null) Text("@${member.handle}", style = MaterialTheme.typography.labelSmall, color = colors.muted)
        }
    }
}

@Composable
private fun CommunityRow(community: Community) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    Row(
        Modifier.fillMaxWidth().clickable { app.navigator.push(Route.Community(community.slug)) }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CommunityMark(community, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(community.name, style = MaterialTheme.typography.titleSmall, color = colors.ink)
            Text(plural(community.memberCount, "member"), style = MaterialTheme.typography.labelSmall, color = colors.muted)
        }
    }
}

@Composable
fun HashtagScreen(tag: String) {
    val app = LocalApp.current
    val viewModel = viewModel(key = "hashtag-$tag") {
        PostListViewModel(load = { cursor -> app.api.hashtag(tag, cursor) }, changes = app.changes)
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("#$tag", onBack = { app.navigator.pop() })
        PostList(viewModel, empty = "No posts with #$tag yet.")
    }
}

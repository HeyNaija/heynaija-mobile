package ng.heynaija.app.communities

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ng.heynaija.app.AppState
import ng.heynaija.app.LocalApp
import ng.heynaija.app.api.ApiException
import ng.heynaija.app.api.Community
import ng.heynaija.app.api.CommunityRef
import ng.heynaija.app.api.mediaUrl
import ng.heynaija.app.launchCatching
import ng.heynaija.app.nav.Route
import ng.heynaija.app.posts.ListGutter
import ng.heynaija.app.posts.PostList
import ng.heynaija.app.posts.PostListViewModel
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii
import ng.heynaija.app.ui.Card
import ng.heynaija.app.ui.Icons
import ng.heynaija.app.ui.Loading
import ng.heynaija.app.ui.PrimaryButton
import ng.heynaija.app.ui.Problem
import ng.heynaija.app.ui.SecondaryButton
import ng.heynaija.app.ui.TopBar
import ng.heynaija.app.ui.plural

/** A community's photo, or its first letter on the sage wash. */
@Composable
fun CommunityMark(community: Community, size: Dp) {
    val colors = HeyNaija.colors
    Box(Modifier.size(size).clip(Radii.control).background(colors.accentSoft), contentAlignment = Alignment.Center) {
        if (community.photo != null) {
            AsyncImage(mediaUrl(community.photo, "256"), null, Modifier.size(size), contentScale = ContentScale.Crop)
        } else {
            Text(community.name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = colors.accentStrong)
        }
    }
}

/** Joining and leaving, at once on screen and put back if the API says no. */
private fun AppState.membership(community: Community, update: (Community) -> Unit) = requireMember {
    val on = !community.viewerIsMember
    update(community.copy(viewerIsMember = on, memberCount = community.memberCount + if (on) 1 else -1))
    scope.launchCatching(this, if (on) "You could not join ${community.name}." else "You could not leave ${community.name}.") {
        try {
            api.setMembership(community.slug, on)
            say(if (on) "Joined ${community.name}" else "Left ${community.name}")
        } catch (e: Exception) {
            update(community)
            throw e
        }
    }
}

data class CommunitiesState(val communities: List<Community> = emptyList(), val loaded: Boolean = false, val failed: Boolean = false)

class CommunitiesViewModel(private val app: AppState) : ViewModel() {
    private val _state = MutableStateFlow(CommunitiesState())
    val state: StateFlow<CommunitiesState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(failed = false) }
        viewModelScope.launch {
            try {
                _state.value = CommunitiesState(app.api.communities(), loaded = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(failed = true) }
            }
        }
    }

    fun toggle(community: Community) = app.membership(community) { changed ->
        _state.update { s -> s.copy(communities = s.communities.map { if (it.id == changed.id) changed else it }) }
    }
}

@Composable
fun CommunitiesScreen() {
    val app = LocalApp.current
    val viewModel = viewModel(key = "communities") { CommunitiesViewModel(app) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TopBar("Communities")
        when {
            !state.loaded && state.failed -> Problem("Communities could not be loaded. Check your connection and try again.", viewModel::load)
            !state.loaded -> Loading()
            else -> LazyColumn(contentPadding = PaddingValues(ListGutter), verticalArrangement = Arrangement.spacedBy(ListGutter)) {
                item {
                    Text(
                        "Places for a city, a subject or a shared interest. Join the ones you care about and their posts come to your Following feed.",
                        Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = HeyNaija.colors.muted,
                    )
                }
                items(state.communities, key = { it.id }) { community ->
                    CommunityCard(community, onToggle = { viewModel.toggle(community) })
                }
            }
        }
    }
}

@Composable
private fun CommunityCard(community: Community, onToggle: () -> Unit) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    Card(onClick = { app.navigator.push(Route.Community(community.slug)) }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            CommunityMark(community, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(community.name, style = MaterialTheme.typography.titleMedium, color = colors.ink)
                Text(plural(community.memberCount, "member"), style = MaterialTheme.typography.labelSmall, color = colors.muted)
                if (community.description.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(community.description, style = MaterialTheme.typography.bodyMedium, color = colors.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(10.dp))
            JoinButton(community, onToggle)
        }
    }
}

@Composable
private fun JoinButton(community: Community, onToggle: () -> Unit) {
    if (community.viewerIsMember) SecondaryButton("Joined", onToggle) else PrimaryButton("Join", onToggle)
}

data class CommunityState(val community: Community? = null, val failed: Boolean = false, val missing: Boolean = false)

class CommunityViewModel(private val app: AppState, private val slug: String) : ViewModel() {
    private val _state = MutableStateFlow(CommunityState())
    val state: StateFlow<CommunityState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(failed = false) }
        viewModelScope.launch {
            try {
                _state.value = CommunityState(app.api.community(slug))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _state.value = CommunityState(failed = e.status != 404, missing = e.status == 404)
            } catch (e: Exception) {
                _state.value = CommunityState(failed = true)
            }
        }
    }

    fun toggle() {
        val community = _state.value.community ?: return
        app.membership(community) { changed -> _state.update { it.copy(community = changed) } }
    }
}

@Composable
fun CommunityScreen(slug: String) {
    val app = LocalApp.current
    val viewModel = viewModel(key = "community-$slug") { CommunityViewModel(app, slug) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val community = state.community
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(community?.name ?: "Community", onBack = { app.navigator.pop() })
            when {
                state.missing -> Problem("This community does not exist.", onRetry = null)
                community == null && state.failed -> Problem("This community could not be loaded. Check your connection and try again.", viewModel::load)
                community == null -> Loading()
                else -> {
                    val posts = viewModel(key = "community-$slug-posts") {
                        PostListViewModel(load = { cursor -> app.api.communityPosts(slug, cursor) }, changes = app.changes, refreshOnPost = true)
                    }
                    PostList(posts, empty = "Nothing has been posted in ${community.name} yet.", bottomPadding = 72.dp) {
                        item(key = "header") { Header(community, viewModel::toggle) }
                    }
                }
            }
        }
        if (community?.viewerIsMember == true) {
            FloatingActionButton(
                onClick = { app.navigator.push(Route.Compose(community = CommunityRef(community.slug, community.name))) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = HeyNaija.colors.accent,
                contentColor = HeyNaija.colors.accentInk,
                shape = Radii.card,
            ) { Icon(Icons.Pen, "Post in ${community.name}", Modifier.size(22.dp)) }
        }
    }
}

@Composable
private fun Header(community: Community, onToggle: () -> Unit) {
    val colors = HeyNaija.colors
    var rules by remember { mutableStateOf(false) }
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CommunityMark(community, 56.dp)
                Spacer(Modifier.weight(1f))
                JoinButton(community, onToggle)
            }
            Spacer(Modifier.height(12.dp))
            Text(community.name, style = MaterialTheme.typography.headlineMedium, color = colors.ink)
            Text(plural(community.memberCount, "member"), style = MaterialTheme.typography.labelSmall, color = colors.muted)
            if (community.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(community.description, style = MaterialTheme.typography.bodyLarge, color = colors.ink)
            }
            if (community.rules.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    if (rules) "Hide the rules" else "Read the rules",
                    Modifier.clickable { rules = !rules }.padding(vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.accentStrong,
                )
                if (rules) Text(community.rules, style = MaterialTheme.typography.bodyMedium, color = colors.ink)
            }
            if (community.moderators.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Moderated by " + community.moderators.joinToString(", ") { "@$it" },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.muted,
                )
            }
        }
    }
}

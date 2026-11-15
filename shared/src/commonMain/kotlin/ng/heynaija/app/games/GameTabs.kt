package ng.heynaija.app.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import ng.heynaija.app.AppState
import ng.heynaija.app.LocalApp
import ng.heynaija.app.api.FeedPage
import ng.heynaija.app.api.Post
import ng.heynaija.app.nav.Route
import ng.heynaija.app.posts.PostList
import ng.heynaija.app.posts.PostListViewModel
import ng.heynaija.app.theme.HeyNaija

// Every game's page has the same tabs (docs/games/GAMES.md in the web repository):
// playing it, then its leaderboard, its competitions and its posts.

private val TAB_NAMES = mapOf("play" to "Play", "leaderboard" to "Leaderboard", "competitions" to "Competitions", "posts" to "Posts")

/** The tabs under a game's bar: the chosen one in ink over a short accent line, as Home's feeds are. */
@Composable
fun GameTabRow(selected: String, onSelect: (String) -> Unit) {
    val colors = HeyNaija.colors
    Column(Modifier.background(colors.bg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp).selectableGroup()) {
            for ((key, name) in TAB_NAMES) {
                val on = key == selected
                Box(
                    Modifier
                        .weight(1f)
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(key) })
                        .padding(top = 10.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            name,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (on) colors.ink else colors.muted,
                            maxLines = 1,
                        )
                        Box(
                            Modifier
                                .padding(top = 8.dp)
                                .width(32.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (on) colors.accent else colors.bg),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = colors.line)
    }
}

/**
 * A game's posts, newest first: those in its own community, and The Arbiter's
 * results for its recent competitions, wherever they were posted (a community's
 * tournament results go to that community). Older pages are the community's.
 */
suspend fun AppState.gamePosts(game: String, community: String, cursor: String?): FeedPage {
    if (cursor != null) return api.communityPosts(community, cursor)
    return coroutineScope {
        val own = async { api.communityPosts(community) }
        val recent = async { runCatching { api.competitions().recent }.getOrDefault(emptyList()) }
        val ids = recent.await().filter { it.game == game }.mapNotNull { it.resultPostId }.distinct().take(10)
        val results = ids.map { id -> async { runCatching { api.post(id) }.getOrNull() } }.awaitAll().filterNotNull()
        val page = own.await()
        val merged = (page.posts + results.filter { r -> page.posts.none { it.id == r.id } })
            .sortedByDescending { runCatching { Instant.parse(it.createdAt) }.getOrNull() ?: Instant.DISTANT_PAST }
        FeedPage(merged, cursor = page.cursor)
    }
}

/** A game's Posts tab, with the way to its community and to Game Night above the posts. */
@Composable
fun GamePosts(game: String, name: String, community: Pair<String, String>) {
    val app = LocalApp.current
    val viewModel = viewModel(key = "game-posts-$game") {
        PostListViewModel(load = { cursor -> app.gamePosts(game, community.first, cursor) }, changes = app.changes)
    }
    PostList(
        viewModel,
        empty = "No $name posts yet. Game threads and competition results are posted here, as ordinary posts anyone can reply to.",
    ) {
        item(key = "game-posts-header") { CommunityLinks(community) }
    }
}

@Composable
private fun CommunityLinks(community: Pair<String, String>) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
        Text(
            community.second,
            Modifier.clickable { app.navigator.push(Route.Community(community.first)) },
            style = MaterialTheme.typography.labelLarge,
            color = colors.accentStrong,
        )
        Text("  ·  ", style = MaterialTheme.typography.labelLarge, color = colors.muted)
        Text(
            "Game Night",
            Modifier.clickable { app.navigator.push(Route.Community("game-night")) },
            style = MaterialTheme.typography.labelLarge,
            color = colors.accentStrong,
        )
    }
}

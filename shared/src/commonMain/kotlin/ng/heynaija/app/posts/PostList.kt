package ng.heynaija.app.posts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import ng.heynaija.app.LocalApp
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.ui.ElasticPullBar
import ng.heynaija.app.ui.Empty
import ng.heynaija.app.ui.PostCard
import ng.heynaija.app.ui.PostCardSkeleton
import ng.heynaija.app.ui.SecondaryButton
import ng.heynaija.app.ui.rememberNow

/** Ask for the next page this many posts before the end, so scrolling rarely waits. */
private const val PREFETCH = 5

/** The gap between cards and the screen's edge, and between one card and the next. */
val ListGutter = 10.dp

/**
 * A list of posts with pull to refresh and the next page on the way before the
 * end. [header] scrolls with the list; [empty] shows when there are no posts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostList(
    viewModel: PostListViewModel,
    modifier: Modifier = Modifier,
    list: LazyListState = rememberLazyListState(),
    empty: String = "Nothing here yet.",
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    header: LazyListScope.() -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now = rememberNow()
    val navigator = LocalApp.current.navigator

    LaunchedEffect(list, viewModel) {
        snapshotFlow {
            val info = list.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            // The count is part of the key, so a page that leaves us near the end asks again.
            (last >= info.totalItemsCount - PREFETCH) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (nearEnd, _) -> if (nearEnd) viewModel.loadMore() }
    }
    // Pressing the tab again at its top scrolls back to the newest.
    val seen = remember { navigator.reselected }
    LaunchedEffect(navigator.reselected) { if (navigator.reselected > seen) list.animateScrollToItem(0) }

    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = viewModel::refresh,
        state = pullState,
        modifier = modifier.fillMaxSize(),
        indicator = {},
    ) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = ListGutter, end = ListGutter, top = ListGutter, bottom = bottomPadding + 32.dp),
            verticalArrangement = Arrangement.spacedBy(ListGutter),
        ) {
            header()
            if (!state.refreshing && pullState.distanceFraction > 0.05f) {
                item(key = "elastic-pull-bar") {
                    ElasticPullBar(pullState.distanceFraction)
                }
            }
            when {
                !state.loaded && state.failed -> item(key = "problem") {
                    Failed("Posts could not be loaded. Check your connection and try again.", viewModel::retry)
                }
                !state.loaded || state.refreshing -> items(5, key = { "skeleton-$it" }) {
                    PostCardSkeleton()
                }
                else -> {
                    state.pinned?.let { pinned -> item(key = "pinned-${pinned.id}") { PostCard(pinned, now, pinned = true) } }
                    if (state.posts.isEmpty() && state.pinned == null) item(key = "empty") { Empty(empty) }
                    items(state.posts, key = { it.id }, contentType = { "post" }) { PostCard(it, now) }
                    item(key = "footer") { Footer(state, viewModel::retry) }
                }
            }
        }
    }
}

@Composable
private fun Footer(state: PostListState, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        when {
            state.failed -> Failed("More posts could not be loaded. Check your connection and try again.", onRetry)
            state.loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            state.atEnd && state.posts.size > 3 -> Text(
                "That is everything for now.",
                style = MaterialTheme.typography.bodyMedium,
                color = HeyNaija.colors.muted,
            )
        }
    }
}

@Composable
private fun Failed(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = HeyNaija.colors.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        SecondaryButton("Try again", onRetry)
    }
}

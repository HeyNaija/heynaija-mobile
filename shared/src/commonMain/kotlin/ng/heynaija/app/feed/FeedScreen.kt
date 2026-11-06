package ng.heynaija.app.feed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import ng.heynaija.app.resources.Res
import ng.heynaija.app.resources.heynaija_wordmark
import ng.heynaija.app.resources.heynaija_wordmark_dark
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii
import ng.heynaija.app.ui.PostCard
import ng.heynaija.app.ui.rememberNow
import org.jetbrains.compose.resources.painterResource

/** Ask for the next page this many posts before the end, so scrolling rarely waits. */
private const val PREFETCH = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(viewModel: FeedViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = HeyNaija.colors
    val list = rememberLazyListState()
    val now = rememberNow()

    LaunchedEffect(list) {
        snapshotFlow {
            val info = list.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            // The count is part of the key, so a page that leaves us near the end asks again.
            (last >= info.totalItemsCount - PREFETCH) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (nearEnd, _) -> if (nearEnd) viewModel.loadMore() }
    }

    Scaffold(containerColor = colors.bg, topBar = { TopBar() }) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize(),
        ) {
            when {
                !state.loaded && state.failed -> Problem(
                    "Posts could not be loaded. Check your connection and try again.",
                    onRetry = viewModel::retry,
                )
                !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> LazyColumn(
                    state = list,
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 20.dp,
                        bottom = padding.calculateBottomPadding() + 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "intro") { Intro() }
                    if (state.posts.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                "Nothing has been posted yet.",
                                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.muted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    items(state.posts, key = { it.id }) { PostCard(it, now) }
                    item(key = "footer") { Footer(state, onRetry = viewModel::retry) }
                }
            }
        }
    }
}

/** The wordmark on the ground, like the web's top bar. */
@Composable
private fun TopBar() {
    val colors = HeyNaija.colors
    Column(Modifier.background(colors.bg).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            Image(
                painterResource(if (colors.dark) Res.drawable.heynaija_wordmark_dark else Res.drawable.heynaija_wordmark),
                contentDescription = "HeyNaija",
                modifier = Modifier.height(36.dp),
            )
        }
        HorizontalDivider(color = colors.line)
    }
}

/** The home page's heading and its one tab for a visitor (apps/web/app/(main)/page.tsx). */
@Composable
private fun Intro() {
    val colors = HeyNaija.colors
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text("MADE FOR US", style = MaterialTheme.typography.labelMedium, color = colors.accentStrong)
        Spacer(Modifier.height(6.dp))
        Text(
            buildAnnotatedString {
                append("Your town square")
                withStyle(SpanStyle(color = colors.accent)) { append(".") }
            },
            style = MaterialTheme.typography.headlineMedium,
            color = colors.ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "A little closer to home. One conversation at a time.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
        )
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            // The underline is as wide as the word.
            Column(Modifier.width(IntrinsicSize.Max)) {
                Text("Latest", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(colors.accent))
            }
            Spacer(Modifier.width(16.dp))
            Text(
                "Newest first, not ranked",
                Modifier.padding(bottom = 10.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colors.muted,
            )
        }
    }
}

@Composable
private fun Footer(state: FeedState, onRetry: () -> Unit) {
    val colors = HeyNaija.colors
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        when {
            state.failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Older posts could not be loaded. Check your connection and try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onRetry, shape = Radii.control) { Text("Try again") }
            }
            state.loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            state.atEnd && state.posts.isNotEmpty() -> Text(
                "That is everything for now.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        }
    }
}

/** A first page that would not load. Scrollable, so pulling down retries too. */
@Composable
private fun Problem(message: String, onRetry: () -> Unit) {
    val colors = HeyNaija.colors
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight).fillMaxWidth().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(message, style = MaterialTheme.typography.bodyLarge, color = colors.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry, shape = Radii.control) { Text("Try again") }
        }
    }
}

package ng.heynaija.app.feed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import ng.heynaija.app.Account
import ng.heynaija.app.LocalApp
import ng.heynaija.app.gists.LiveNow
import ng.heynaija.app.gists.LiveNowViewModel
import ng.heynaija.app.gists.StartGistDialog
import ng.heynaija.app.nav.Route
import ng.heynaija.app.posts.PostList
import ng.heynaija.app.posts.PostListViewModel
import ng.heynaija.app.resources.Res
import ng.heynaija.app.resources.heynaija_wordmark
import ng.heynaija.app.resources.heynaija_wordmark_dark
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii
import ng.heynaija.app.ui.Card
import ng.heynaija.app.ui.Icons
import ng.heynaija.app.ui.PrimaryButton
import ng.heynaija.app.ui.SecondaryButton
import org.jetbrains.compose.resources.painterResource

/** The home feeds, in the web's order (apps/web/lib/home-feed.ts). Following is for members. */
private enum class Feed(val label: String, val note: String) {
    Discover("Discover", "Ranked by conversation, not by ads"),
    Latest("Latest", "Newest first, not ranked"),
    Following("Following", "People and communities you follow"),
}

private const val FEED = "feed"

@Composable
fun HomeScreen() {
    val app = LocalApp.current
    val account by app.account.collectAsStateWithLifecycle()
    val member = account is Account.SignedIn
    val feeds = if (member) Feed.entries else listOf(Feed.Discover, Feed.Latest)
    val lists = remember { mutableMapOf<Feed, LazyListState>() }
    // Gists, for members on their list (ADR 0017): Live now above every feed, and starting one from the bar
    // for those who may host. A member who may only listen (under 18, or no age on record) still gets Live now.
    val gists = (account as? Account.SignedIn)?.me?.has("gists") == true
    val mayHost = (account as? Account.SignedIn)?.me?.mayTakeStage == true
    val liveNow = viewModel(key = "live-now") { LiveNowViewModel(app.api) }
    val live by liveNow.items.collectAsStateWithLifecycle()
    if (gists) {
        LifecycleResumeEffect(liveNow) {
            liveNow.start()
            onPauseOrDispose { liveNow.stop() }
        }
    }
    var starting by remember { mutableStateOf(false) }
    if (starting) StartGistDialog(onDismiss = { starting = false })

    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            WordmarkBar(onStartGist = if (mayHost) ({ starting = true }) else null)
            key(member) {
                // The tab last chosen, else Discover, as on the web.
                val initialPage = remember {
                    val saved = app.platform.settings.get(FEED)
                    val index = feeds.indexOfFirst { it.name == saved }
                    if (index >= 0) index else 0
                }
                val pagerState = rememberPagerState(initialPage = initialPage) { feeds.size }

                LaunchedEffect(pagerState, feeds) {
                    snapshotFlow { pagerState.settledPage }
                        .drop(1) // not the page it opened on
                        .collect { page -> feeds.getOrNull(page)?.let { app.platform.settings.set(FEED, it.name) } }
                }

                Tabs(feeds, pagerState) { targetPage ->
                    scope.launch {
                        if (abs(targetPage - pagerState.currentPage) > 1) pagerState.scrollToPage(targetPage)
                        else pagerState.animateScrollToPage(targetPage)
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) { page ->
                    val feed = feeds[page]
                    val viewModel = viewModel(key = "home-${feed.name}-$member") {
                        PostListViewModel(
                            load = { cursor ->
                                when (feed) {
                                    Feed.Discover -> app.api.discover(cursor)
                                    Feed.Latest -> app.api.latest(cursor)
                                    Feed.Following -> app.api.following(cursor)
                                }
                            },
                            changes = app.changes,
                            refreshOnPost = feed != Feed.Discover,
                        )
                    }
                    PostList(
                        viewModel,
                        list = lists.getOrPut(feed) { LazyListState() },
                        empty = when (feed) {
                            Feed.Following -> "Follow people and join communities, and their posts show here."
                            else -> "Nothing has been posted yet."
                        },
                        bottomPadding = 72.dp,
                    ) {
                        if (!member) item(key = "visitor") { VisitorCard() }
                        if (gists && live.isNotEmpty()) item(key = "live-now") { LiveNow(live) }
                    }
                }
            }
        }
        if (member) {
            FloatingActionButton(
                onClick = { app.navigator.push(Route.Compose()) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = HeyNaija.colors.accent,
                contentColor = HeyNaija.colors.accentInk,
                shape = Radii.card,
            ) { Icon(Icons.Pen, "New post", Modifier.size(22.dp)) }
        }
    }
}

/** The wordmark on the ground, like the web's top bar; starting a Gist and settings sit on the right for members. */
@Composable
private fun WordmarkBar(onStartGist: (() -> Unit)?) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    Column(Modifier.background(colors.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(if (colors.dark) Res.drawable.heynaija_wordmark_dark else Res.drawable.heynaija_wordmark),
                contentDescription = "HeyNaija",
                modifier = Modifier.height(30.dp),
            )
            Spacer(Modifier.weight(1f))
            if (onStartGist != null) {
                Box(Modifier.size(44.dp).clickable(onClick = onStartGist), contentAlignment = Alignment.Center) {
                    Icon(Icons.Live, "Start a Gist", Modifier.size(22.dp), tint = colors.muted)
                }
            }
            // Visitors on a debug build need it too, to pick the server.
            if (app.me != null || app.platform.debug) {
                Box(Modifier.size(44.dp).clickable { app.navigator.push(Route.Settings) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Gear, "Settings", Modifier.size(22.dp), tint = colors.muted)
                }
            }
        }
    }
}

@Composable
private fun Tabs(
    feeds: List<Feed>,
    pagerState: PagerState,
    onSelect: (Int) -> Unit,
) {
    val colors = HeyNaija.colors

    Column(Modifier.background(colors.bg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .selectableGroup()
                .drawBehind {
                    val slot = size.width / feeds.size
                    val at = pagerState.currentPage + pagerState.currentPageOffsetFraction
                    val w = 36.dp.toPx()
                    val h = 3.dp.toPx()
                    val centre = if (layoutDirection == LayoutDirection.Rtl) {
                        size.width - slot * (at + 0.5f)
                    } else {
                        slot * (at + 0.5f)
                    }
                    drawRoundRect(
                        colors.accent,
                        Offset(centre - w / 2, size.height - h),
                        Size(w, h),
                        CornerRadius(1.5.dp.toPx()),
                    )
                },
        ) {
            feeds.forEachIndexed { index, feed ->
                val selected = pagerState.targetPage == index
                Column(
                    Modifier
                        .weight(1f)
                        .selectable(selected = selected, role = Role.Tab) { onSelect(index) }
                        .padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        feed.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) colors.ink else colors.muted,
                    )
                    Spacer(Modifier.height(13.dp))
                }
            }
        }
        HorizontalDivider(color = colors.line)
    }
}

/** For someone not signed in: what this is, and the two ways in. */
@Composable
private fun VisitorCard() {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("MADE FOR US", style = MaterialTheme.typography.labelMedium, color = colors.accentStrong)
            Spacer(Modifier.height(6.dp))
            Text("Your town square.", style = MaterialTheme.typography.headlineMedium, color = colors.ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "HeyNaija is open by invite while it grows. Sign in, or join with the code someone sent you.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
            Spacer(Modifier.height(14.dp))
            Row {
                PrimaryButton("Join with an invite", { app.navigator.push(Route.SignUp) }, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                SecondaryButton("Sign in", { app.navigator.push(Route.SignIn) }, Modifier.weight(1f))
            }
        }
    }
}

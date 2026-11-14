package ng.heynaija.app.notifications

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ng.heynaija.app.AppState
import ng.heynaija.app.LocalApp
import ng.heynaija.app.api.Notification
import ng.heynaija.app.api.NotificationPage
import ng.heynaija.app.nav.Route
import ng.heynaija.app.platform.PushEvents
import ng.heynaija.app.posts.ListGutter
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii
import ng.heynaija.app.ui.Avatar
import ng.heynaija.app.ui.ElasticPullBar
import ng.heynaija.app.ui.Empty
import ng.heynaija.app.ui.FOUNDER_INLINE
import ng.heynaija.app.ui.Icons
import ng.heynaija.app.ui.NotificationRowSkeleton
import ng.heynaija.app.ui.NotificationsSkeletonList
import ng.heynaija.app.ui.Problem
import ng.heynaija.app.ui.TopBar
import ng.heynaija.app.ui.founderInlineContent
import ng.heynaija.app.ui.rememberNow
import ng.heynaija.app.ui.timeAgo
import ng.heynaija.app.profile.MeScreen

data class NotificationsState(
    val items: List<Notification> = emptyList(),
    val cursor: String? = null,
    val loaded: Boolean = false,
    /** The next, older page is on its way. */
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val failed: Boolean = false,
)

/**
 * [newest], the first page, put above the notifications already shown, so the list
 * keeps its place and what was loaded below it. The server marks read everything
 * up to a time, so when the oldest on the new page is read, so is everything below.
 * With nothing in common (more new ones than a page holds), the list starts again.
 */
internal fun withNewest(shown: NotificationsState, newest: NotificationPage): NotificationsState {
    val fresh = newest.notifications
    val ids = fresh.mapTo(HashSet()) { it.id }
    val below = shown.items.drop(shown.items.indexOfLast { it.id in ids } + 1)
    if (below.isEmpty() || below.size == shown.items.size) return shown.copy(items = fresh, cursor = newest.cursor)
    val read = fresh.last().read
    return shown.copy(items = fresh + below.map { if (read) it.copy(read = true) else it })
}

class NotificationsViewModel(private val app: AppState) : ViewModel() {
    private val _state = MutableStateFlow(NotificationsState())
    val state: StateFlow<NotificationsState> = _state.asStateFlow()

    private var fetching: Job? = null

    /**
     * Fetches the newest while the list is on screen, and marks them read. A later
     * call starts again, so one made as a push arrives brings that notification.
     * [pulled]: the member pulled the list down.
     */
    fun refresh(pulled: Boolean = false) {
        fetching?.cancel()
        _state.update { it.copy(refreshing = pulled || it.refreshing, failed = false) }
        fetching = viewModelScope.launch {
            if (pulled) {
                delay(3800)
            }
            val page = try {
                app.api.notifications()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(refreshing = false, failed = true) }
                return@launch
            }
            _state.update { withNewest(it, page).copy(loaded = true, refreshing = false) }
            // Seen: everything up to the newest is read, and the tab's count goes. The
            // list keeps showing which were new until the member comes back to it.
            val newest = page.notifications.firstOrNull()
            if (newest != null && page.notifications.any { !it.read }) {
                try {
                    app.api.markRead(newest.id)
                    app.updateMe { it.copy(unreadNotifications = 0) }
                    // Any that came in meanwhile, and no older answer bringing the old count back.
                    app.refreshMe()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Marked next time the list is shown.
                }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (!s.loaded || s.loadingMore || s.cursor == null || s.failed) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val page = app.api.notifications(s.cursor)
                _state.update {
                    // The list started again from the newest meanwhile: this page no longer follows it.
                    if (it.cursor != s.cursor) it.copy(loadingMore = false)
                    else it.copy(items = (it.items + page.notifications).distinctBy(Notification::id), cursor = page.cursor, loadingMore = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false, failed = true) }
            }
        }
    }
}

private val REASONS = mapOf(
    "spam" to "spam",
    "harassment" to "harassment",
    "hate" to "hate speech",
    "scam" to "being a scam",
    "illegal" to "illegal content",
    "needs_label" to "sensitive content that was not marked",
)

/** What happened, in the web's words (apps/web/app/(main)/notifications/page.tsx). */
private fun describe(n: Notification): Pair<ImageVector, String> {
    val gist = n.post?.gistId != null
    return when (n.kind) {
        "reply" -> Icons.Bubble to if (gist) "replied in a Gist" else "replied to your post"
        "like" -> Icons.HeartFilled to "liked your post"
        "repost" -> Icons.Repost to "reposted your post"
        "quote" -> Icons.Quote to "quoted your post"
        "mention" -> Icons.Mention to if (gist) "mentioned you in a Gist" else "mentioned you"
        "follow" -> Icons.Follow to "followed you"
        "story_update" -> Icons.Story to "added to their story"
        "community_announcement" -> Icons.Megaphone to "posted an announcement in ${n.community?.name ?: "a community"}"
        "post_removed" -> Icons.Shield to "A moderator removed your post for ${REASONS[n.detail] ?: "breaking the community rules"}."
        "post_restored" -> Icons.Shield to "A moderator restored your post."
        "post_labelled" -> Icons.Shield to "A moderator marked your post as sensitive. People under 18 won’t see it, and its pictures and video show behind a warning."
        "report_resolved" -> {
            val about = when {
                n.detail?.startsWith("gist_") == true -> "a Gist"
                n.subjectHandle != null -> "@${n.subjectHandle}"
                n.community != null -> "the photo of ${n.community.name}"
                else -> "a post"
            }
            Icons.Flag to if (n.detail == "actioned" || n.detail == "gist_actioned") "Thanks for your report about $about. A moderator took action."
            else "Thanks for your report about $about. A moderator looked and found no rule was broken."
        }
        "avatar_removed" -> Icons.Shield to "A moderator removed your profile photo because it broke the community rules. You can add a different one."
        "profile_cleared" -> Icons.Shield to "A moderator cleared your name and bio because they broke the community rules. You can write new ones."
        "moderator_granted" -> Icons.Shield to "You are now a moderator of ${n.community?.name ?: "a community"}. Reports about its posts will reach you."
        "moderator_revoked" -> Icons.Shield to "You are no longer a moderator of ${n.community?.name ?: "a community"}."
        "account_reinstated" -> Icons.Shield to "Your account is active again. A moderator lifted the suspension."
        "idea_status" -> Icons.Lightbulb to "An idea you backed has news."
        else -> Icons.Bell to "Something happened."
    }
}

/** Where pressing it goes (apps/web/lib/notification-target.ts): a reply in a Gist opens in its Gist. */
private fun target(n: Notification): Route? = when (n.kind) {
    "reply", "like", "repost", "quote", "mention", "post_restored", "post_labelled", "story_update", "community_announcement" -> n.post?.let { Route.Thread(it.id, gist = it.gistId) }
    "follow" -> n.actor?.let { Route.Profile(it.handle) }
    "avatar_removed", "profile_cleared" -> Route.EditProfile
    "moderator_revoked" -> n.community?.let { Route.Community(it.slug) }
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen() {
    val app = LocalApp.current
    if (app.me == null) {
        MeScreen()
        return
    }
    val viewModel = viewModel(key = "notifications") { NotificationsViewModel(app) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val push by app.push.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val now = rememberNow()
    // While the list is on screen with the app in front: the newest on arriving and on
    // coming back to the app, then again as each push comes in.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh()
            PushEvents.arrived.collect { viewModel.refresh() }
        }
    }
    // New ones come in above the first shown. At the top the list shows them, rather
    // than keeping its place below them as it does further down.
    val newest = state.items.firstOrNull()?.id
    remember(newest) { Snapshot.withoutReadObservation { if (list.firstVisibleItemIndex == 0) list.requestScrollToItem(0) } }
    LaunchedEffect(list) {
        snapshotFlow { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 5 }
            .distinctUntilChanged()
            .collect { if (it) viewModel.loadMore() }
    }
    // Pressing the tab again scrolls back to the newest. A press made before the list was
    // shown (on another tab, say) does not, so coming back keeps the place.
    val seen = remember { app.navigator.reselected }
    LaunchedEffect(app.navigator.reselected) { if (app.navigator.reselected > seen) list.animateScrollToItem(0) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Notifications", actions = {
            IconButton(onClick = { app.navigator.push(Route.NotificationSettings) }) {
                Icon(Icons.Gear, "Notification settings", Modifier.size(22.dp), tint = HeyNaija.colors.muted)
            }
        })
        when {
            !state.loaded && state.failed -> Problem("Notifications could not be loaded. Check your connection and try again.", { viewModel.refresh(pulled = true) })
            !state.loaded -> NotificationsSkeletonList()
            else -> {
                val pullState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = { viewModel.refresh(pulled = true) },
                    state = pullState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {},
                ) {
                    LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(ListGutter),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (push.available && !push.on && !push.off && !push.offerDismissed) item(key = "push") { PushOffer(app) }
                        if (!state.refreshing && pullState.distanceFraction > 0.05f) {
                            item(key = "elastic-pull-bar") {
                                ElasticPullBar(pullState.distanceFraction)
                            }
                        }
                        if (state.refreshing) {
                            items(6, key = { "refreshing-skeleton-$it" }) {
                                NotificationRowSkeleton()
                            }
                        } else {
                            if (state.items.isEmpty()) item { Empty("When people reply to you, like your posts or follow you, it shows here.") }
                            items(state.items, key = { it.id }) { NotificationRow(it, now) }
                            if (state.loadingMore && state.items.isNotEmpty()) item {
                                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: Notification, now: Instant) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val (icon, words) = remember(n) { describe(n) }
    val go = target(n)
    val ago = remember(n.createdAt, now) { timeAgo(Instant.parse(n.createdAt), now) }
    // As on the web: who and what beside the avatar, then the quoted post and where
    // it leads across the whole card, rather than in a column beside the avatar.
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Radii.card)
            .background(if (n.read) colors.surface else colors.accentSoft)
            .border(1.dp, if (n.read) colors.line else colors.accent.copy(alpha = 0.3f), Radii.card)
            .let { if (go != null) it.clickable { app.navigator.push(go) } else it }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val actor = n.actor
        Row(verticalAlignment = Alignment.Top) {
            if (actor != null) {
                // What happened rides on the corner of who did it, as on the web.
                Box {
                    Avatar(actor.handle, actor.avatar, 36.dp, Modifier.clickable { app.navigator.push(Route.Profile(actor.handle)) })
                    Box(
                        Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(20.dp).clip(CircleShape).background(colors.surface).padding(2.dp),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, null, Modifier.size(14.dp), tint = colors.accentStrong) }
                }
            } else {
                Box(Modifier.size(36.dp).clip(CircleShape).background(colors.panel), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(18.dp), tint = colors.accentStrong)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    buildAnnotatedString {
                        if (actor != null) {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(actor.displayName ?: "@${actor.handle}") }
                            if (actor.founder) {
                                append(" ")
                                appendInlineContent(FOUNDER_INLINE)
                            }
                            append(" ")
                        }
                        append(words)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink,
                    inlineContent = founderInlineContent,
                )
                Text(ago, Modifier.padding(top = 1.dp), style = MaterialTheme.typography.labelSmall, color = colors.muted)
            }
            if (!n.read) {
                Box(Modifier.padding(start = 8.dp, top = 8.dp).size(8.dp).clip(CircleShape).background(colors.accent))
            }
        }
        n.post?.excerpt?.takeIf { it.isNotBlank() && n.kind in setOf("reply", "like", "repost", "quote", "mention", "story_update", "community_announcement") }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (n.read) colors.muted else colors.brandDeep,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (go != null) {
            Text(
                "${destination(n)} →",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.accentStrong,
            )
        }
    }
}

/** Where the card leads, in the web's words. */
private fun destination(n: Notification): String = when (n.kind) {
    "reply" -> "View reply"
    "quote" -> "View quote"
    "mention" -> "View mention"
    "follow" -> "View profile"
    "story_update" -> "View update"
    "community_announcement" -> "View announcement"
    else -> if (n.post != null) "View post" else "View details"
}

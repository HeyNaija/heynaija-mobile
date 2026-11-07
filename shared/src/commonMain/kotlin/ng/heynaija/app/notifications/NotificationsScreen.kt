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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ng.heynaija.app.AppState
import ng.heynaija.app.LocalApp
import ng.heynaija.app.api.Notification
import ng.heynaija.app.nav.Route
import ng.heynaija.app.posts.ListGutter
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii
import ng.heynaija.app.ui.Avatar
import ng.heynaija.app.ui.Empty
import ng.heynaija.app.ui.FOUNDER_INLINE
import ng.heynaija.app.ui.Icons
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
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val failed: Boolean = false,
)

class NotificationsViewModel(private val app: AppState) : ViewModel() {
    private val _state = MutableStateFlow(NotificationsState())
    val state: StateFlow<NotificationsState> = _state.asStateFlow()

    init { load(fresh = true) }

    fun refresh() = load(fresh = true, pulled = true)

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.cursor == null || s.failed) return
        load(fresh = false)
    }

    fun load(fresh: Boolean, pulled: Boolean = false) {
        val cursor = if (fresh) null else _state.value.cursor
        _state.update { it.copy(loading = !pulled, refreshing = pulled, failed = false) }
        viewModelScope.launch {
            try {
                val page = app.api.notifications(cursor)
                _state.update {
                    NotificationsState(
                        items = if (fresh) page.notifications else (it.items + page.notifications).distinctBy(Notification::id),
                        cursor = page.cursor,
                        loaded = true,
                    )
                }
                // Seen: everything up to the newest is read, and the tab's count goes.
                if (fresh) page.notifications.firstOrNull()?.let { newest ->
                    if (!newest.read || page.notifications.any { !it.read }) {
                        app.api.markRead(newest.id)
                        app.updateMe { it.copy(unreadNotifications = 0) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, refreshing = false, failed = true) }
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
    "reply", "like", "repost", "quote", "mention", "post_restored", "post_labelled" -> n.post?.let { Route.Thread(it.id, gist = it.gistId) }
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
    LaunchedEffect(list) {
        snapshotFlow { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 5 }
            .distinctUntilChanged()
            .collect { if (it) viewModel.loadMore() }
    }
    LaunchedEffect(app.navigator.reselected) { if (app.navigator.reselected > 0) list.animateScrollToItem(0) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Notifications", actions = {
            IconButton(onClick = { app.navigator.push(Route.NotificationSettings) }) {
                Icon(Icons.Gear, "Notification settings", Modifier.size(22.dp), tint = HeyNaija.colors.muted)
            }
        })
        when {
            !state.loaded && state.failed -> Problem("Notifications could not be loaded. Check your connection and try again.", { viewModel.load(true) })
            !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> PullToRefreshBox(state.refreshing, viewModel::refresh, Modifier.fillMaxSize()) {
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(ListGutter),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (push.available && !push.on && !push.off && !push.offerDismissed) item(key = "push") { PushOffer(app) }
                    if (state.items.isEmpty()) item { Empty("When people reply to you, like your posts or follow you, it shows here.") }
                    items(state.items, key = { it.id }) { NotificationRow(it, now) }
                    if (state.loading && state.items.isNotEmpty()) item {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
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
        n.post?.excerpt?.takeIf { it.isNotBlank() && n.kind in setOf("reply", "like", "repost", "quote", "mention") }?.let {
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
    else -> if (n.post != null) "View post" else "View details"
}

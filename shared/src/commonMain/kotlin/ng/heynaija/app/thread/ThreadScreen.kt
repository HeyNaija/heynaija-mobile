package ng.heynaija.app.thread

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ng.heynaija.app.LocalApp
import ng.heynaija.app.PostChange
import ng.heynaija.app.api.ApiException
import ng.heynaija.app.api.HeyNaijaApi
import ng.heynaija.app.api.Post
import ng.heynaija.app.api.PostStory
import ng.heynaija.app.nav.Route
import ng.heynaija.app.posts.ListGutter
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii
import ng.heynaija.app.ui.Avatar
import ng.heynaija.app.ui.SpineButton
import ng.heynaija.app.ui.UpdatePrompt
import ng.heynaija.app.ui.SpineItem
import ng.heynaija.app.ui.StoryBar
import ng.heynaija.app.ui.StoryStart
import ng.heynaija.app.ui.Empty
import ng.heynaija.app.ui.GistReplyContext
import ng.heynaija.app.ui.Icons
import ng.heynaija.app.ui.PostCard
import ng.heynaija.app.ui.Problem
import ng.heynaija.app.ui.SecondaryButton
import ng.heynaija.app.ui.TopBar
import ng.heynaija.app.ui.rememberNow

data class ThreadState(
    val ancestors: List<Post> = emptyList(),
    val post: Post? = null,
    val replies: List<Post> = emptyList(),
    val cursor: String? = null,
    val loaded: Boolean = false,
    val loadingMore: Boolean = false,
    val failed: Boolean = false,
    /** The post is gone, or the reader may not see it. */
    val missing: Boolean = false,
    /** A reply in a Gist: whether the reader hosts it, and may hide replies. */
    val viewerIsHost: Boolean = false,
    /** On a story's first post: its updates shown, oldest first (the newest few until the rest are unfolded). */
    val updates: List<Post> = emptyList(),
    val unfolding: Boolean = false,
)

/** A post's thread, or with [gist] a reply in that Gist and the replies under it (the Gist API sends no ancestors). */
class ThreadViewModel(
    private val api: HeyNaijaApi,
    private val id: String,
    first: Post?,
    changes: Flow<PostChange>,
    private val gist: String? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(ThreadState(post = first))
    val state: StateFlow<ThreadState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            changes.collect { change ->
                when (change) {
                    is PostChange.Updated -> _state.update { s ->
                        fun Post.updated() = if (this.id == change.post.id) change.post else this
                        s.copy(ancestors = s.ancestors.map { it.updated() }, post = s.post?.updated(), replies = s.replies.map { it.updated() }, updates = s.updates.map { it.updated() })
                    }
                    is PostChange.Deleted -> _state.update { s ->
                        when {
                            s.post?.id == change.id -> s.copy(post = s.post.copy(deleted = true))
                            s.replies.any { it.id == change.id } -> s.copy(
                                replies = s.replies.filterNot { it.id == change.id },
                                post = s.post?.let { it.copy(replyCount = (it.replyCount - 1).coerceAtLeast(0)) },
                            )
                            else -> s
                        }
                    }
                    // A reply was posted, here or under one of these replies: show it.
                    is PostChange.Created -> load()
                }
            }
        }
    }

    fun load() {
        _state.update { it.copy(failed = false) }
        viewModelScope.launch {
            try {
                _state.value = if (gist != null) {
                    val post = api.gistReply(gist, id)
                    val page = api.gistReplyBranch(gist, id)
                    ThreadState(post = post, replies = page.replies, cursor = page.cursor, loaded = true, viewerIsHost = page.viewerIsHost)
                } else {
                    val page = api.thread(id)
                    ThreadState(page.ancestors, page.post, page.replies, page.replyCursor, loaded = true, updates = page.updates)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _state.update { it.copy(failed = e.status != 404, missing = e.status == 404, loaded = it.post != null && e.status != 404) }
            } catch (e: Exception) {
                _state.update { it.copy(failed = true) }
            }
        }
    }

    /** Every update of the story, oldest first, in place of the newest few. */
    fun unfoldUpdates() {
        if (_state.value.unfolding) return
        _state.update { it.copy(unfolding = true) }
        viewModelScope.launch {
            try {
                val all = mutableListOf<Post>()
                var cursor: String? = null
                do {
                    val page = api.storyUpdates(id, cursor)
                    all += page.posts
                    cursor = page.cursor
                } while (cursor != null && all.size < 1000)
                _state.update { it.copy(updates = all, unfolding = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(unfolding = false) }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        val cursor = s.cursor ?: return
        if (s.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val page = if (gist != null) api.gistReplyBranch(gist, id, cursor) else api.replies(id, cursor)
                _state.update { it.copy(replies = (it.replies + page.replies).distinctBy(Post::id), cursor = page.cursor, loadingMore = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }
}

/** The connector between a post and the one it answers: in the gap, never across a card. */
@Composable
private fun Connector() {
    Box(Modifier.fillMaxWidth().height(ListGutter)) {
        Box(Modifier.padding(start = 33.dp).width(2.dp).height(ListGutter).background(HeyNaija.colors.lineStrong))
    }
}

@Composable
fun ThreadScreen(id: String, first: Post?, gist: String? = null) {
    val app = LocalApp.current
    val viewModel = viewModel(key = "thread-$id") { ThreadViewModel(app.api, id, first, app.changes, gist) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now = rememberNow()
    val inGist = gist?.let { GistReplyContext(it, state.viewerIsHost) }

    Column(Modifier.fillMaxSize()) {
        TopBar(if (gist != null || state.post?.replyParentId != null) "Reply" else "Post", onBack = { app.navigator.pop() })
        val post = state.post
        when {
            state.missing -> Problem("This post is not available. It may have been deleted.", onRetry = null)
            post == null && state.failed -> Problem("This post could not be loaded. Check your connection and try again.", viewModel::load)
            post == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = ListGutter, end = ListGutter, top = ListGutter, bottom = 48.dp),
            ) {
                items(state.ancestors, key = { "a-${it.id}" }) { ancestor ->
                    Column {
                        PostCard(ancestor, now, compact = true)
                        Connector()
                    }
                }
                if (gist != null) item(key = "gist") { InGist(gist) }
                val story = post.story
                // An update on its own page: the story it belongs to, above it (web ADR 0024).
                if (story != null && story.number > 0) item(key = "story-start") { UpdateOf(story) }
                item(key = "post-${post.id}") { PostCard(post, now, focused = true, gist = inGist) }
                // A story's first post: its updates under it, joined by the spine.
                if (story != null && story.number == 0) {
                    val author = app.me?.id == post.authorId
                    val folded = story.updates - state.updates.size
                    item(key = "story-bar") { StoryBar(post) }
                    if (folded > 0) {
                        item(key = "story-fold") {
                            SpineItem(last = false) {
                                SpineButton(if (folded == 1) "Update 1" else "Updates 1 to $folded", Icons.Chevron, accent = false, onClick = viewModel::unfoldUpdates)
                            }
                        }
                    }
                    itemsIndexed(state.updates, key = { _, update -> "u-${update.id}" }) { index, update ->
                        // The curve meets the card at its avatar: 10dp inset plus half a 32dp avatar.
                        SpineItem(last = index == state.updates.lastIndex && !author, curveAt = 26.dp) { PostCard(update, now, compact = true) }
                    }
                    if (author) {
                        // The box the next update is written in, as Reply to is under a post; the curve meets its avatar.
                        item(key = "story-add") {
                            SpineItem(last = true, curveAt = 28.dp) { UpdatePrompt(post) }
                        }
                    }
                }
                if (app.me != null && post.viewerCanReply && !post.deleted && !post.unavailable) {
                    item(key = "reply") { ReplyPrompt(post, gist) }
                }
                if (state.loaded && state.replies.isEmpty()) {
                    item(key = "none") { Empty(if (post.viewerCanReply) "No replies yet." else "No replies.") }
                }
                if (state.replies.isNotEmpty()) item(key = "gap") { Spacer(Modifier.height(ListGutter)) }
                items(state.replies, key = { "r-${it.id}" }) { reply ->
                    Column(Modifier.padding(bottom = 8.dp)) { PostCard(reply, now, compact = true, gist = inGist) }
                }
                if (!state.loaded && !state.failed) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                    }
                }
                if (state.cursor != null) {
                    item(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            if (state.loadingMore) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            else SecondaryButton("More replies", viewModel::loadMore)
                        }
                    }
                }
            }
        }
    }
}

/** Above an update on its own page: the story's first post, and its place in the story. */
@Composable
private fun UpdateOf(story: PostStory) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    Column(Modifier.padding(bottom = ListGutter)) {
        StoryStart(story)
        Row(Modifier.padding(top = 6.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Update ${story.number} of ${story.updates} · ", style = MaterialTheme.typography.labelMedium, color = colors.muted)
            Text(
                "Read the whole story",
                Modifier.clickable { app.navigator.push(Route.Thread(story.id)) },
                style = MaterialTheme.typography.labelMedium,
                color = colors.accentStrong,
            )
        }
    }
}

/**
 * Over a reply in a Gist: the way to the Gist. Back to it when it is the screen
 * underneath, so the member never has the same room open twice.
 */
@Composable
private fun InGist(gist: String) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val below = app.navigator.stack.dropLast(1).lastOrNull()?.route
    Row(
        Modifier
            .padding(bottom = ListGutter)
            .fillMaxWidth()
            .clip(Radii.card)
            .clickable { if (below == Route.Gist(gist)) app.navigator.pop() else app.navigator.push(Route.Gist(gist)) }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Live, null, Modifier.size(18.dp), tint = colors.accentStrong)
        Text("All replies to this Gist", style = MaterialTheme.typography.labelLarge, color = colors.accentStrong)
    }
}

/** "Post your reply", with the member's face: opens the composer. */
@Composable
private fun ReplyPrompt(post: Post, gist: String?) {
    val app = LocalApp.current
    val me = app.me ?: return
    val colors = HeyNaija.colors
    Row(
        Modifier
            .padding(top = ListGutter)
            .fillMaxWidth()
            .background(colors.surface, Radii.card)
            .border(1.dp, colors.line, Radii.card)
            .clickable { app.reply(post, gist) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(me.handle, me.avatar, 32.dp)
        Text("Reply to @${post.authorHandle}", style = MaterialTheme.typography.bodyLarge, color = colors.faint)
    }
}

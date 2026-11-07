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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import ng.heynaija.app.nav.Route
import ng.heynaija.app.posts.ListGutter
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii
import ng.heynaija.app.ui.Avatar
import ng.heynaija.app.ui.Empty
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
)

class ThreadViewModel(private val api: HeyNaijaApi, private val id: String, first: Post?, changes: Flow<PostChange>) : ViewModel() {
    private val _state = MutableStateFlow(ThreadState(post = first))
    val state: StateFlow<ThreadState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            changes.collect { change ->
                when (change) {
                    is PostChange.Updated -> _state.update { s ->
                        fun Post.updated() = if (this.id == change.post.id) change.post else this
                        s.copy(ancestors = s.ancestors.map { it.updated() }, post = s.post?.updated(), replies = s.replies.map { it.updated() })
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
                val page = api.thread(id)
                _state.value = ThreadState(page.ancestors, page.post, page.replies, page.replyCursor, loaded = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _state.update { it.copy(failed = e.status != 404, missing = e.status == 404, loaded = it.post != null && e.status != 404) }
            } catch (e: Exception) {
                _state.update { it.copy(failed = true) }
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
                val page = api.replies(id, cursor)
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
fun ThreadScreen(id: String, first: Post?) {
    val app = LocalApp.current
    val viewModel = viewModel(key = "thread-$id") { ThreadViewModel(app.api, id, first, app.changes) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now = rememberNow()

    Column(Modifier.fillMaxSize()) {
        TopBar(if (state.post?.replyParentId != null) "Reply" else "Post", onBack = { app.navigator.pop() })
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
                item(key = "post-${post.id}") { PostCard(post, now, focused = true) }
                if (app.me != null && post.viewerCanReply && !post.deleted) {
                    item(key = "reply") { ReplyPrompt(post) }
                }
                if (state.loaded && state.replies.isEmpty()) {
                    item(key = "none") { Empty(if (post.viewerCanReply) "No replies yet." else "No replies.") }
                }
                if (state.replies.isNotEmpty()) item(key = "gap") { Spacer(Modifier.height(ListGutter)) }
                items(state.replies, key = { "r-${it.id}" }) { reply ->
                    Column(Modifier.padding(bottom = 8.dp)) { PostCard(reply, now, compact = true) }
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

/** "Post your reply", with the member's face: opens the composer. */
@Composable
private fun ReplyPrompt(post: Post) {
    val app = LocalApp.current
    val me = app.me ?: return
    val colors = HeyNaija.colors
    Row(
        Modifier
            .padding(top = ListGutter)
            .fillMaxWidth()
            .background(colors.surface, Radii.card)
            .border(1.dp, colors.line, Radii.card)
            .clickable { app.navigator.push(Route.Compose(replyTo = post)) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(me.handle, me.avatar, 32.dp)
        Text("Reply to @${post.authorHandle}", style = MaterialTheme.typography.bodyLarge, color = colors.faint)
    }
}

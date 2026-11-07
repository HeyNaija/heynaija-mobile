package ng.heynaija.app.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
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
    /** The answers opened under the replies, by the reply they answer. */
    val branches: Map<String, Branch> = emptyMap(),
) {
    /** Every post on the screen, changed by [change]. */
    fun mapPosts(change: (Post) -> Post) = copy(
        ancestors = ancestors.map(change),
        post = post?.let(change),
        replies = replies.map(change),
        updates = updates.map(change),
        branches = branches.mapValues { (_, branch) -> branch.copy(replies = branch.replies.map(change), recent = branch.recent.map(change)) },
    )

    /**
     * The post with this [id] that replies open under in place, and its level: the
     * focus's own replies and its story's updates are 1, and the replies under them deeper.
     */
    fun reply(id: String): Pair<Post, Int>? {
        val top = replies + updates
        var depth = 1
        var at = id
        // Up through the branches to one of those, within the levels a branch may open.
        while (top.none { it.id == at }) {
            at = branches.entries.firstOrNull { (_, branch) -> branch.has(at) }?.key ?: return null
            if (++depth > THREAD_MAX_DEPTH + THREAD_MAX_GAPS) return null
        }
        return (top + branches.values.flatMap { it.replies + it.recent }).first { it.id == id } to depth
    }
}

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
                    is PostChange.Updated -> _state.update { s -> s.mapPosts { if (it.id == change.post.id) change.post else it } }
                    is PostChange.Deleted -> _state.update { s ->
                        val parent = s.branches.entries.firstOrNull { (_, branch) -> branch.has(change.id) }?.key
                        when {
                            s.post?.id == change.id -> s.copy(post = s.post.copy(deleted = true))
                            s.replies.any { it.id == change.id } -> s.copy(
                                replies = s.replies.filterNot { it.id == change.id },
                                post = s.post?.let { it.copy(replyCount = (it.replyCount - 1).coerceAtLeast(0)) },
                            )
                            parent != null -> {
                                val left = s.branches.getValue(parent).let { branch ->
                                    branch.copy(replies = branch.replies.filterNot { it.id == change.id }, recent = branch.recent.filterNot { it.id == change.id })
                                }
                                // Emptied by the member, it closes rather than say nothing is left.
                                s.copy(branches = s.branches + (parent to if (left.empty) left.copy(open = false) else left))
                                    .mapPosts { if (it.id == parent) it.copy(replyCount = (it.replyCount - 1).coerceAtLeast(0)) else it }
                            }
                            else -> s
                        }
                    }
                    is PostChange.Created -> posted(change.id, change.replyTo)
                }
            }
        }
    }

    fun load() {
        _state.update { it.copy(failed = false) }
        viewModelScope.launch {
            try {
                val loaded = if (gist != null) {
                    val post = api.gistReply(gist, id)
                    val page = api.gistReplyBranch(gist, id)
                    ThreadState(post = post, replies = page.replies, cursor = page.cursor, loaded = true, viewerIsHost = page.viewerIsHost)
                } else {
                    val page = api.thread(id)
                    ThreadState(page.ancestors, page.post, page.replies, page.replyCursor, loaded = true, updates = page.updates)
                }
                // Loading again keeps the branches the reader opened.
                _state.update { loaded.copy(branches = it.branches) }
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

    /** Opens or hides the replies under [reply], loading them the first time. */
    fun toggle(reply: Post) {
        val branch = _state.value.branches[reply.id]
        when {
            branch?.loading == true -> {}
            branch?.open == true && branch.loaded -> setBranch(reply.id) { it.copy(open = false) }
            branch?.loaded == true -> setBranch(reply.id) { it.copy(open = true) }
            else -> loadBranch(reply.id)
        }
    }

    /** The next page of the replies under the reply [id], or the first again after it failed. */
    fun moreReplies(id: String) {
        val branch = _state.value.branches[id] ?: return
        if (branch.loading || (branch.loaded && branch.cursor == null)) return
        loadBranch(id)
    }

    private fun loadBranch(id: String) {
        val cursor = _state.value.branches[id]?.takeIf { it.loaded }?.cursor
        setBranch(id) { it.copy(open = true, loading = true, failed = false) }
        viewModelScope.launch {
            try {
                val page = if (gist != null) api.gistReplyBranch(gist, id, cursor, oldestFirst = true) else api.replies(id, cursor, oldestFirst = true)
                setBranch(id) { it.append(page.replies, page.cursor) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setBranch(id) { it.copy(loading = false, failed = true) }
            }
        }
    }

    /**
     * A post the member just made. A reply to a reply or story update shown here joins
     * its branch, which opens to show it; anything else (a reply to the focus, an
     * update to its story) loads the thread again. A reply at the limit has no branch here: its
     * count goes up, and it shows on that reply's own page.
     */
    private fun posted(id: String, replyTo: String?) {
        val (parent, depth) = replyTo?.let { _state.value.reply(it) } ?: return load()
        if (!opensInPlace(depth, parent.unavailable)) return
        // A reply that had none has nothing left to load, so the new one joins its list
        // rather than waiting behind "Your reply" for the rest.
        val fresh = Branch(loaded = !parent.hasReplies && parent.replyCount == 0)
        viewModelScope.launch {
            try {
                val reply = if (gist != null) api.gistReply(gist, id) else api.post(id)
                setBranch(parent.id, fresh) { it.withPosted(reply, parent.authorId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                load()
            }
        }
    }

    private fun setBranch(id: String, new: Branch = Branch(), change: (Branch) -> Branch) =
        _state.update { it.copy(branches = it.branches + (id to change(it.branches[id] ?: new))) }
}

/** The connector between a post and the one it answers: in the gap, never across a card. */
@Composable
private fun Connector() {
    Box(Modifier.fillMaxWidth().height(ListGutter)) {
        Box(Modifier.padding(start = 33.dp).width(2.dp).height(ListGutter).background(HeyNaija.colors.lineStrong))
    }
}

// Replies opened under a reply indent as a story's updates do (ui/Story.kt): each level
// 26dp in, its line 12dp in, the rows 8dp apart.
private val BRANCH_INDENT = 26.dp
private val BRANCH_LINE = 12.dp
private val REPLY_GAP = 8.dp
/** Where the line curves into a reply's card: 10dp inset plus half its 32dp avatar. */
private val REPLY_AXIS = 26.dp

/**
 * A row under the post in focus or one of its story's updates. A row at level 1 (one
 * of the focus's replies, or an update) is drawn as it is, where its list puts it.
 * Deeper ones indent under the reply they answer, and the line comes down the
 * gutter from it, curving into this row at [curveAt] below its top (its avatar's
 * height for a card; the middle when null). The line starts at the top of the row,
 * in the gap, so the curve branches off one continuous line, and runs on past the
 * row only to what comes next under the same reply. The lines of the levels above
 * run on beside it. "Your reply" has the line straight through it, and past the
 * depth limit one straight line beside the replies shows where their run starts
 * and ends. Every line stays in the gap and the gutter, never across a card.
 */
@Composable
private fun BranchRow(row: ReplyRow, curveAt: Dp?, content: @Composable () -> Unit) {
    if (row.depth == 1) return content()
    val color = HeyNaija.colors.lineStrong
    val level = minOf(row.depth, THREAD_MAX_DEPTH)
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = 2.dp.toPx()
                val gap = REPLY_GAP.toPx()
                val indent = BRANCH_INDENT.toPx()
                fun down(x: Float, from: Float = 0f, to: Float = size.height) = drawLine(color, Offset(x, from), Offset(x, to), stroke)
                row.lines.runs.forEachIndexed { above, on -> if (on) down(above * indent + BRANCH_LINE.toPx()) }
                val x = (level - 2) * indent + BRANCH_LINE.toPx()
                val edge = (level - 1) * indent
                when {
                    row.depth > THREAD_MAX_DEPTH -> down(edge - 6.dp.toPx(), from = if (row.lines.first && row.depth == THREAD_MAX_DEPTH + 1) gap else 0f)
                    row is ReplyRow.Gap -> down(x)
                    else -> {
                        val radius = 8.dp.toPx()
                        val y = gap + (curveAt?.toPx() ?: ((size.height - gap) / 2))
                        // Straight runs are lines, as on the spine, so the line is the same from row to row.
                        down(x, to = if (row.lines.onward) size.height else y - radius)
                        val path = Path().apply {
                            moveTo(x, y - radius)
                            quadraticTo(x, y, x + radius, y)
                            lineTo(edge, y)
                        }
                        drawPath(path, color, style = Stroke(width = stroke))
                    }
                }
            }
            .padding(start = BRANCH_INDENT * (level - 1), top = REPLY_GAP),
    ) { content() }
}

/** One of [replyRows]: a reply with its toggle, More replies, "Your reply", or No available replies. */
@Composable
private fun ReplyRowView(row: ReplyRow, now: Instant, gist: String?, inGist: GistReplyContext?, viewModel: ThreadViewModel) {
    val app = LocalApp.current
    when (row) {
        is ReplyRow.Reply -> BranchRow(row, curveAt = REPLY_AXIS) {
            val reply = row.post
            PostCard(reply, now, compact = true, gist = inGist, toggle = row.toggle?.let { toggle ->
                {
                    BranchToggleButton(toggle) {
                        if (toggle == BranchToggle.Continue) app.navigator.push(Route.Thread(reply.id, reply, gist))
                        else viewModel.toggle(reply)
                    }
                }
            })
        }
        is ReplyRow.More -> BranchRow(row, curveAt = null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    if (row.loading) "Loading replies…" else if (row.failed) "Try again" else "More replies",
                    { viewModel.moreReplies(row.parent) },
                    enabled = !row.loading,
                )
                if (row.failed && !row.loading) {
                    Text("Replies could not be loaded.", style = MaterialTheme.typography.labelMedium, color = HeyNaija.colors.danger)
                }
            }
        }
        is ReplyRow.Gap -> BranchRow(row, curveAt = null) { ReplyGap(rule = !row.lines.first) }
        is ReplyRow.Empty -> BranchRow(row, curveAt = null) {
            Text("No available replies.", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium, color = HeyNaija.colors.muted)
        }
    }
}

/** In a reply's action row, as tall as its buttons: opens or hides the replies under it. */
@Composable
private fun BranchToggleButton(toggle: BranchToggle, onClick: () -> Unit) {
    val label = when (toggle) {
        BranchToggle.View -> "View replies"
        BranchToggle.Hide -> "Hide replies"
        BranchToggle.Loading -> "Loading…"
        BranchToggle.Continue -> "Continue conversation"
    }
    Box(
        Modifier
            .clip(Radii.control)
            .clickable(enabled = toggle != BranchToggle.Loading, onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = HeyNaija.colors.accentStrong, maxLines = 1)
    }
}

/** Before the member's own replies, while some before them are not loaded yet: under a dashed rule after the loaded ones. */
@Composable
private fun ReplyGap(rule: Boolean) {
    val colors = HeyNaija.colors
    Text(
        "Your reply · Some replies are not loaded yet.",
        Modifier
            .fillMaxWidth()
            .drawBehind {
                if (rule) drawLine(colors.lineStrong, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            }
            .padding(top = if (rule) 10.dp else 2.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = colors.muted,
    )
}

@Composable
fun ThreadScreen(id: String, first: Post?, gist: String? = null) {
    val app = LocalApp.current
    val viewModel = viewModel(key = "thread-$id") { ThreadViewModel(app.api, id, first, app.changes, gist) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now = rememberNow()
    val inGist = gist?.let { GistReplyContext(it, state.viewerIsHost) }
    val rows = remember(state.replies, state.branches) { replyRows(state.replies, state.branches) }

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
                        // The curve meets the card at its avatar. An update opens its replies under it, as
                        // a reply does, one level in from the spine, which runs on past them.
                        SpineItem(last = index == state.updates.lastIndex && !author, curveAt = REPLY_AXIS) {
                            val branch = remember(update, state.branches) { replyRows(listOf(update), state.branches) }
                            Column { for (row in branch) key(row.key) { ReplyRowView(row, now, gist, inGist, viewModel) } }
                        }
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
                items(rows, key = { it.key }, contentType = { it::class }) { row ->
                    // The focus's own replies stand apart, a list's gap from each other.
                    Box(Modifier.padding(top = if (row.depth == 1) ListGutter else 0.dp)) { ReplyRowView(row, now, gist, inGist, viewModel) }
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

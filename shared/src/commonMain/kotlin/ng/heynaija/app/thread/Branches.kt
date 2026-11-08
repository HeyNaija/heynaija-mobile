package ng.heynaija.app.thread

import ng.heynaija.app.api.Post

// A post's replies as branches opened in place, as on the web (apps/web/lib/thread-state.ts).

/**
 * Replies opened in place stop here, counting from the post in focus: its replies
 * are level 1. A reply at the limit has no replies drawn under it; "Continue
 * conversation" opens it as the focus instead. Each level indents, so more would
 * only narrow the column.
 */
const val THREAD_MAX_DEPTH = 5

/**
 * A post that is gone has no page to continue to, so one at the limit would strand
 * the replies behind it. These open a few levels further, without indenting.
 */
const val THREAD_MAX_GAPS = 6

/** Whether the replies under a post at [depth] open under it, in place. */
fun opensInPlace(depth: Int, unavailable: Boolean): Boolean =
    depth < THREAD_MAX_DEPTH + if (unavailable) THREAD_MAX_GAPS else 0

/** The answers to one reply, opened under it. They read oldest first, so a back-and-forth reads down the page. */
data class Branch(
    val replies: List<Post> = emptyList(),
    /** Replies the member posted here before the rest were loaded: shown after them, behind "Your reply". */
    val recent: List<Post> = emptyList(),
    val cursor: String? = null,
    val loaded: Boolean = false,
    val open: Boolean = false,
    val loading: Boolean = false,
    val failed: Boolean = false,
) {
    /** Loaded to the end, with nothing in it the reader may see. */
    val empty get() = loaded && cursor == null && replies.isEmpty() && recent.isEmpty()

    fun has(id: String) = replies.any { it.id == id } || recent.any { it.id == id }

    /** A page after the replies already here. Once there are no more, the member's own join them in place. */
    fun append(page: List<Post>, next: String?): Branch {
        val all = (replies + page).distinctBy(Post::id)
        val ids = all.mapTo(HashSet(), Post::id)
        val mine = recent.filterNot { it.id in ids }
        return copy(
            replies = if (next == null) all + mine else all,
            recent = if (next == null) emptyList() else mine,
            cursor = next,
            loaded = true,
            loading = false,
            failed = false,
        )
    }

    /**
     * A reply the member just posted here, under a reply by [parentAuthor]. The API
     * puts that author's replies first, then everyone else's, so it goes where it
     * would load, or behind "Your reply" while replies not loaded yet could come
     * before it. It never stands in for a page that was not loaded.
     */
    fun withPosted(reply: Post, parentAuthor: String): Branch {
        if (replies.any { it.id == reply.id } || recent.any { it.id == reply.id }) return this
        val lead = { post: Post -> !post.unavailable && post.authorId == parentAuthor }
        val at = if (!loaded) null else {
            val complete = cursor == null
            // Every one of the author's replies is loaded once anyone else's is, or the list is complete.
            val firstOther = replies.indexOfFirst { !lead(it) }
            val leadEnd = if (firstOther != -1) firstOther else if (complete) replies.size else null
            if (lead(reply)) leadEnd else if (complete) replies.size else null
        }
        return if (at == null) copy(open = true, recent = recent + reply)
        else copy(open = true, replies = replies.take(at) + reply + replies.drop(at))
    }
}

/** What a reply's own action row offers for the replies under it. */
enum class BranchToggle { View, Hide, Loading, Continue }

/**
 * The lines in the gutter beside a row under a reply. [runs]: for each level from
 * 2 to the one above this row's, whether that level's line runs on past this row
 * to a later reply. [onward]: whether this row's own line runs on past it, to what
 * comes next under the same reply; the last ends in its curve. [first]: the first
 * under the reply it answers.
 */
data class Lines(val runs: List<Boolean> = emptyList(), val onward: Boolean = false, val first: Boolean = true)

/** A row under the post in focus. [depth] 1 is the focus's own replies, which stand apart with no lines. */
sealed interface ReplyRow {
    val key: String
    val depth: Int
    val lines: Lines

    data class Reply(val post: Post, override val depth: Int, override val lines: Lines, val toggle: BranchToggle?) : ReplyRow {
        override val key get() = "r-${post.id}"
    }

    /** "More replies" under [parent], or "Try again" once a page of them failed. */
    data class More(val parent: String, override val depth: Int, override val lines: Lines, val loading: Boolean, val failed: Boolean) : ReplyRow {
        override val key get() = "m-$parent"
    }

    /** Before the member's own replies, while some before them are not loaded yet. */
    data class Gap(val parent: String, override val depth: Int, override val lines: Lines) : ReplyRow {
        override val key get() = "g-$parent"
    }

    /** A branch with nothing left in it the reader may see. */
    data class Empty(val parent: String, override val depth: Int, override val lines: Lines) : ReplyRow {
        override val key get() = "e-$parent"
    }
}

/**
 * The focus's [replies], with the branches open under them, as the rows the screen
 * draws, in reading order. Each post is shown once, however the pages overlap.
 */
fun replyRows(replies: List<Post>, branches: Map<String, Branch>): List<ReplyRow> {
    val rows = mutableListOf<ReplyRow>()
    val seen = HashSet<String>()

    fun toggle(post: Post, depth: Int): BranchToggle? {
        val branch = branches[post.id]
        val expandable = branch?.empty != true &&
            (post.hasReplies || post.replyCount > 0 || !branch?.replies.isNullOrEmpty() || !branch?.recent.isNullOrEmpty())
        return when {
            !expandable -> null
            // A post that is gone has no page of its own to continue on.
            !opensInPlace(depth, post.unavailable) -> if (post.unavailable) null else BranchToggle.Continue
            branch?.loading == true -> BranchToggle.Loading
            branch?.open == true && branch.loaded -> BranchToggle.Hide
            else -> BranchToggle.View
        }
    }

    fun visit(post: Post, depth: Int, lines: Lines) {
        rows += ReplyRow.Reply(post, depth, lines, toggle(post, depth))
        val branch = branches[post.id]
        if (branch == null || !branch.open || !opensInPlace(depth, post.unavailable)) return
        // Past the limit the replies keep the width they have, so their levels draw no lines of their own.
        val runs = if (depth in 2..THREAD_MAX_DEPTH) lines.runs + lines.onward else lines.runs
        val under = depth + 1
        val children = buildList<(Lines) -> Unit> {
            for (reply in branch.replies) if (seen.add(reply.id)) add { visit(reply, under, it) }
            val mine = branch.recent.filter { seen.add(it.id) }
            if (mine.isNotEmpty()) {
                add { rows += ReplyRow.Gap(post.id, under, it) }
                for (reply in mine) add { visit(reply, under, it) }
            }
            if (branch.empty) add { rows += ReplyRow.Empty(post.id, under, it) }
            if (branch.cursor != null || branch.failed) add { rows += ReplyRow.More(post.id, under, it, branch.loading, branch.failed) }
        }
        children.forEachIndexed { index, child -> child(Lines(runs, onward = index < children.lastIndex, first = index == 0)) }
    }

    for (reply in replies.filter { seen.add(it.id) }) visit(reply, 1, Lines())
    return rows
}

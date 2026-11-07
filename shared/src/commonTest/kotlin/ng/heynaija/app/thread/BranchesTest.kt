package ng.heynaija.app.thread

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import ng.heynaija.app.api.Post

class BranchesTest {
    private fun post(id: String, author: String = "ada", replies: Int = 0, unavailable: Boolean = false) =
        Post(id = id, authorId = author, authorHandle = author, createdAt = "2026-11-07T11:00:00Z", replyCount = replies, hasReplies = replies > 0, unavailable = unavailable)

    private fun open(vararg replies: Post, cursor: String? = null) = Branch(replies = replies.toList(), cursor = cursor, loaded = true, open = true)

    private fun List<ReplyRow>.keys() = map { it.key }

    @Test
    fun theFocusRepliesStandApartWithNoLines() {
        val rows = replyRows(listOf(post("a"), post("b")), emptyMap())

        assertEquals(listOf("r-a", "r-b"), rows.keys())
        assertEquals(listOf(1, 1), rows.map { it.depth })
        assertEquals(listOf(Lines(), Lines()), rows.map { it.lines })
    }

    @Test
    fun anOpenBranchIsDrawnUnderItsReplyWithTheLineRunningOnOnlyToWhatComesNext() {
        // a
        //   b
        //     d
        //   c
        val rows = replyRows(
            listOf(post("a", replies = 2)),
            mapOf("a" to open(post("b", replies = 1), post("c")), "b" to open(post("d"))),
        )

        assertEquals(listOf("r-a", "r-b", "r-d", "r-c"), rows.keys())
        assertEquals(listOf(1, 2, 3, 2), rows.map { it.depth })
        // b's line runs on to c; d hangs from b's, beside the line still running to c.
        assertEquals(Lines(runs = emptyList(), onward = true, first = true), rows[1].lines)
        assertEquals(Lines(runs = listOf(true), onward = false, first = true), rows[2].lines)
        assertEquals(Lines(runs = emptyList(), onward = false, first = false), rows[3].lines)
    }

    @Test
    fun aLevelEndsWhereItsLastReplyEnds() {
        val rows = replyRows(
            listOf(post("a", replies = 2)),
            mapOf("a" to open(post("b"), post("c", replies = 1)), "c" to open(post("d"))),
        )

        // Nothing follows c under a, so no line runs on beside d.
        assertEquals(Lines(runs = listOf(false), onward = false, first = true), rows.single { it.key == "r-d" }.lines)
    }

    @Test
    fun theToggleOpensHidesAndLoads() {
        val reply = post("a", replies = 3)
        fun toggleWith(branch: Branch?) = (replyRows(listOf(reply), branch?.let { mapOf("a" to it) } ?: emptyMap()).first() as ReplyRow.Reply).toggle

        assertEquals(BranchToggle.View, toggleWith(null))
        assertEquals(BranchToggle.Loading, toggleWith(Branch(open = true, loading = true)))
        assertEquals(BranchToggle.Hide, toggleWith(open(post("b"))))
        assertEquals(BranchToggle.View, toggleWith(open(post("b")).copy(open = false)))
        assertNull((replyRows(listOf(post("x")), emptyMap()).first() as ReplyRow.Reply).toggle, "nothing to open")
    }

    @Test
    fun repliesStopAtTheFifthLevelAndContinueOnTheirOwnPage() {
        // a1 > a2 > a3 > a4 > a5 > a6, every branch open.
        val chain = (1..6).map { post("a$it", replies = if (it < 6) 1 else 0) }
        val branches = (1..5).associate { "a$it" to open(chain[it]) }

        val rows = replyRows(listOf(chain[0]), branches)

        assertEquals((1..5).map { "r-a$it" }, rows.keys())
        assertEquals(BranchToggle.Continue, (rows.last() as ReplyRow.Reply).toggle)
    }

    @Test
    fun theRepliesBehindAPostThatIsGoneOpenPastTheLimitWithoutLines() {
        val chain = (1..4).map { post("a$it", replies = 1) } + post("gone", unavailable = true).copy(hasReplies = true)
        val branches = (1..4).associate { "a$it" to open(chain[it]) } + ("gone" to open(post("b")))

        val rows = replyRows(listOf(chain[0]), branches)

        assertEquals(BranchToggle.Hide, (rows.single { it.key == "r-gone" } as ReplyRow.Reply).toggle)
        val beyond = rows.last()
        assertEquals("r-b", beyond.key)
        assertEquals(6, beyond.depth)
        // The levels above draw their lines; the one past the limit adds none.
        assertEquals(listOf(false, false, false, false), beyond.lines.runs)
    }

    @Test
    fun moreRepliesAndTryAgainEndTheirBranch() {
        val paged = replyRows(listOf(post("a", replies = 30)), mapOf("a" to open(post("b"), cursor = "c2")))
        assertEquals(listOf("r-a", "r-b", "m-a"), paged.keys())
        assertEquals(true, paged[1].lines.onward)
        assertEquals(false, paged[2].lines.onward)

        val failed = replyRows(listOf(post("a", replies = 3)), mapOf("a" to Branch(open = true, failed = true)))
        assertEquals(ReplyRow.More("a", 2, Lines(onward = false, first = true), loading = false, failed = true), failed[1])
    }

    @Test
    fun aBranchStillLoadingTakesNoRoomAndAnEmptyOneSaysSo() {
        val loading = replyRows(listOf(post("a", replies = 3)), mapOf("a" to Branch(open = true, loading = true)))
        assertEquals(listOf("r-a"), loading.keys())

        val empty = replyRows(listOf(post("a", replies = 1)), mapOf("a" to Branch(loaded = true, open = true)))
        assertEquals(listOf("r-a", "e-a"), empty.keys())
        assertNull((empty.first() as ReplyRow.Reply).toggle)
    }

    @Test
    fun aPostIsShownOnceHoweverThePagesOverlap() {
        val rows = replyRows(listOf(post("a", replies = 2), post("b")), mapOf("a" to open(post("b"), post("c"))))

        assertEquals(listOf("r-a", "r-c", "r-b"), rows.keys())
        assertEquals(Lines(runs = emptyList(), onward = false, first = true), rows[1].lines)
    }

    @Test
    fun aReplyPostedBeforeTheRestLoadedWaitsBehindYourReply() {
        val branch = Branch().withPosted(post("mine", author = "me"), parentAuthor = "ada")
        val rows = replyRows(listOf(post("a", replies = 4)), mapOf("a" to branch))

        assertEquals(listOf("r-a", "g-a", "r-mine"), rows.keys())
        // The line runs straight through "Your reply" to the reply under it.
        assertEquals(true, rows[1].lines.onward)
        assertEquals(BranchToggle.View, (rows[0] as ReplyRow.Reply).toggle)
    }

    @Test
    fun aPostedReplyGoesWhereItWouldLoad() {
        val loaded = open(post("lead", author = "ada"), post("other", author = "bola"))

        // Everyone else's newest, after the rest, once the list is complete.
        assertEquals(listOf("lead", "other", "mine"), loaded.withPosted(post("mine", author = "me"), "ada").replies.map { it.id })
        // The author's own, after their other replies and before everyone else's.
        assertEquals(listOf("lead", "again", "other"), loaded.withPosted(post("again", author = "ada"), "ada").replies.map { it.id })
        // With more to load, it waits behind "Your reply" rather than standing in for the page between.
        val paged = loaded.copy(cursor = "c2").withPosted(post("mine", author = "me"), "ada")
        assertEquals(listOf("mine"), paged.recent.map { it.id })
        // Posted twice, it is there once.
        assertEquals(loaded, loaded.withPosted(post("lead", author = "ada"), "ada"))
    }

    @Test
    fun aReplyOnTheScreenIsFoundWithItsLevel() {
        val state = ThreadState(
            replies = listOf(post("a", replies = 1)),
            branches = mapOf("a" to open(post("b", replies = 1)), "b" to Branch(recent = listOf(post("c")))),
        )

        assertEquals("a" to 1, state.reply("a")?.let { (post, depth) -> post.id to depth })
        assertEquals("b" to 2, state.reply("b")?.let { (post, depth) -> post.id to depth })
        assertEquals("c" to 3, state.reply("c")?.let { (post, depth) -> post.id to depth })
        assertNull(state.reply("elsewhere"))
    }

    @Test
    fun theLastPageTakesInTheRepliesPostedBeforeIt() {
        val waiting = Branch(open = true).withPosted(post("mine", author = "me"), "ada")

        val first = waiting.append(listOf(post("b")), next = "c2")
        assertEquals(listOf("b"), first.replies.map { it.id })
        assertEquals(listOf("mine"), first.recent.map { it.id })

        val last = first.append(listOf(post("c"), post("mine", author = "me")), next = null)
        assertEquals(listOf("b", "c", "mine"), last.replies.map { it.id })
        assertEquals(emptyList(), last.recent)
    }
}

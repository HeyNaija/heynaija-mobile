package ng.heynaija.app.thread

import io.ktor.http.HttpStatusCode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import ng.heynaija.app.PostChange
import ng.heynaija.app.api.FakeServer
import ng.heynaija.app.api.Post

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun json(id: String, parent: String?, author: String = "ada", replies: Int = 0) =
        """{ "id": "$id", "authorId": "$author", "authorHandle": "$author", "createdAt": "2026-11-07T11:00:00.000Z",
            "replyParentId": ${parent?.let { "\"$it\"" } ?: "null"}, "replyCount": $replies, "hasReplies": ${replies > 0} }"""

    private fun page(vararg posts: String, cursor: String? = null) =
        """{ "replies": [${posts.joinToString(",")}], "cursor": ${cursor?.let { "\"$it\"" } ?: "null"} }"""

    private var threadLoads = 0

    /** The post "f", whose reply "a" has two answers, sent a page at a time. Loaded again, "f" has a second reply. */
    private val server = FakeServer { request ->
        when (val path = request.url.encodedPath.removePrefix("/api/v1")) {
            "/posts/f" -> HttpStatusCode.OK to """{ "post": ${json("f", null, replies = 1)}, "replyCursor": null,
                "replies": [${json("a", "f", replies = 2)}${if (++threadLoads > 1) "," + json("z", "f", author = "me") else ""}] }"""
            "/posts/a/replies" -> HttpStatusCode.OK to
                if (request.url.parameters["cursor"] == null) page(json("b", "a"), cursor = "c2") else page(json("c", "a"))
            "/posts/mine/post" -> HttpStatusCode.OK to json("mine", "a", author = "me")
            // A story's first post "s": update "u1" has no replies, "u2" has one.
            "/posts/s" -> HttpStatusCode.OK to """{ "post": ${json("s", null)}, "replies": [], "replyCursor": null,
                "updates": [${json("u1", null)}, ${json("u2", null, replies = 1)}] }"""
            "/posts/u2/replies" -> HttpStatusCode.OK to page(json("d", "u2"))
            else -> HttpStatusCode.NotFound to """{ "message": "No $path" }"""
        }
    }

    private val changes = MutableSharedFlow<PostChange>(extraBufferCapacity = 4)

    /** The API answers on its own thread: wait, in real time, for the state it leads to. */
    private suspend fun ThreadViewModel.until(check: (ThreadState) -> Boolean) =
        withContext(Dispatchers.Default) { withTimeout(5_000) { state.first(check) } }

    private fun ThreadViewModel.keys() = state.value.let { s -> replyRows(s.replies, s.branches).map { it.key } }

    private suspend fun thread() = ThreadViewModel(server.api, "f", null, changes).also { it.until { s -> s.loaded } }

    @Test
    fun viewRepliesOpensThemOldestFirstAPageAtATimeAndHideFoldsThem() = runTest {
        val vm = thread()
        val reply = vm.state.value.replies.single()

        vm.toggle(reply)
        vm.until { it.branches["a"]?.loaded == true }
        assertEquals(listOf("r-a", "r-b", "m-a"), vm.keys())
        val first = server.requests.last()
        assertEquals("oldest", first.url.parameters["order"])
        assertNull(first.url.parameters["cursor"])

        vm.moreReplies("a")
        vm.until { it.branches["a"]?.let { branch -> branch.loaded && branch.cursor == null } == true }
        assertEquals(listOf("r-a", "r-b", "r-c"), vm.keys())
        assertEquals("c2", server.requests.last().url.parameters["cursor"])

        vm.toggle(reply)
        assertEquals(listOf("r-a"), vm.keys())

        // Opened again, it shows what it loaded without asking again.
        val asked = server.requests.size
        vm.toggle(reply)
        assertEquals(listOf("r-a", "r-b", "r-c"), vm.keys())
        assertEquals(asked, server.requests.size)
    }

    @Test
    fun aReplyPostedUnderAReplyJoinsItsBranch() = runTest {
        val vm = thread()

        changes.emit(PostChange.Created("mine", replyTo = "a"))
        vm.until { it.branches["a"]?.recent?.isNotEmpty() == true }

        // "a" has replies not loaded yet, so the new one waits behind "Your reply" until they are.
        assertEquals(listOf("r-a", "g-a", "r-mine"), vm.keys())
        vm.toggle(vm.state.value.replies.single())
        vm.until { it.branches["a"]?.loaded == true }
        assertEquals(listOf("r-a", "r-b", "g-a", "r-mine", "m-a"), vm.keys())
    }

    @Test
    fun aReplyToThePostLoadsTheThreadAgainAndKeepsWhatIsOpen() = runTest {
        val vm = thread()
        vm.toggle(vm.state.value.replies.single())
        vm.until { it.branches["a"]?.loaded == true }

        changes.emit(PostChange.Created("z", replyTo = "f"))
        vm.until { it.replies.size == 2 }

        assertEquals(listOf("r-a", "r-b", "m-a", "r-z"), vm.keys())
    }

    @Test
    fun aStoryUpdateOpensItsRepliesAndTakesInOnesPostedToIt() = runTest {
        val vm = ThreadViewModel(server.api, "s", null, changes).also { it.until { s -> s.loaded } }
        val (quiet, busy) = vm.state.value.updates
        fun keys(update: Post) = vm.state.value.let { s -> replyRows(listOf(s.updates.first { it.id == update.id }), s.branches).map { it.key } }

        vm.toggle(busy)
        vm.until { it.branches["u2"]?.loaded == true }
        assertEquals(listOf("r-u2", "r-d"), keys(busy))
        assertEquals("oldest", server.requests.last().url.parameters["order"])

        // u1 had no replies, so the one posted to it is all there is: it joins the list, not "Your reply".
        changes.emit(PostChange.Created("mine", replyTo = "u1"))
        changes.emit(PostChange.Updated(quiet.copy(replyCount = 1)))
        vm.until { it.branches["u1"]?.replies?.isNotEmpty() == true }
        assertEquals(listOf("r-u1", "r-mine"), keys(quiet))

        changes.emit(PostChange.Deleted("mine"))
        assertEquals(listOf("r-u1"), keys(quiet))
        assertEquals(0, vm.state.value.updates.first().replyCount)
    }

    @Test
    fun aReplyDeletedFromABranchLeavesIt() = runTest {
        val vm = thread()
        vm.toggle(vm.state.value.replies.single())
        vm.until { it.branches["a"]?.loaded == true }

        changes.emit(PostChange.Deleted("b"))

        assertEquals(listOf("r-a", "m-a"), vm.keys())
        assertEquals(1, vm.state.value.replies.single().replyCount)
    }

    @Test
    fun aReplyInAGistAsksSomeoneSignedOutToSignInWhileGistsAreOn() = runTest {
        // The API checks the gists flag before the session: 401 while Gists are on for anyone, 404 while off.
        val on = FakeServer { HttpStatusCode.Unauthorized to """{ "message": "Sign in first" }""" }
        val asked = ThreadViewModel(on.api, "r1", null, changes, gist = "g1").also { it.until { s -> s.signIn } }
        assertEquals(false, asked.state.value.failed)
        assertEquals(0, on.signedOut)

        val off = FakeServer { HttpStatusCode.NotFound to """{ "message": "Not found" }""" }
        val missing = ThreadViewModel(off.api, "r1", null, changes, gist = "g1").also { it.until { s -> s.missing } }
        assertEquals(false, missing.state.value.signIn)
    }
}

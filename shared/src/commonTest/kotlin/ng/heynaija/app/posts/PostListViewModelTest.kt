package ng.heynaija.app.posts

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import ng.heynaija.app.PostChange
import ng.heynaija.app.api.FeedPage
import ng.heynaija.app.api.Post
import ng.heynaija.app.api.QuotedPost

@OptIn(ExperimentalCoroutinesApi::class)
class PostListViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun post(id: String, liked: Boolean = false) = Post(id = id, authorHandle = "ada", createdAt = "2026-11-06T11:00:00Z", viewerLiked = liked)

    @Test
    fun pagesFollowTheCursorAndAPostIsShownOnce() = runTest {
        val pages = mapOf(null to FeedPage(listOf(post("a"), post("b")), "c2"), "c2" to FeedPage(listOf(post("b"), post("c")), null))
        val asked = mutableListOf<String?>()
        val vm = PostListViewModel(load = { cursor -> asked += cursor; pages.getValue(cursor) })

        vm.loadMore()

        assertEquals(listOf(null, "c2"), asked)
        assertEquals(listOf("a", "b", "c"), vm.state.value.posts.map { it.id })
        assertTrue(vm.state.value.atEnd)
    }

    @Test
    fun aFailedFirstPageCanBeTriedAgain() = runTest {
        var fail = true
        val vm = PostListViewModel(load = { if (fail) error("offline") else FeedPage(listOf(post("a")), null) })
        assertTrue(vm.state.value.failed)
        assertEquals(false, vm.state.value.loaded)

        fail = false
        vm.retry()

        assertEquals(listOf("a"), vm.state.value.posts.map { it.id })
    }

    @Test
    fun aLikeOrADeleteElsewhereShowsHere() = runTest {
        val changes = MutableSharedFlow<PostChange>(extraBufferCapacity = 4)
        val vm = PostListViewModel(load = { FeedPage(listOf(post("a"), post("b")), null) }, changes = changes)

        changes.emit(PostChange.Updated(post("a", liked = true)))
        changes.emit(PostChange.Deleted("b"))

        assertEquals(listOf("a"), vm.state.value.posts.map { it.id })
        assertTrue(vm.state.value.posts.single().viewerLiked)
    }

    @Test
    fun bookmarksDropAPostNoLongerBookmarked() = runTest {
        val changes = MutableSharedFlow<PostChange>(extraBufferCapacity = 4)
        val saved = post("a").copy(viewerBookmarked = true)
        val vm = PostListViewModel(load = { FeedPage(listOf(saved), null) }, changes = changes, keep = { it.viewerBookmarked })

        changes.emit(PostChange.Updated(saved.copy(viewerBookmarked = false)))

        assertTrue(vm.state.value.posts.isEmpty())
    }

    @Test
    fun postingRefreshesOnlyListsThatShowNewPosts() = runTest {
        val changes = MutableSharedFlow<PostChange>(extraBufferCapacity = 4)
        var latestLoads = 0
        var discoverLoads = 0
        PostListViewModel(load = { latestLoads++; FeedPage(emptyList(), null) }, changes = changes, refreshOnPost = true)
        PostListViewModel(load = { discoverLoads++; FeedPage(emptyList(), null) }, changes = changes)

        changes.emit(PostChange.Created("new"))

        assertEquals(2, latestLoads)
        assertEquals(1, discoverLoads)
    }

    @Test
    fun aNewNameShowsOnTheMembersPostsAtOnce() = runTest {
        val changes = MutableSharedFlow<PostChange>(extraBufferCapacity = 4)
        val mine = post("a").copy(authorId = "m1", authorName = "Ada")
        val quoting = post("b").copy(authorId = "t1", authorHandle = "tunde", authorName = "Tunde", quote = QuotedPost(id = "a", authorHandle = "ada", authorName = "Ada"))
        val vm = PostListViewModel(load = { FeedPage(listOf(mine, quoting), null) }, changes = changes)

        changes.emit(PostChange.Author(id = "m1", handle = "ada", name = "Ada Obi", avatar = "avatars/m1/2"))

        val (a, b) = vm.state.value.posts
        assertEquals("Ada Obi", a.authorName)
        assertEquals("avatars/m1/2", a.authorAvatar)
        assertEquals("Tunde", b.authorName)
        assertEquals("Ada Obi", b.quote?.authorName)
    }
}

package ng.heynaija.app.feed

import io.ktor.http.HttpStatusCode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import ng.heynaija.app.api.FakeServer
import ng.heynaija.app.api.pageJson
import ng.heynaija.app.api.postJson

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun FeedViewModel.settled() = state.first { !it.loading && !it.refreshing }

    @Test
    fun pagesUntilTheCursorRunsOut() = runTest {
        val server = FakeServer { request ->
            when (request.url.parameters["cursor"]) {
                null -> HttpStatusCode.OK to pageJson(postJson("p1"), postJson("p2"), cursor = "c2")
                // p2 moved onto the next page while we read; it is shown once.
                "c2" -> HttpStatusCode.OK to pageJson(postJson("p2"), postJson("p3"))
                else -> error("unexpected cursor")
            }
        }
        val feed = FeedViewModel(server.api)
        assertEquals(listOf("p1", "p2"), feed.settled().posts.map { it.id })

        feed.loadMore()
        val state = feed.settled()

        assertEquals(listOf("p1", "p2", "p3"), state.posts.map { it.id })
        assertTrue(state.atEnd)
        feed.loadMore()
        assertEquals(2, server.requests.size)
    }

    @Test
    fun aFailedFirstPageCanBeTriedAgain() = runTest {
        var up = false
        val server = FakeServer {
            if (up) HttpStatusCode.OK to pageJson(postJson("p1")) else HttpStatusCode.BadGateway to "{}"
        }
        val feed = FeedViewModel(server.api)
        val failed = feed.settled()
        assertTrue(failed.failed)
        assertFalse(failed.loaded)

        up = true
        feed.retry()
        val state = feed.settled()

        assertFalse(state.failed)
        assertEquals(listOf("p1"), state.posts.map { it.id })
    }

    @Test
    fun refreshingStartsAgainFromTheNewest() = runTest {
        var newest = "p1"
        val server = FakeServer { HttpStatusCode.OK to pageJson(postJson(newest), cursor = "c2") }
        val feed = FeedViewModel(server.api)
        feed.settled()

        newest = "p0"
        feed.refresh()
        val state = feed.settled()

        assertEquals(listOf("p0"), state.posts.map { it.id })
        assertEquals(null, server.requests.last().url.parameters["cursor"])
    }
}

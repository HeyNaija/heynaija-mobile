package ng.heynaija.app.api

import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class HeyNaijaApiTest {
    @Test
    fun readsLatestAndIgnoresWhatItDoesNotKnow() = runTest {
        val server = FakeServer { HttpStatusCode.OK to pageJson(postJson("p1"), cursor = "c2") }

        val page = server.api.latest()

        val request = server.requests.single()
        assertEquals("https://test.heynaija.ng/api/v1/feed", request.url.toString().substringBefore('?'))
        assertEquals("20", request.url.parameters["limit"])
        assertNull(request.url.parameters["cursor"])
        assertEquals("c2", page.cursor)
        val post = page.posts.single()
        assertEquals("ada", post.authorHandle)
        assertNull(post.authorName)
        assertEquals("Lagos", post.community?.name)
        assertEquals(1536, post.images.single().height)
        assertTrue(post.quote!!.unavailable)
    }

    @Test
    fun sendsTheCursorForTheNextPage() = runTest {
        val server = FakeServer { HttpStatusCode.OK to pageJson() }

        val page = server.api.latest(cursor = "c2")

        assertEquals("c2", server.requests.single().url.parameters["cursor"])
        assertNull(page.cursor)
    }

    @Test
    fun aRefusalThrows() = runTest {
        val server = FakeServer { HttpStatusCode.ServiceUnavailable to """{"code":"auth_provider_unavailable"}""" }

        assertFailsWith<Exception> { server.api.latest() }
    }

    @Test
    fun mediaUrlsAddTheSize() {
        assertEquals("$ORIGIN/media/posts/p1/640.webp", mediaUrl("/media/posts/p1", "640"))
    }
}

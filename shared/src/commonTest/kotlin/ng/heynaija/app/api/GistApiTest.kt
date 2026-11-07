package ng.heynaija.app.api

import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

private val gistJson = """
    {
      "id": "g1", "title": "Jollof wars", "status": "live",
      "community": { "id": "c1", "slug": "lagos", "name": "Lagos" },
      "host": { "id": "h1", "handle": "ada", "displayName": "Ada" },
      "speakers": [{ "id": "s1", "handle": "tunde", "displayName": null, "role": "cohost" }],
      "locked": false, "requestsOpen": true, "listenerCap": 500, "listenerCount": 12, "peakListeners": 14,
      "startedAt": "2026-11-07T10:00:00.000Z", "endedAt": null,
      "viewer": { "role": null, "requestedToSpeak": false }
    }
""".trimIndent()

private val HttpRequestData.json get() = (body as TextContent).text

class GistApiTest {
    @Test
    fun readsTheLiveNowList() = runTest {
        val server = FakeServer { HttpStatusCode.OK to """{ "items": [{ "id": "g1", "title": "Jollof wars", "host": { "id": "h1", "handle": "ada", "displayName": null }, "community": null, "listenerCount": 3, "startedAt": null }] }""" }

        val live = server.api.liveGists().single()

        assertEquals("https://test.heynaija.ng/api/v1/gists/live", server.requests.single().url.toString())
        assertEquals("@ada", live.host.name)
        assertNull(live.community)
    }

    @Test
    fun joiningGivesATokenAndTheRoom() = runTest {
        val server = FakeServer { HttpStatusCode.OK to """{ "url": "wss://rtc.heynaija.ng", "token": "jwt", "role": "listener", "gist": $gistJson }""" }

        val joined = server.api.joinGist("g1")

        val request = server.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/gists/g1/join", request.url.encodedPath)
        assertEquals("wss://rtc.heynaija.ng", joined.url)
        assertEquals("cohost", joined.gist.speakers.single().role)
        assertEquals("Lagos", joined.gist.community?.name)
    }

    @Test
    fun aFullGistIsRefusedByName() = runTest {
        val server = FakeServer { HttpStatusCode.Conflict to """{ "code": "gist_full", "message": "This Gist is full." }""" }

        val refusal = assertFailsWith<ApiException> { server.api.joinGist("g1") }

        assertEquals(409, refusal.status)
        assertEquals("gist_full", refusal.code)
    }

    @Test
    fun theHostLocksTheRoomWithAPatch() = runTest {
        val server = FakeServer { HttpStatusCode.OK to gistJson }

        server.api.gistSettings("g1", locked = true)

        val request = server.requests.single()
        assertEquals(HttpMethod.Patch, request.method)
        assertEquals("""{"locked":true}""", request.json)
    }

    @Test
    fun aReplyToAReplyInAGistNamesIt() = runTest {
        val server = FakeServer { HttpStatusCode.Created to """{ "id": "r2" }""" }

        val created = server.api.createGistReply("g1", "True talk", replyTo = "r1")

        val request = server.requests.single()
        assertEquals("/api/v1/gists/g1/replies", request.url.encodedPath)
        assertEquals("""{"text":"True talk","replyTo":"r1"}""", request.json)
        assertEquals("r2", created.id)
    }

    @Test
    fun aRepliesPageSaysWhoMayReply() = runTest {
        val server = FakeServer { HttpStatusCode.OK to """{ "replies": [${postJson("r1")}], "cursor": null, "count": 1, "viewerCanReply": true, "viewerIsHost": false, "replyAudience": "following" }""" }

        val page = server.api.gistReplies("g1")

        assertEquals("newest", server.requests.single().url.parameters["order"])
        assertEquals("following", page.replyAudience)
        assertEquals(listOf("r1"), page.replies.map { it.id })
    }
}

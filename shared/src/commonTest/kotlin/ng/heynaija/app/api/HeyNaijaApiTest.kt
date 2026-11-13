package ng.heynaija.app.api

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class HeyNaijaApiTest {
    @Test
    fun readsAThreadWithAGoneReplyInIt() = runTest {
        val gone = """{ "unavailable": true, "id": "r0", "replyParentId": "p1", "createdAt": "2026-11-06T11:00:00.000Z", "hasReplies": true }"""
        val server = FakeServer { HttpStatusCode.OK to """{ "ancestors": [], "post": ${postJson("p1")}, "replies": [$gone, ${postJson("r1")}], "replyCursor": null, "ancestorsTruncated": false }""" }

        val thread = server.api.thread("p1")

        assertEquals(listOf(true, false), thread.replies.map { it.unavailable })
    }

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
    fun pushServicesAreWhatTheServerSaysOrAndroidAloneFromAnOlderOne() = runTest {
        val both = FakeServer { HttpStatusCode.OK to """{ "publicKey": null, "app": true, "appServices": ["fcm", "apns", "apns_sandbox"] }""" }
        assertEquals(listOf("fcm", "apns", "apns_sandbox"), both.api.pushServices())
        val older = FakeServer { HttpStatusCode.OK to """{ "publicKey": "BPk", "app": true }""" }
        assertEquals(listOf("fcm"), older.api.pushServices())
        val none = FakeServer { HttpStatusCode.OK to """{ "publicKey": null, "app": false }""" }
        assertEquals(emptyList(), none.api.pushServices())
    }

    @Test
    fun applesCodeGoesToTheApiToBeKept() = runTest {
        val server = FakeServer(token = "ory_st_abc") { HttpStatusCode.NoContent to "" }

        server.api.keepAppleToken("c.one-time")

        val request = server.requests.single()
        assertEquals("https://test.heynaija.ng/api/v1/apple/token", request.url.toString())
        assertEquals("""{"code":"c.one-time"}""", (request.body as io.ktor.http.content.TextContent).text)
    }

    @Test
    fun aRefusalThrows() = runTest {
        val server = FakeServer { HttpStatusCode.ServiceUnavailable to """{"code":"auth_provider_unavailable"}""" }

        assertFailsWith<Exception> { server.api.latest() }
    }

    @Test
    fun theSessionTokenGoesToTheApiAsABearerToken() = runTest {
        val server = FakeServer(token = "ory_st_abc") { HttpStatusCode.OK to pageJson() }

        server.api.latest()

        assertEquals("Bearer ory_st_abc", server.requests.single().headers["Authorization"])
    }

    @Test
    fun aVisitorSendsNoAuthorization() = runTest {
        val server = FakeServer { HttpStatusCode.OK to pageJson() }

        server.api.latest()

        assertNull(server.requests.single().headers["Authorization"])
    }

    @Test
    fun theTokenNeverGoesToAnUploadUrl() = runTest {
        val server = FakeServer(token = "ory_st_abc") { HttpStatusCode.OK to "" }

        server.api.putFile(Upload("m1", "https://bucket.example/uploads/m1?X-Amz-Signature=s"), "image/jpeg", byteArrayOf(1, 2, 3))

        val request = server.requests.single()
        assertNull(request.headers["Authorization"])
        assertEquals("image/jpeg", request.body.contentType?.toString())
    }

    @Test
    fun aRefusalCarriesTheApisCodeAndWords() = runTest {
        val server = FakeServer(token = "t") {
            HttpStatusCode.Forbidden to """{"statusCode":403,"code":"reply_not_allowed","message":"Only people mentioned can reply"}"""
        }

        val e = assertFailsWith<ApiException> { server.api.createPost(NewPost("Hi", replyTo = "p1")) }

        assertEquals(403, e.status)
        assertEquals("reply_not_allowed", e.code)
        assertEquals("Only people mentioned can reply", e.message)
        assertEquals(0, server.signedOut)
    }

    @Test
    fun a401WithATokenMeansTheSessionEnded() = runTest {
        val server = FakeServer(token = "stale") { HttpStatusCode.Unauthorized to """{"message":"sign in required"}""" }

        assertFailsWith<ApiException> { server.api.me() }

        assertEquals(1, server.signedOut)
    }

    @Test
    fun aNewPostSendsOnlyWhatItHas() = runTest {
        val server = FakeServer(token = "t") { HttpStatusCode.Created to """{"id":"p9"}""" }

        val created = server.api.createPost(
            NewPost("Who wins?", community = "lagos", poll = NewPoll(listOf("Jollof", "Fried rice"), multiple = false, expiresIn = "1_day")),
        )

        assertEquals("p9", created.id)
        val body = (server.requests.single().body as io.ktor.http.content.TextContent).text
        val json = HeyNaijaApi.json.parseToJsonElement(body).toString()
        assertTrue("\"community\":\"lagos\"" in json, json)
        assertTrue("\"options\":[\"Jollof\",\"Fried rice\"]" in json, json)
        assertTrue("replyTo" !in json, json)
        assertTrue("images" !in json, json)
    }

    @Test
    fun anUpdateNamesItsStoryAndLeavesTheReplySettingToIt() = runTest {
        val server = FakeServer(token = "t") { HttpStatusCode.Created to """{"id":"u2"}""" }

        server.api.createPost(NewPost("Day two", storyOf = "s1", replyAudience = null))

        val body = (server.requests.single().body as io.ktor.http.content.TextContent).text
        val json = HeyNaijaApi.json.parseToJsonElement(body).toString()
        assertTrue("\"storyOf\":\"s1\"" in json, json)
        // Left out, the update starts with its story's first post's setting.
        assertTrue("replyAudience" !in json, json)
    }

    @Test
    fun readsAStoryWithItsNewestUpdates() = runTest {
        val start = """{ "id": "s1", "authorHandle": "ada", "text": "Building a darkroom", "images": [], "createdAt": "2026-11-01T10:00:00.000Z" }"""
        val update = postJson("u2", text = "Day two").trimEnd().removeSuffix("}") +
            """, "story": { "id": "s1", "number": 2, "updates": 2, "followers": 3, "following": true, "start": $start } }"""
        val first = postJson("s1").trimEnd().removeSuffix("}") +
            """, "story": { "id": "s1", "number": 0, "updates": 2, "followers": 3, "following": true, "start": null } }"""
        val server = FakeServer { HttpStatusCode.OK to """{ "ancestors": [], "post": $first, "updates": [$update], "replies": [], "replyCursor": null }""" }

        val thread = server.api.thread("s1")

        assertEquals(0, thread.post.story?.number)
        val story = thread.updates.single().story!!
        assertEquals(2, story.number)
        assertEquals("Building a darkroom", story.start?.text)
        assertTrue(story.following)
        // A server from before stories sends none, and that still reads.
        assertNull(HeyNaijaApi.json.decodeFromString<Post>(postJson("p1")).story)
    }

    @Test
    fun mediaUrlsAddTheSizeOnEachServer() {
        val was = Servers.current
        try {
            Servers.current = Server.Production
            assertEquals("https://heynaija.ng/media/posts/p1/640.webp", mediaUrl("/media/posts/p1", "640"))
            Servers.current = Server.Staging
            assertEquals("https://staging.heynaija.ng/media/posts/p1/640.webp", mediaUrl("/media/posts/p1", "640"))
            // The dev stack's bucket is served at its own root.
            Servers.current = Server.Local
            assertEquals("http://127.0.0.1:9100/heynaija-media/posts/p1/640.webp", mediaUrl("/media/posts/p1", "640"))
        } finally {
            Servers.current = was
        }
    }

    @Test
    fun videoFilesAreAbsoluteWhereverTheServerNamesThem() {
        val was = Servers.current
        try {
            Servers.current = Server.Production
            // Production and staging name them on their media domain already.
            assertEquals("https://media.heynaija.ng/videos/v1/poster.webp", mediaFile("https://media.heynaija.ng/videos/v1/poster.webp"))
            // The dev stack has no MEDIA_ORIGIN and sends a path.
            Servers.current = Server.Local
            assertEquals("http://127.0.0.1:9100/heynaija-media/videos/v1/hls/master.m3u8", mediaFile("/media/videos/v1/hls/master.m3u8"))
        } finally {
            Servers.current = was
        }
    }

    @Test
    fun weeklyHighlightsAreOfferedOnlyWhereTheServerSendsTheirPreference() = runTest {
        // Staging and production today: no weekly highlights, so no key, and no switch.
        val without = FakeServer(token = "t") { HttpStatusCode.OK to """{ "reply": true, "like": false, "email": true }""" }
        val with = FakeServer(token = "t") { HttpStatusCode.OK to """{ "reply": true, "email": false, "highlights": false }""" }

        assertFalse(offersHighlights(without.api.notificationPreferences()))
        val offered = with.api.notificationPreferences()
        assertTrue(offersHighlights(offered))
        assertEquals(false, offered[HIGHLIGHTS_PREFERENCE])
        // Not a kind of notification: never listed with replies and likes, whose switches default to on.
        assertTrue(NOTIFICATION_KINDS.none { it.first == HIGHLIGHTS_PREFERENCE })
    }

    @Test
    fun turningWeeklyHighlightsOffSendsThatAlone() = runTest {
        val server = FakeServer(token = "t") { HttpStatusCode.OK to """{ "reply": true, "email": true, "highlights": false }""" }

        val saved = server.api.setNotificationPreference(HIGHLIGHTS_PREFERENCE, false)

        val request = server.requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("/api/v1/notifications/preferences", request.url.encodedPath)
        assertEquals("""{"highlights":false}""", (request.body as TextContent).text)
        assertEquals(false, saved[HIGHLIGHTS_PREFERENCE])
    }
}

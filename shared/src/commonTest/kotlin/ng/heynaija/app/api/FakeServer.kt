package ng.heynaija.app.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher

val TestServer = Server(
    key = "test",
    label = "Test",
    api = "https://test.heynaija.ng/api",
    kratos = "https://test.heynaija.ng/kratos",
    media = "https://test.heynaija.ng",
    web = "https://test.heynaija.ng",
)

/**
 * An API that answers from [handle] and keeps every request it was sent. [token]
 * is the session the app holds; [signedOut] counts the API telling it the session ended.
 * It answers on its own thread, as the real one does, unless given a [dispatcher]:
 * with the test's own, a request is answered where it was made, in the test's time.
 */
class FakeServer(
    var token: String? = null,
    dispatcher: CoroutineDispatcher? = null,
    private val handle: (HttpRequestData) -> Pair<HttpStatusCode, String>,
) {
    val requests = mutableListOf<HttpRequestData>()
    var signedOut = 0

    private val engine = MockEngine(
        MockEngineConfig().apply {
            this.dispatcher = dispatcher
            addHandler { request ->
                requests += request
                val (status, body) = handle(request)
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        },
    )

    val api = HeyNaijaApi(server = { TestServer }, token = { token }, engine = engine, onSignedOut = { signedOut++ })
    val kratos = KratosAuth(server = { TestServer }, engine = engine)
}

/** A post as the API sends it, with fields the app does not read yet. */
fun postJson(id: String, handle: String = "ada", text: String = "Hello from Lagos") = """
    {
      "id": "$id", "authorId": "a1", "authorHandle": "$handle", "authorName": null,
      "authorFounder": false, "text": "$text", "mentions": [], "hashtags": [],
      "createdAt": "2026-11-06T11:00:00.000Z", "replyParentId": null,
      "replyAudience": "everyone", "viewerCanReply": false,
      "likeCount": 3, "replyCount": 1, "repostCount": 0,
      "viewerLiked": false, "viewerReposted": false, "viewerBookmarked": false,
      "deleted": false, "pinnedOnProfile": false, "pinnedInCommunity": false,
      "viewerCanPinInCommunity": false, "reason": null, "replyTo": null,
      "images": [{ "src": "/media/posts/p1", "alt": "", "width": 1024, "height": 1536, "animated": false }],
      "video": null, "links": [], "linkPreview": null, "authorAvatar": null,
      "community": { "slug": "lagos", "name": "Lagos" }, "quote": { "unavailable": true },
      "poll": null, "repostedBy": null
    }
""".trimIndent()

fun pageJson(vararg posts: String, cursor: String? = null) =
    """{ "posts": [${posts.joinToString(",")}], "cursor": ${cursor?.let { "\"$it\"" } ?: "null"} }"""

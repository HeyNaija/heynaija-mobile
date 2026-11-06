package ng.heynaija.app.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Posts per page; the API allows 1 to 100 and defaults to 30. */
const val PAGE_SIZE = 20

/**
 * The HeyNaija API, as the web app's proxy serves it at `<origin>/api/v1`.
 * Signed out for now: only public reads. `engine` is for tests; the app uses
 * OkHttp on Android and URLSession on iOS, whichever is on the classpath.
 */
class HeyNaijaApi(
    private val origin: String = ORIGIN,
    engine: HttpClientEngine? = null,
) {
    private val client = engine?.let { HttpClient(it) { setUp() } } ?: HttpClient { setUp() }

    private fun HttpClientConfig<*>.setUp() {
        // A refused or failed request throws instead of decoding an error body.
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
        // Patchy mobile data: give up on a stalled request rather than spin forever.
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 30_000
        }
    }

    /** Latest: every post on HeyNaija, newest first, not ranked. Public. */
    suspend fun latest(cursor: String? = null, limit: Int = PAGE_SIZE): FeedPage =
        client.get("$origin/api/v1/feed") {
            parameter("limit", limit)
            if (cursor != null) parameter("cursor", cursor)
        }.body()

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            // A null where the app expects a value takes the field's default.
            coerceInputValues = true
        }
    }
}

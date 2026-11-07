package ng.heynaija.app.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Posts per page; the API allows 1 to 100 and defaults to 30. */
const val PAGE_SIZE = 20

/**
 * The API said no. [status] is the HTTP status, [code] the API's own name for
 * the refusal where it gives one (`reply_not_allowed`, `reauth_required`, ...),
 * and [message] its words, fit to show.
 */
class ApiException(val status: Int, val code: String?, message: String?) : Exception(message ?: "HTTP $status")

/** The parts of a new post; see `newPost` in apps/api/src/posts/posts.service.ts. */
data class NewPost(
    val text: String,
    val replyTo: String? = null,
    val quote: String? = null,
    val community: String? = null,
    val images: List<Pair<String, String>> = emptyList(),
    val poll: NewPoll? = null,
    /** Null: everyone's, or for an update its story's first post's setting. */
    val replyAudience: String? = "everyone",
    /** An update to this story: its first post. */
    val storyOf: String? = null,
    /** A ready video's upload ID and its description. */
    val video: Pair<String, String>? = null,
    /** Marked sensitive by its author (ADR 0020). */
    val sensitive: Boolean = false,
)

data class NewPoll(val options: List<String>, val multiple: Boolean, val expiresIn: String)

/**
 * The HeyNaija API. Every call carries the session token, when there is one, as
 * `Authorization: Bearer` (ADR 0022 in the web repository); public reads work
 * without. `engine` is for tests; the app uses OkHttp on Android and URLSession on
 * iOS. [onSignedOut] hears of a 401 on a call that sent a token: the session has
 * ended elsewhere.
 */
class HeyNaijaApi(
    private val server: () -> Server = { Servers.current },
    private val token: () -> String? = { null },
    engine: HttpClientEngine? = null,
    private val onSignedOut: () -> Unit = {},
) {
    private val client = engine?.let { HttpClient(it) { setUp() } } ?: HttpClient { setUp() }

    private fun HttpClientConfig<*>.setUp() {
        install(ContentNegotiation) { json(json) }
        // Patchy mobile data: give up on a stalled request rather than spin forever.
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 30_000
        }
        // Only the API gets the token: never an upload URL, which has its own signature.
        install(createClientPlugin("Session") {
            onRequest { request, _ ->
                val token = token()
                if (token != null && request.url.buildString().startsWith(server().api)) {
                    request.headers[HttpHeaders.Authorization] = "Bearer $token"
                }
            }
        })
    }

    private fun url(path: String) = "${server().api}/v1$path"

    private suspend fun check(response: HttpResponse): HttpResponse {
        if (response.status.isSuccess()) return response
        val status = response.status.value
        if (status == 401 && response.call.request.headers[HttpHeaders.Authorization] != null) onSignedOut()
        val body = runCatching { json.parseToJsonElement(response.bodyAsText()).jsonObject }.getOrNull()
        val code = (body?.get("code") as? JsonPrimitive)?.contentOrNull
            ?: ((body?.get("message") as? JsonObject)?.get("code") as? JsonPrimitive)?.contentOrNull
        val message = when (val m = body?.get("message")) {
            is JsonPrimitive -> m.contentOrNull
            is JsonArray -> (m.firstOrNull() as? JsonPrimitive)?.contentOrNull
            is JsonObject -> (m["message"] as? JsonPrimitive)?.contentOrNull
            else -> null
        }
        throw ApiException(status, code, message)
    }

    private suspend inline fun <reified T> get(path: String, crossinline block: HttpRequestBuilder.() -> Unit = {}): T =
        check(client.get(url(path)) { block() }).body()

    private suspend fun send(method: String, path: String, body: JsonObject? = null): HttpResponse {
        val build: HttpRequestBuilder.() -> Unit = {
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        return check(
            when (method) {
                "POST" -> client.post(url(path), build)
                "PUT" -> client.put(url(path), build)
                "PATCH" -> client.patch(url(path), build)
                else -> client.delete(url(path), build)
            },
        )
    }

    private fun HttpRequestBuilder.page(cursor: String?, limit: Int = PAGE_SIZE) {
        parameter("limit", limit)
        if (cursor != null) parameter("cursor", cursor)
    }

    private fun seg(value: String) = value.encodeURLPathPart()

    // Who is signed in

    suspend fun me(): Me = get("/me")

    /** Drops the API's cached lookup of this session, before Kratos ends it. */
    suspend fun logout() {
        send("POST", "/logout")
    }

    // Feeds

    /** Latest: every post on HeyNaija, newest first, not ranked. Public. */
    suspend fun latest(cursor: String? = null, limit: Int = PAGE_SIZE): FeedPage = get("/feed") { page(cursor, limit) }

    /** Posts from people and communities the member follows. */
    suspend fun following(cursor: String? = null): FeedPage = get("/feed/following") { page(cursor) }

    /** Discover, ranked as of [at] (ISO 8601) on its first page so loading it again keeps the order. */
    suspend fun discover(cursor: String? = null, at: String? = null): FeedPage = get("/feed/discover") {
        page(cursor)
        if (cursor == null && at != null) parameter("at", at)
    }

    suspend fun bookmarks(cursor: String? = null): FeedPage = get("/bookmarks") { page(cursor) }

    suspend fun hashtag(tag: String, cursor: String? = null): FeedPage = get("/hashtags/${seg(tag)}/posts") { page(cursor) }

    /** [filter] is 'posts', 'replies' or 'media'. */
    suspend fun memberPosts(handle: String, filter: String, cursor: String? = null): FeedPage =
        get("/members/${seg(handle)}/posts") {
            parameter("filter", filter)
            page(cursor)
        }

    suspend fun communityPosts(slug: String, cursor: String? = null): FeedPage =
        get("/communities/${seg(slug)}/posts") { page(cursor) }

    // Posts and threads

    suspend fun thread(id: String, oldestFirst: Boolean = false): ThreadPage = get("/posts/${seg(id)}") {
        if (oldestFirst) parameter("order", "oldest")
    }

    suspend fun replies(id: String, cursor: String?, oldestFirst: Boolean = false): ReplyPage =
        get("/posts/${seg(id)}/replies") {
            parameter("order", if (oldestFirst) "oldest" else "newest")
            if (cursor != null) parameter("cursor", cursor)
        }

    suspend fun post(id: String): Post = get("/posts/${seg(id)}/post")

    suspend fun createPost(post: NewPost): Created {
        val body = buildJsonObject {
            put("text", post.text)
            post.replyTo?.let { put("replyTo", it) }
            post.quote?.let { put("quote", it) }
            post.community?.let { put("community", it) }
            if (post.images.isNotEmpty()) {
                put("images", buildJsonArray {
                    for ((id, alt) in post.images) add(buildJsonObject { put("id", id); put("alt", alt) })
                })
            }
            post.video?.let { (id, description) ->
                put("video", buildJsonObject { put("id", id); put("description", description) })
            }
            post.poll?.let { poll ->
                put("poll", buildJsonObject {
                    put("multiple", poll.multiple)
                    put("expiresIn", poll.expiresIn)
                    put("options", buildJsonArray { for (option in poll.options) add(JsonPrimitive(option)) })
                })
            }
            post.replyAudience?.let { put("replyAudience", it) }
            post.storyOf?.let { put("storyOf", it) }
            if (post.sensitive) put("sensitive", true)
        }
        return send("POST", "/posts", body).body()
    }

    suspend fun deletePost(id: String) { send("DELETE", "/posts/${seg(id)}") }

    suspend fun setLike(id: String, on: Boolean) { send(if (on) "PUT" else "DELETE", "/posts/${seg(id)}/like") }

    suspend fun setRepost(id: String, on: Boolean) { send(if (on) "PUT" else "DELETE", "/posts/${seg(id)}/repost") }

    suspend fun setBookmark(id: String, on: Boolean) { send(if (on) "PUT" else "DELETE", "/posts/${seg(id)}/bookmark") }

    /** A page of a story's updates, oldest first. */
    suspend fun storyUpdates(id: String, cursor: String?): FeedPage = get("/posts/${seg(id)}/updates") {
        parameter("limit", 100)
        if (cursor != null) parameter("cursor", cursor)
    }

    /** Follow a story to be told about each new update, or stop. */
    suspend fun setStoryFollow(id: String, on: Boolean) { send(if (on) "PUT" else "DELETE", "/posts/${seg(id)}/story-follow") }

    suspend fun setProfilePin(id: String, on: Boolean) { send(if (on) "PUT" else "DELETE", "/posts/${seg(id)}/pin") }

    /** The author marks their own post sensitive, or takes their own mark off (ADR 0020). */
    suspend fun setSensitive(id: String, on: Boolean) { send(if (on) "PUT" else "DELETE", "/posts/${seg(id)}/sensitive") }

    /** [audience] is 'everyone', 'following' or 'mentioned'. */
    suspend fun setReplyAudience(id: String, audience: String) {
        send("PUT", "/posts/${seg(id)}/reply-audience", buildJsonObject { put("audience", audience) })
    }

    suspend fun vote(id: String, optionIds: List<String>) {
        send("PUT", "/posts/${seg(id)}/poll/vote", buildJsonObject {
            put("optionIds", buildJsonArray { for (option in optionIds) add(JsonPrimitive(option)) })
        })
    }

    // Members

    /** A profile, or [ApiException] 404; a handle given up recently answers 404 with `movedTo`. */
    suspend fun profile(handle: String): Profile = get("/members/${seg(handle)}")

    suspend fun setFollow(handle: String, on: Boolean) { send(if (on) "PUT" else "DELETE", "/members/${seg(handle)}/follow") }

    suspend fun setBlock(handle: String, on: Boolean) { send(if (on) "PUT" else "DELETE", "/members/${seg(handle)}/block") }

    suspend fun setMute(handle: String, on: Boolean) { send(if (on) "PUT" else "DELETE", "/members/${seg(handle)}/mute") }

    suspend fun updateProfile(displayName: String, bio: String) {
        send("PUT", "/me/profile", buildJsonObject {
            put("displayName", displayName)
            put("bio", bio)
        })
    }

    /** The member's choices about posts marked sensitive (ADR 0020); a null one stays as it is. */
    suspend fun setSensitiveSettings(showMedia: Boolean? = null, markPosts: Boolean? = null) {
        send("PUT", "/me/sensitive", buildJsonObject {
            showMedia?.let { put("showMedia", it) }
            markPosts?.let { put("markPosts", it) }
        })
    }

    // Uploads: start, PUT the file to the URL given, then complete.

    /** [kind] is 'avatar' or 'images'. */
    suspend fun startUpload(kind: String, contentType: String, bytes: Int): Upload =
        send("POST", "/me/$kind/upload", buildJsonObject {
            put("contentType", contentType)
            put("bytes", bytes)
        }).body()

    suspend fun putFile(upload: Upload, contentType: String, data: ByteArray) {
        val response = client.put(upload.uploadUrl) {
            header(HttpHeaders.ContentType, contentType)
            setBody(data)
            // A photo on a slow connection takes longer than an API call.
            timeout { requestTimeoutMillis = 120_000 }
        }
        if (!response.status.isSuccess()) throw ApiException(response.status.value, "upload_failed", "The picture could not be sent.")
    }

    /** Finishes an avatar upload; answers with the new avatar's path. */
    suspend fun completeAvatar(mediaId: String): String? =
        send("POST", "/me/avatar/${seg(mediaId)}/complete").body<JsonObject>()["avatar"]?.jsonPrimitive?.contentOrNull

    suspend fun removeAvatar() { send("DELETE", "/me/avatar") }

    /** Finishes a post image's upload; it is processed in the background. */
    suspend fun completeImage(mediaId: String): UploadStatus = send("POST", "/me/images/${seg(mediaId)}/complete").body()

    /** 'processing', 'ready' or 'failed'. */
    suspend fun imageStatus(mediaId: String): UploadStatus = get("/me/images/${seg(mediaId)}")

    // A post's video (ADR 0019, 0021): parts go straight to storage, then the worker makes it ready.

    suspend fun startVideoUpload(contentType: String, bytes: Long, durationMs: Long?): VideoPlan =
        send("POST", "/me/videos/upload", buildJsonObject {
            put("contentType", contentType)
            put("bytes", bytes)
            if (durationMs != null) put("durationMs", durationMs)
        }).body()

    /** After a broken upload: the parts already stored, and fresh URLs for the rest. */
    suspend fun resumeVideoUpload(mediaId: String): VideoPlan = get("/me/videos/${seg(mediaId)}/upload")

    suspend fun putVideoPart(part: VideoPart, data: ByteArray) {
        val response = client.put(part.url) {
            setBody(data)
            timeout { requestTimeoutMillis = 300_000 }
        }
        if (!response.status.isSuccess()) throw ApiException(response.status.value, "upload_failed", "Part of the video could not be sent.")
    }

    suspend fun completeVideoUpload(mediaId: String): VideoStatus = send("POST", "/me/videos/${seg(mediaId)}/complete").body()

    suspend fun videoStatus(mediaId: String): VideoStatus = get("/me/videos/${seg(mediaId)}")

    suspend fun removeVideo(mediaId: String) { send("DELETE", "/me/videos/${seg(mediaId)}") }

    // Communities

    suspend fun communities(): List<Community> = get("/communities")

    suspend fun community(slug: String): Community = get("/communities/${seg(slug)}")

    suspend fun setMembership(slug: String, on: Boolean) {
        send(if (on) "PUT" else "DELETE", "/communities/${seg(slug)}/membership")
    }

    // Search and topics

    /** [q] of 2 to 80 characters. */
    suspend fun search(q: String): SearchResults = get("/search") { parameter("q", q) }

    /** Members whose handle starts with [q] (1 to 20 letters, numbers or _), for an @mention. */
    suspend fun mentionSuggestions(q: String): MentionPage = get("/search/mentions") { parameter("q", q) }

    /** [ApiException] 404 when trending topics are switched off. */
    suspend fun trendingTopics(): TrendingTopics = get("/topics/trending")

    // Notifications

    suspend fun notifications(cursor: String? = null): NotificationPage = get("/notifications") { page(cursor) }

    /** Marks everything up to [upTo], the newest the member has seen, as read. */
    suspend fun markRead(upTo: String) { send("POST", "/notifications/read", buildJsonObject { put("upTo", upTo) }) }

    suspend fun notificationPreferences(): Map<String, Boolean> = get("/notifications/preferences")

    suspend fun setNotificationPreference(key: String, on: Boolean): Map<String, Boolean> =
        send("PUT", "/notifications/preferences", buildJsonObject { put(key, on) }).body()

    /** Whether this server pushes notifications to the app (ADR 0023 in the web repository). */
    suspend fun pushesToApp(): Boolean = get<PushKey>("/push/key").app

    /**
     * Pushes the member's notifications to this installation from now on: its
     * [token] with the push service ([service], "fcm"), and the [key] the server
     * seals what it sends with. [ApiException] 404 when the server does not push to the app.
     */
    suspend fun registerPushDevice(service: String, token: String, key: String) {
        send("POST", "/push/devices", buildJsonObject {
            put("service", service)
            put("token", token)
            put("key", key)
        })
    }

    /** Stops pushing to this installation: before signing out. */
    suspend fun removePushDevice(service: String, token: String) {
        send("POST", "/push/devices/remove", buildJsonObject {
            put("service", service)
            put("token", token)
        })
    }

    // Safety

    /** [target] is `"postId" to id` or `"handle" to handle`. */
    suspend fun report(target: Pair<String, String>, reason: String, details: String) {
        send("POST", "/reports", buildJsonObject {
            put(target.first, target.second)
            put("reason", reason)
            put("details", details)
        })
    }

    // The welcome steps and the date of birth

    suspend fun suggestions(communities: List<String>): List<Suggestion> =
        get("/me/onboarding/suggestions") { parameter("communities", communities.joinToString(",")) }

    suspend fun completeOnboarding(communities: List<String>, follow: List<String>) {
        send("POST", "/me/onboarding/complete", buildJsonObject {
            put("communities", buildJsonArray { for (c in communities) add(JsonPrimitive(c)) })
            put("follow", buildJsonArray { for (h in follow) add(JsonPrimitive(h)) })
        })
    }

    suspend fun skipOnboarding() { send("POST", "/me/onboarding/skip") }

    /**
     * YYYY-MM-DD, for members who joined before signup asked. Answers 'recorded',
     * 'suspended' (under the minimum age, until a moderator looks), 'already_given'
     * or 'not_a_date'.
     */
    suspend fun giveBirthDate(birthDate: String): String =
        send("POST", "/me/birth-date", buildJsonObject { put("birthDate", birthDate) }).body<JsonObject>()["outcome"]
            ?.jsonPrimitive?.contentOrNull ?: "not_a_date"

    // Signup's checks, before the Kratos flow

    suspend fun inviteUsable(code: String): Boolean =
        send("POST", "/invites/check", buildJsonObject { put("code", code) }).body<JsonObject>()["usable"]
            ?.jsonPrimitive?.contentOrNull == "true"

    /** 'allowed', 'too_young' or 'not_a_date', and the youngest age that may join. */
    suspend fun checkAge(birthDate: String): Pair<String, Int> {
        val answer = send("POST", "/signup/age", buildJsonObject { put("birthDate", birthDate) }).body<JsonObject>()
        return (answer["outcome"]?.jsonPrimitive?.contentOrNull ?: "not_a_date") to
            (answer["joinAge"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 18)
    }

    // Gists (ADR 0017): every route answers 404 to a member who may not see the Gist, or Gists at all.

    /** The live Gists the member may join, newest first. */
    suspend fun liveGists(): List<LiveGist> = get<LiveGists>("/gists/live").items

    /** Starts a Gist now, with the member as host; 409 `already_hosting` when one of theirs is live. */
    suspend fun startGist(title: String): GistView = send("POST", "/gists", buildJsonObject { put("title", title) }).body()

    suspend fun gist(id: String): GistView = get("/gists/${seg(id)}")

    /** A LiveKit token and the member's role. Also the call that reconnects. */
    suspend fun joinGist(id: String): GistJoin = send("POST", "/gists/${seg(id)}/join").body()

    suspend fun leaveGist(id: String) { send("POST", "/gists/${seg(id)}/leave") }

    /** Once connected: sets the member's LiveKit permissions to their role, so a speaker may publish. */
    suspend fun syncGist(id: String) { send("POST", "/gists/${seg(id)}/sync") }

    suspend fun setSpeakRequest(id: String, on: Boolean) { send(if (on) "PUT" else "DELETE", "/gists/${seg(id)}/speak-request") }

    /** Host and co-hosts: requests to speak, oldest first. */
    suspend fun speakRequests(id: String): List<SpeakRequest> = get<SpeakRequests>("/gists/${seg(id)}/requests").items

    /** Host and co-hosts: who is listening. */
    suspend fun gistListeners(id: String): List<Person> = get<GistPeople>("/gists/${seg(id)}/listeners").items

    /** Lets a listener speak (approving a request, or inviting them). */
    suspend fun addSpeaker(id: String, memberId: String) { send("PUT", "/gists/${seg(id)}/speakers/${seg(memberId)}") }

    /** Back to the listeners; a speaker can do this to themselves. */
    suspend fun demoteSpeaker(id: String, memberId: String) { send("DELETE", "/gists/${seg(id)}/speakers/${seg(memberId)}") }

    suspend fun addCohost(id: String, memberId: String) { send("PUT", "/gists/${seg(id)}/cohosts/${seg(memberId)}") }

    suspend fun muteInGist(id: String, memberId: String) { send("POST", "/gists/${seg(id)}/members/${seg(memberId)}/mute") }

    suspend fun removeFromGist(id: String, memberId: String) { send("DELETE", "/gists/${seg(id)}/members/${seg(memberId)}") }

    /** Host only: lock the room, or open and close requests to speak. */
    suspend fun gistSettings(id: String, locked: Boolean? = null, requestsOpen: Boolean? = null): GistView =
        send("PATCH", "/gists/${seg(id)}", buildJsonObject {
            locked?.let { put("locked", it) }
            requestsOpen?.let { put("requestsOpen", it) }
        }).body()

    /** Host only: ends it for everyone. */
    suspend fun endGist(id: String) { send("POST", "/gists/${seg(id)}/end") }

    // A Gist's replies: posts read and written only through the Gist.

    suspend fun gistReplies(id: String, cursor: String? = null): GistReplies = get("/gists/${seg(id)}/replies") {
        parameter("order", "newest")
        if (cursor != null) parameter("cursor", cursor)
    }

    suspend fun gistReply(id: String, postId: String): Post = get("/gists/${seg(id)}/replies/${seg(postId)}")

    /** The replies under one reply in a Gist. */
    suspend fun gistReplyBranch(id: String, postId: String, cursor: String? = null): ReplyPage =
        get("/gists/${seg(id)}/replies/${seg(postId)}/replies") {
            parameter("order", "newest")
            if (cursor != null) parameter("cursor", cursor)
        }

    /** At the top of the Gist's replies, or under [replyTo], a reply in the same Gist. Text only. */
    suspend fun createGistReply(id: String, text: String, replyTo: String? = null): Created =
        send("POST", "/gists/${seg(id)}/replies", buildJsonObject {
            put("text", text)
            replyTo?.let { put("replyTo", it) }
        }).body()

    suspend fun setGistReplyLike(id: String, postId: String, on: Boolean) {
        send(if (on) "PUT" else "DELETE", "/gists/${seg(id)}/replies/${seg(postId)}/like")
    }

    /** Host only: hides a reply from everyone, or shows it again. */
    suspend fun hideGistReply(id: String, postId: String, hidden: Boolean) {
        send(if (hidden) "PUT" else "DELETE", "/gists/${seg(id)}/replies/${seg(postId)}/hide")
    }

    /** Host only: 'everyone', 'following', 'mentioned' or 'off'. */
    suspend fun setGistReplyAudience(id: String, audience: String) {
        send("PUT", "/gists/${seg(id)}/replies/audience", buildJsonObject { put("audience", audience) })
    }

    // Deleting the account (docs/adr/0016)

    suspend fun deletion(): Deletion = get("/me/deletion")

    /** [ApiException] with `reauth_required` when the member has not signed in within the last few minutes. */
    suspend fun requestDeletion(confirmHandle: String): Deletion =
        send("POST", "/me/deletion", buildJsonObject { put("confirmHandle", confirmHandle) }).body()

    suspend fun cancelDeletion() { send("DELETE", "/me/deletion") }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            // A null where the app expects a value takes the field's default.
            coerceInputValues = true
            explicitNulls = false
        }
    }
}

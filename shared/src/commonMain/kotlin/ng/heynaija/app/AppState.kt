package ng.heynaija.app

import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import ng.heynaija.app.api.ApiException
import ng.heynaija.app.api.HeyNaijaApi
import ng.heynaija.app.api.KratosAuth
import ng.heynaija.app.api.Me
import ng.heynaija.app.api.Post
import ng.heynaija.app.api.Server
import ng.heynaija.app.api.Servers
import ng.heynaija.app.nav.Navigator
import ng.heynaija.app.nav.Route
import ng.heynaija.app.nav.routeOf
import ng.heynaija.app.platform.KeyValueStore
import ng.heynaija.app.platform.Platform
import ng.heynaija.app.platform.PushDevice
import ng.heynaija.app.platform.PushEvents

/** Who is using the app. [Unknown] only while the app starts. */
sealed interface Account {
    data object Unknown : Account
    data object SignedOut : Account
    data class SignedIn(val me: Me) : Account
}

/** A change to a post that every list showing it should take up. */
sealed interface PostChange {
    data class Updated(val post: Post) : PostChange
    data class Deleted(val id: String) : PostChange
    /** The member posted: feeds that would show it start again from the top. */
    data class Created(val id: String) : PostChange
}

private const val TOKEN = "session_token"
private const val ME = "me"
private const val SERVER = "server"
// The push token the server was last given, and whether the member turned push off here.
private const val PUSH_TOKEN = "push_token"
private const val PUSH_OFF = "push_off"
private const val PUSH_OFFER_DISMISSED = "push_offer_dismissed"

/**
 * Push on this phone: whether the server and this build can push to it at all
 * ([available]), whether they do now ([on]), and whether the member turned it off
 * in Settings ([off]).
 */
data class PushState(val available: Boolean = false, val on: Boolean = false, val off: Boolean = false, val offerDismissed: Boolean = false)

/**
 * Who the app is signed in as, from what it keeps: for code that runs without the
 * app open, such as showing a push notification. Null when signed out.
 */
fun savedMemberId(secure: KeyValueStore, settings: KeyValueStore): String? {
    if (secure.get(TOKEN) == null) return null
    val me = settings.get(ME) ?: return null
    return runCatching { HeyNaijaApi.json.decodeFromString(Me.serializer(), me).id }.getOrNull()
}

/**
 * The app's one state holder: the session, the API, navigation, and the events
 * screens share. Screens reach it through [LocalApp].
 */
class AppState(val platform: Platform) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        if (platform.debug) Servers.current = Server.byKey(platform.settings.get(SERVER))
    }

    private var token: String? = platform.secure.get(TOKEN)

    val api = HeyNaijaApi(token = { token }, onSignedOut = { scope.launch { forget() } })
    val kratos = KratosAuth()
    val navigator = Navigator()

    private val _account = MutableStateFlow<Account>(Account.Unknown)
    val account: StateFlow<Account> = _account.asStateFlow()

    val me: Me? get() = (account.value as? Account.SignedIn)?.me

    private val _changes = MutableSharedFlow<PostChange>(extraBufferCapacity = 16)
    val changes: SharedFlow<PostChange> = _changes.asSharedFlow()

    private val _push = MutableStateFlow(
        PushState(off = platform.settings.get(PUSH_OFF) != null, offerDismissed = platform.settings.get(PUSH_OFFER_DISMISSED) != null),
    )
    val push: StateFlow<PushState> = _push.asStateFlow()

    init {
        scope.launch { PushEvents.newToken.collect { syncPush() } }
        scope.launch { PushEvents.arrived.collect { refreshMe() } }
    }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Short notes for the snackbar: "Posted", or why something did not work. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun say(message: String) {
        _messages.tryEmit(message)
    }

    fun emit(change: PostChange) {
        _changes.tryEmit(change)
    }

    /** Reads the saved session: shown at once from the last copy of `/v1/me`, then checked. */
    fun start() {
        val saved = token
        if (platform.debug) println("HeyNaija: starting ${if (saved == null) "signed out" else "with a saved session"} on ${Servers.current.label}")
        if (saved == null) {
            _account.value = Account.SignedOut
            return
        }
        val cached = platform.settings.get(ME)?.let { runCatching { HeyNaijaApi.json.decodeFromString(Me.serializer(), it) }.getOrNull() }
        _account.value = cached?.let { Account.SignedIn(it) } ?: Account.Unknown
        scope.launch {
            refreshMe(firstLoad = cached == null)
            syncPush()
        }
    }

    /** Asks `/v1/me` again: for the unread count, a changed profile, or a finished welcome. */
    suspend fun refreshMe(firstLoad: Boolean = false) {
        if (token == null) return
        try {
            val me = api.me()
            platform.settings.set(ME, HeyNaijaApi.json.encodeToString(Me.serializer(), me))
            _account.value = Account.SignedIn(me)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            if (platform.debug) println("HeyNaija: /v1/me answered ${e.status} ${e.code}")
            // 401 has already signed out (onSignedOut). A 403 is an invite that was never spent,
            // or a suspended account: neither can use the app.
            if (e.status == 403) forget()
            else if (firstLoad) _account.value = Account.SignedOut
        } catch (e: Exception) {
            // Offline at launch with nothing cached: show the app signed out until it can ask.
            if (firstLoad) _account.value = Account.SignedOut
        }
    }

    fun updateMe(change: (Me) -> Me) {
        _account.update { if (it is Account.SignedIn) Account.SignedIn(change(it.me)) else it }
    }

    /** Keeps a new session's token and loads the member. */
    suspend fun signedIn(newToken: String) {
        token = newToken
        platform.secure.set(TOKEN, newToken)
        refreshMe(firstLoad = true)
        navigator.reset()
        syncPush()
    }

    /** Ends the session here and in Kratos. */
    suspend fun signOut() {
        val ending = token ?: return
        platform.push?.let { stopPush(it) }
        runCatching { api.logout() }
        kratos.logout(ending)
        forget()
    }

    private fun forget() {
        token = null
        platform.secure.set(TOKEN, null)
        platform.settings.set(ME, null)
        _account.value = Account.SignedOut
        navigator.reset()
        // Whatever the server still sends to this phone is refused from now on, and
        // the next member's notifications are sealed with a new key.
        platform.push?.let { push ->
            scope.launch { push.deleteToken() }
            push.forgetKey()
        }
        platform.settings.set(PUSH_TOKEN, null)
        platform.settings.set(PUSH_OFF, null)
        _push.value = PushState(offerDismissed = _push.value.offerDismissed)
    }

    /**
     * Push to this phone (ADR 0023 in the web repository). Run when the app starts
     * signed in, after signing in, when the push service changes the token, and
     * when the member turns push on or off: the server gets this installation's
     * current token for the member signed in or, when notifications may not show
     * here or the member turned them off, loses it, so that email carries them.
     */
    suspend fun syncPush() {
        val push = platform.push ?: return
        if (token == null) return
        val available = try {
            api.pushesToApp()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        val off = platform.settings.get(PUSH_OFF) != null
        _push.update { it.copy(available = available, off = off) }
        if (!available) return
        if (off || !push.allowed()) {
            stopPush(push)
            _push.update { it.copy(on = false) }
            return
        }
        val device = push.token() ?: return
        try {
            api.registerPushDevice(push.service, device, push.key())
            platform.settings.set(PUSH_TOKEN, device)
            _push.update { it.copy(on = true) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (platform.debug) println("HeyNaija: push registration failed: ${e.message}")
        }
    }

    /** The app came back to the front: notifications may have been allowed or blocked in the system's settings. */
    fun pushMayHaveChanged() {
        val push = platform.push ?: return
        val state = _push.value
        if (state.available && !state.off && push.allowed() != state.on) scope.launch { syncPush() }
    }

    /** The member turned push on or off in Settings, or on from the offer in Notifications. */
    suspend fun setPushOff(off: Boolean) {
        platform.settings.set(PUSH_OFF, if (off) "1" else null)
        syncPush()
    }

    fun dismissPushOffer() {
        platform.settings.set(PUSH_OFFER_DISMISSED, "1")
        _push.update { it.copy(offerDismissed = true) }
    }

    /** Takes this phone's push back from the server, while the session still works. */
    private suspend fun stopPush(push: PushDevice) {
        val registered = platform.settings.get(PUSH_TOKEN) ?: return
        try {
            api.removePushDevice(push.service, registered)
            platform.settings.set(PUSH_TOKEN, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Tried again next time; the server keeps pushing until then.
        }
    }

    /** A tapped notification's link: opened on the Notifications tab, where it would have been seen. */
    fun openLink(link: String) {
        navigator.openFromNotification(routeOf(link, Servers.current.web) ?: Route.Notifications)
    }

    val currentToken: String? get() = token

    fun switchServer(server: Server) {
        if (server == Servers.current) return
        scope.launch {
            signOut()
            Servers.current = server
            platform.settings.set(SERVER, server.key)
            forget()
        }
    }

    /** Runs [block] for a member, or sends a visitor to sign in. */
    fun requireMember(block: (Me) -> Unit) {
        val me = me
        if (me != null) block(me) else navigator.push(Route.SignIn)
    }

    /**
     * Changes a post at once on every screen ([optimistic]), then asks the API; if
     * that fails, puts it back and says why.
     */
    fun act(before: Post, optimistic: Post, failure: String, call: suspend () -> Unit) {
        emit(PostChange.Updated(optimistic))
        scope.launch {
            try {
                call()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emit(PostChange.Updated(before))
                say((e as? ApiException)?.message?.takeIf { e.code != null } ?: failure)
            }
        }
    }

    /** A reply in a Gist ([gist]) is liked through the Gist. */
    fun toggleLike(post: Post, gist: String? = null) = requireMember {
        val on = !post.viewerLiked
        act(post, post.copy(viewerLiked = on, likeCount = (post.likeCount + if (on) 1 else -1).coerceAtLeast(0)), "That like did not go through.") {
            if (gist != null) api.setGistReplyLike(gist, post.id, on) else api.setLike(post.id, on)
        }
    }

    /** The Gist's host hides a reply from everyone, or shows it again. */
    fun hideGistReply(gist: String, post: Post, hidden: Boolean) {
        scope.launch {
            try {
                api.hideGistReply(gist, post.id, hidden)
                // Shown again: the stub in hand has no text, so read the reply afresh.
                emit(PostChange.Updated(if (hidden) post.copy(unavailable = true, hiddenByHost = true) else api.gistReply(gist, post.id)))
                say(if (hidden) "Reply hidden" else "Reply shown")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say((e as? ApiException)?.message?.takeIf { e.code != null } ?: "That did not work. Try again.")
            }
        }
    }

    fun toggleRepost(post: Post) = requireMember {
        val on = !post.viewerReposted
        act(post, post.copy(viewerReposted = on, repostCount = (post.repostCount + if (on) 1 else -1).coerceAtLeast(0)), "That repost did not go through.") {
            api.setRepost(post.id, on)
        }
        if (on) say("Reposted")
    }

    fun toggleBookmark(post: Post) = requireMember {
        val on = !post.viewerBookmarked
        act(post, post.copy(viewerBookmarked = on), "That bookmark did not save.") { api.setBookmark(post.id, on) }
        say(if (on) "Saved to your bookmarks" else "Removed from your bookmarks")
    }

    fun delete(post: Post) {
        scope.launch {
            try {
                api.deletePost(post.id)
                emit(PostChange.Deleted(post.id))
                say("Post deleted")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say("The post could not be deleted. Try again.")
            }
        }
    }

    /** The address of a post on the web, to share. */
    fun postLink(post: Post) = "${Servers.current.web}/p/${post.id}"
}

val LocalApp = staticCompositionLocalOf<AppState> { error("AppState is provided by App()") }

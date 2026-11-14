package ng.heynaija.app.gists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import ng.heynaija.app.AppState
import ng.heynaija.app.PostChange
import ng.heynaija.app.api.ApiException
import ng.heynaija.app.api.GistView
import ng.heynaija.app.api.Person
import ng.heynaija.app.api.Post
import ng.heynaija.app.api.SpeakRequest
import ng.heynaija.app.platform.GistAudio
import ng.heynaija.app.platform.GistAudioEvents
import ng.heynaija.app.reasonOf

/**
 * Where the member is with the room. [Missing]: the Gist is gone, or they may not
 * see it (removed, blocked): the API makes these the same 404.
 */
enum class Phase { Loading, Missing, LoadFailed, Prejoin, Connecting, Live, Reconnecting, Ended, Full, Locked, Failed, Elsewhere, Gone }

data class GistState(
    val view: GistView? = null,
    val phase: Phase = Phase.Loading,
    /** Why joining or an action failed, in words to show. */
    val problem: String? = null,
    val micOn: Boolean = false,
    val micBusy: Boolean = false,
    val micProblem: String? = null,
    /** Member ids: who is speaking now, whose microphone is live, whose connection is weak. */
    val speaking: Set<String> = emptySet(),
    val mics: Set<String> = emptySet(),
    val weak: Set<String> = emptySet(),
    /** Host and co-hosts: requests to speak, oldest first. */
    val requests: List<SpeakRequest> = emptyList(),
    /** A host action is on its way. */
    val busy: Boolean = false,
    /** Host and co-hosts, once the list is opened. */
    val listeners: List<Person>? = null,
    val replies: Replies = Replies(),
) {
    val polling get() = phase == Phase.Live || phase == Phase.Reconnecting
}

/** The Gist's replies: posts, read and written only through the Gist. */
data class Replies(
    val posts: List<Post> = emptyList(),
    val cursor: String? = null,
    val count: Int = 0,
    val canReply: Boolean = false,
    val viewerIsHost: Boolean = false,
    val audience: String = "everyone",
    val loaded: Boolean = false,
    val failed: Boolean = false,
    val loadingMore: Boolean = false,
)

private enum class Outcome { Ok, Retry, Stop }

/**
 * One visit to a Gist: the join / reconnect / leave state machine of the web's
 * gist-session.ts, polling the room (there is no realtime channel), the
 * microphone, the host's controls, and the replies.
 *
 * Rules it keeps, as the web does: every await in an attempt is followed by a
 * staleness check (the member left, or a newer attempt began); whatever a stale
 * attempt made is let go, and /leave is sent once if the API was told the member
 * joined; and why a connection ended decides what comes next (a network drop
 * reconnects, a duplicate or a removal never does).
 *
 * The visit lasts as long as the screen is on the stack: a screen pushed on top
 * (a report, a profile) keeps the sound playing; going back leaves.
 */
class GistViewModel(private val app: AppState, val id: String) : ViewModel() {
    private val api = app.api
    private val _state = MutableStateFlow(GistState())
    val state: StateFlow<GistState> = _state.asStateFlow()

    private var gen = 0
    private var closed = false
    private var ended = false
    private var present = false
    private var reconnecting = false
    private var audio: GistAudio? = null
    private val latest = Latest()
    private var lastRole: String? = null
    private var lastStatus = "live"
    private var visible = true
    private var pollNow: Job? = null

    init {
        load()
        loadReplies()
        viewModelScope.launch {
            var failures = 0
            var sinceReplies = 0L
            while (true) {
                val wait = nextPollDelay(failures)
                delay(wait)
                failures = if (_state.value.polling && visible) (if (poll()) 0 else failures + 1) else 0
                // Replies come in while people talk; there is no push, so look again now and then.
                sinceReplies += wait
                if (visible && sinceReplies >= LIVE_POLL_MS) {
                    sinceReplies = 0
                    loadReplies()
                }
            }
        }
        viewModelScope.launch {
            app.changes.collect { change ->
                when (change) {
                    is PostChange.Updated -> updateReplies { r -> r.copy(posts = r.posts.map { if (it.id == change.post.id) change.post else it }) }
                    is PostChange.Deleted -> updateReplies { r ->
                        if (r.posts.none { it.id == change.id }) r
                        else r.copy(posts = r.posts.filterNot { it.id == change.id }, count = (r.count - 1).coerceAtLeast(0))
                    }
                    // A reply was posted, here or under one of these: show it.
                    is PostChange.Created -> loadReplies()
                }
            }
        }
    }

    fun load() {
        _state.update { it.copy(phase = Phase.Loading) }
        viewModelScope.launch {
            try {
                val view = api.gist(id)
                lastRole = view.viewer.role
                lastStatus = view.status
                _state.update { it.copy(view = view, phase = if (view.status == "ended") Phase.Ended else Phase.Prejoin) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(phase = if ((e as? ApiException)?.status == 404) Phase.Missing else Phase.LoadFailed) }
            }
        }
    }

    // ---- joining, reconnecting, leaving ----------------------------------------------

    private fun stale(my: Int) = closed || my != gen

    private fun setPhase(phase: Phase) = _state.update {
        if (phase == Phase.Live || phase == Phase.Reconnecting) it.copy(phase = phase)
        else it.copy(phase = phase, micOn = false, micBusy = false, speaking = emptySet(), mics = emptySet(), weak = emptySet(), requests = emptyList())
    }

    /** Stops the sound. Sends /leave once if the API thinks the member is in and [leave] is set. */
    private fun teardown(leave: Boolean) {
        audio?.disconnect()
        audio = null
        if (leave && present) {
            // Not the view model's scope: leaving the screen cancels that, and the API should still hear.
            app.scope.launch { runCatching { api.leaveGist(id) } }
        }
        present = false
        _state.update { it.copy(micOn = false, micBusy = false) }
    }

    private fun bail(): Outcome {
        if (closed) teardown(leave = true)
        return Outcome.Stop
    }

    /** One join: a token from the API, a fresh connection, then /sync. */
    private suspend fun connectOnce(my: Int): Outcome {
        val joined = try {
            api.joinGist(id).also { present = true }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (stale(my)) return bail()
            val refused = e as? ApiException
            when (refused?.let { joinFailure(it.status, it.code) } ?: JoinFailure.Retry) {
                JoinFailure.NotFound -> {
                    // Removed, or no longer visible.
                    setPhase(Phase.Missing)
                    return Outcome.Stop
                }
                JoinFailure.Full -> { setPhase(Phase.Full); return Outcome.Stop }
                JoinFailure.Locked -> { setPhase(Phase.Locked); return Outcome.Stop }
                JoinFailure.Ended -> { finish(); return Outcome.Stop }
                JoinFailure.Retry -> {
                    _state.update { it.copy(problem = reasonOf(e) ?: "Could not reach the Gist. Check your connection.") }
                    return Outcome.Retry
                }
            }
        }
        if (stale(my)) return bail()
        lastRole = joined.role
        lastStatus = joined.gist.status
        _state.update { it.copy(view = joined.gist) }
        val factory = app.platform.gistAudio
        if (factory == null) {
            _state.update { it.copy(problem = "Gists cannot play on this device.") }
            return Outcome.Retry
        }
        // Every attempt starts a new connection: a dropped one is never reused, and its microphone went with it.
        audio?.disconnect()
        val made = factory.create(events(my))
        audio = made
        _state.update { it.copy(micOn = false, micBusy = false) }
        val failure = suspendCancellableCoroutine { done -> made.connect(joined.url, joined.token) { done.resume(it) } }
        if (stale(my)) return bail()
        if (failure != null) {
            if (app.platform.debug) println("HeyNaija: the Gist's audio did not connect: $failure")
            _state.update { it.copy(problem = "Could not connect the audio. Check your connection and try again.") }
            return Outcome.Retry
        }
        // The server lets a speaker publish only after this; the poll repeats it if the role changes.
        sync()
        return Outcome.Ok
    }

    private fun events(my: Int) = object : GistAudioEvents {
        private val current get() = my == gen && !closed

        override fun onReconnecting() { if (current) setPhase(Phase.Reconnecting) }

        override fun onReconnected() { if (current) setPhase(Phase.Live) }

        override fun onLost(reason: String) { if (current) viewModelScope.launch { lost(reason) } }

        override fun onSpeaking(ids: List<String>) { if (current) _state.update { it.copy(speaking = ids.toSet()) } }

        override fun onMics(ids: List<String>) { if (current) _state.update { it.copy(mics = ids.toSet()) } }

        override fun onWeak(ids: List<String>) { if (current) _state.update { it.copy(weak = ids.toSet()) } }

        override fun onMicOff() { if (current) _state.update { it.copy(micOn = false) } }
    }

    private fun sync() {
        viewModelScope.launch { runCatching { api.syncGist(id) } }
    }

    fun join() {
        if (closed) return
        val my = ++gen
        _state.update { it.copy(problem = null) }
        setPhase(Phase.Connecting)
        viewModelScope.launch {
            val result = connectOnce(my)
            if (stale(my)) return@launch
            when (result) {
                Outcome.Retry -> {
                    // A /join may have answered before the sound failed to connect: leave no ghost listener behind.
                    teardown(leave = true)
                    setPhase(Phase.Failed)
                }
                Outcome.Stop -> Unit
                Outcome.Ok -> setPhase(Phase.Live)
            }
        }
    }

    /** The connection ended; why decides what happens next. */
    private suspend fun lost(reason: String) {
        if (closed || ended) return
        if (reason == "network") return reconnect()
        // Anything else is final: no automatic rejoin.
        gen++
        if (reason == "room_deleted") return finish()
        // Another device has the presence now, or the member was removed: no /leave.
        present = false
        teardown(leave = false)
        setPhase(if (reason == "duplicate") Phase.Elsewhere else Phase.Gone)
    }

    private suspend fun reconnect() {
        if (reconnecting || closed || ended) return
        reconnecting = true
        val my = ++gen
        try {
            setPhase(Phase.Reconnecting)
            repeat(RECONNECT_ATTEMPTS) { attempt ->
                delay(reconnectDelay(attempt))
                if (stale(my)) { bail(); return }
                // Every reconnect asks the API again: a new token, and the role as it is now.
                val result = connectOnce(my)
                if (stale(my)) return
                when (result) {
                    Outcome.Ok -> {
                        _state.update { it.copy(problem = null) }
                        setPhase(Phase.Live)
                        return
                    }
                    Outcome.Stop -> return
                    Outcome.Retry -> Unit
                }
            }
            // Out of attempts: stop, and let the member decide.
            teardown(leave = true)
            setPhase(Phase.Failed)
        } finally {
            reconnecting = false
        }
    }

    /** The Gist ended: a poll, a refused join, or the room was closed. */
    private fun finish() {
        ended = true
        gen++
        teardown(leave = false)
        setPhase(Phase.Ended)
    }

    /** The Leave button. */
    fun leave() {
        closed = true
        gen++
        teardown(leave = true)
    }

    override fun onCleared() {
        // Going back from the screen by any route leaves, as closing the page does on the web.
        if (!closed) leave()
    }

    /** The app is on screen or not: no polling in the background, and a fresh look on coming back. */
    fun setVisible(on: Boolean) {
        visible = on
        if (on && _state.value.polling) {
            pollNow?.cancel()
            pollNow = viewModelScope.launch { poll() }
        }
    }

    // ---- following the room by polling ----------------------------------------------

    /** One look at the room; false when it could not be read. */
    private suspend fun poll(): Boolean {
        val ticket = latest.begin()
        val view = try {
            api.gist(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if ((e as? ApiException)?.status == 404) {
                gen++
                teardown(leave = false)
                setPhase(Phase.Missing)
                return true
            }
            return false
        }
        // An older answer, or one fetched before the member's last action, must not overwrite a newer view.
        if (!latest.accept(ticket)) return true
        val next = applyView(lastRole, lastStatus, view)
        lastRole = next.role
        lastStatus = next.status
        _state.update { it.copy(view = view) }
        next.announcement?.let(app::say)
        if (next.ended) {
            finish()
            return true
        }
        if (next.resync) {
            // A demotion takes the microphone: switch it off here too, then match LiveKit to the role.
            if (view.viewer.role == "listener" && _state.value.micOn) setMicNow(false)
            sync()
        }
        val role = view.viewer.role
        if (role == "host" || role == "cohost") {
            runCatching { api.speakRequests(id) }.onSuccess { requests -> _state.update { it.copy(requests = requests) } }
        } else {
            _state.update { it.copy(requests = emptyList()) }
        }
        return true
    }

    /** After something the member did: views asked for before now are out of date, so look again. */
    private suspend fun refreshAfterAction() {
        latest.noteAction()
        poll()
    }

    // ---- the member's own controls ------------------------------------------------------

    /** Turns the microphone on or off; the screen has asked the system for the permission first. */
    fun setMic(on: Boolean) {
        if (_state.value.micBusy) return
        viewModelScope.launch { setMicNow(on) }
    }

    private suspend fun setMicNow(on: Boolean) {
        val made = audio ?: return
        _state.update { it.copy(micBusy = true, micProblem = null) }
        val failure = suspendCancellableCoroutine { done -> made.setMicrophone(on) { done.resume(it) } }
        // The connection was replaced meanwhile (a reconnect): the new one starts muted.
        if (made !== audio) return
        if (failure == null) {
            _state.update { it.copy(micOn = on, micBusy = false) }
            app.say(if (on) "Microphone on" else "You are muted")
        } else {
            if (app.platform.debug) println("HeyNaija: the microphone did not ${if (on) "start" else "stop"}: $failure")
            _state.update {
                it.copy(
                    micOn = false,
                    micBusy = false,
                    micProblem = if (failure == "denied") MIC_DENIED else "The microphone did not start. Try again. You are still listening.",
                )
            }
        }
    }

    /** The system said no to the microphone. */
    fun micDenied() = _state.update { it.copy(micProblem = MIC_DENIED) }

    fun requestToSpeak(on: Boolean) {
        _state.update { it.copy(problem = null) }
        viewModelScope.launch {
            try {
                api.setSpeakRequest(id, on)
                app.say(if (on) "Request sent. The host will see it." else "Request withdrawn.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(problem = reasonOf(e) ?: "That did not work. Try again.") }
            }
            refreshAfterAction()
        }
    }

    // ---- host and co-host controls -------------------------------------------------------

    private fun act(done: String, call: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, problem = null) }
        viewModelScope.launch {
            try {
                call()
                app.say(done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                app.say(reasonOf(e) ?: "That did not work. Try again.")
            }
            _state.update { it.copy(busy = false) }
            refreshAfterAction()
            if (_state.value.listeners != null) loadListeners()
        }
    }

    fun approve(p: Person) = act("${p.name} can speak.") { api.addSpeaker(id, p.id) }

    fun demote(p: Person) = act("${p.name} is a listener again.") { api.demoteSpeaker(id, p.id) }

    fun mute(p: Person) = act("${p.name} is muted.") { api.muteInGist(id, p.id) }

    fun remove(p: Person) = act("${p.name} was removed.") { api.removeFromGist(id, p.id) }

    fun makeCohost(p: Person) = act("${p.name} is a co-host.") { api.addCohost(id, p.id) }

    fun setLocked(on: Boolean) =
        act(if (on) "The room is locked. No new listeners can join." else "The room is unlocked.") { api.gistSettings(id, locked = on) }

    fun setRequestsOpen(on: Boolean) =
        act(if (on) "Requests to speak are on." else "Requests to speak are off.") { api.gistSettings(id, requestsOpen = on) }

    fun end() = act("The Gist has ended.") { api.endGist(id) }

    fun loadListeners() {
        viewModelScope.launch {
            runCatching { api.gistListeners(id) }.onSuccess { people -> _state.update { it.copy(listeners = people) } }
        }
    }

    fun closeListeners() = _state.update { it.copy(listeners = null) }

    // ---- replies ----------------------------------------------------------------------

    private fun updateReplies(change: (Replies) -> Replies) = _state.update { it.copy(replies = change(it.replies)) }

    fun loadReplies() {
        viewModelScope.launch {
            try {
                val page = api.gistReplies(id)
                updateReplies {
                    Replies(page.replies, page.cursor, page.count, page.viewerCanReply, page.viewerIsHost, page.replyAudience, loaded = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateReplies { it.copy(failed = !it.loaded) }
            }
        }
    }

    fun moreReplies() {
        val r = _state.value.replies
        val cursor = r.cursor ?: return
        if (r.loadingMore) return
        updateReplies { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val page = api.gistReplies(id, cursor)
                updateReplies { it.copy(posts = (it.posts + page.replies).distinctBy(Post::id), cursor = page.cursor, loadingMore = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateReplies { it.copy(loadingMore = false) }
            }
        }
    }

    fun postReply(text: String, onDone: () -> Unit) {
        val t = text.trim()
        if (t.isEmpty()) return
        viewModelScope.launch {
            try {
                api.createGistReply(id, t)
                onDone()
                loadReplies()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                app.say(reasonOf(e) ?: "Could not post your reply. Try again.")
            }
        }
    }

    fun setReplyAudience(audience: String) {
        val before = _state.value.replies.audience
        updateReplies { it.copy(audience = audience) }
        viewModelScope.launch {
            try {
                api.setGistReplyAudience(id, audience)
                app.say(GIST_REPLY_AUDIENCES.first { it.first == audience }.second)
                loadReplies()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateReplies { it.copy(audience = before) }
                app.say(reasonOf(e) ?: "That did not save. Try again.")
            }
        }
    }
}

private const val MIC_DENIED =
    "HeyNaija may not use the microphone, so you are still listening. Allow the microphone for HeyNaija in your phone's settings, then tap the microphone again."

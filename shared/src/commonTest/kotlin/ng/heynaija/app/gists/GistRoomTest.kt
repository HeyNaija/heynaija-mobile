package ng.heynaija.app.gists

import io.ktor.http.HttpStatusCode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import ng.heynaija.app.PostChange
import ng.heynaija.app.api.FakeServer
import ng.heynaija.app.platform.GistAudio
import ng.heynaija.app.platform.GistAudioEvents
import ng.heynaija.app.platform.GistAudioFactory

/** A room that connects at once and keeps what it was asked; the test plays the server's part through [events]. */
private class FakeAudio : GistAudioFactory, GistAudio {
    lateinit var events: GistAudioEvents
    val mic = mutableListOf<Boolean>()
    var disconnects = 0

    override fun create(events: GistAudioEvents): GistAudio = also { this.events = events }

    override fun connect(url: String, token: String, done: (String?) -> Unit) = done(null)

    override fun setMicrophone(on: Boolean, done: (String?) -> Unit) {
        mic += on
        done(null)
    }

    override fun disconnect() {
        disconnects++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GistRoomTest {
    // One dispatcher for the room and the API, so every step happens where the test takes it, in the test's own time.
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun gist(role: String?) = """
        { "id": "g1", "title": "Jollof wars", "status": "live", "community": null,
          "host": { "id": "me", "handle": "amaka", "displayName": "Amaka" }, "speakers": [],
          "locked": false, "requestsOpen": true, "listenerCap": 500, "listenerCount": 0, "peakListeners": 0,
          "startedAt": "2026-11-07T10:00:00.000Z", "endedAt": null,
          "viewer": { "role": ${role?.let { "\"$it\"" } ?: "null"}, "requestedToSpeak": false } }
    """.trimIndent()

    /** The member hosts the Gist "g1". */
    private val server = FakeServer(dispatcher = dispatcher) { request ->
        when (val path = request.url.encodedPath.removePrefix("/api/v1/gists/g1")) {
            "" -> HttpStatusCode.OK to gist("host")
            "/join" -> HttpStatusCode.OK to """{ "url": "wss://rtc.test", "token": "jwt", "role": "host", "gist": ${gist("host")} }"""
            "/sync", "/leave" -> HttpStatusCode.OK to "{}"
            "/requests" -> HttpStatusCode.OK to """{ "items": [] }"""
            "/replies" -> HttpStatusCode.OK to """{ "replies": [], "cursor": null, "count": 0, "viewerCanReply": true, "viewerIsHost": true, "replyAudience": "everyone" }"""
            else -> HttpStatusCode.NotFound to """{ "message": "No $path" }"""
        }
    }

    private val audio = FakeAudio()
    private val said = mutableListOf<String>()

    private fun asked(path: String) = server.requests.count { it.url.encodedPath == "/api/v1/gists/g1$path" }

    /**
     * Runs [test] in the room, as its host, the moment it shows as live: the server has not yet let this
     * connection publish. The room's own work (its polling) is stopped before the test ends, so nothing of it
     * outlives the test's main dispatcher.
     */
    private fun inTheRoom(test: suspend TestScope.(GistRoom) -> Unit) = runTest {
        val room = GistRoom("g1", server.api, audio, MutableSharedFlow<PostChange>(), { said += it }, CoroutineScope(SupervisorJob() + dispatcher), debug = false)
        try {
            assertEquals(Phase.Prejoin, room.state.value.phase)
            room.join()
            assertEquals(Phase.Live, room.state.value.phase)
            test(room)
        } finally {
            room.dispose()
        }
    }

    @Test
    fun aTapBeforeTheRoomAllowsTheMicrophoneWaitsForItInsteadOfFailing() = inTheRoom { room ->
        val before = asked("/sync")

        room.setMic(true)

        // Nothing is published while the room would refuse it, and the button shows it is working on it.
        assertTrue(audio.mic.isEmpty())
        assertTrue(room.state.value.micBusy)
        assertNull(room.state.value.micProblem)
        // The server is asked again, in case the first /sync was lost.
        assertEquals(before + 1, asked("/sync"))

        audio.events.onMayPublish(true)

        assertEquals(listOf(true), audio.mic)
        assertTrue(room.state.value.micOn)
        assertFalse(room.state.value.micBusy)
        assertNull(room.state.value.micProblem)
        assertTrue("Microphone on" in said)
    }

    @Test
    fun aMicrophoneTheRoomAlreadyAllowsTurnsOnAtOnce() = inTheRoom { room ->
        audio.events.onMayPublish(true)
        val before = asked("/sync")

        room.setMic(true)

        assertEquals(listOf(true), audio.mic)
        assertTrue(room.state.value.micOn)
        assertEquals(before, asked("/sync"))
    }

    @Test
    fun aMicrophoneTheRoomNeverAllowsStaysOffAndSaysSo() = inTheRoom { room ->
        room.setMic(true)
        advanceTimeBy(STAGE_WAIT_MS - 1)
        assertTrue(room.state.value.micBusy)

        advanceTimeBy(2)

        val state = room.state.value
        assertTrue(audio.mic.isEmpty())
        assertFalse(state.micOn)
        assertFalse(state.micBusy)
        assertTrue("still listening" in state.micProblem.orEmpty())

        // And the next tap, once the room allows it, works.
        audio.events.onMayPublish(true)
        room.setMic(true)
        assertTrue(room.state.value.micOn)
        assertNull(room.state.value.micProblem)
    }

    @Test
    fun mutingNeverWaitsForTheRoom() = inTheRoom { room ->
        audio.events.onMayPublish(true)
        room.setMic(true)
        // A host made them a listener: the right to publish goes, and muting must still work.
        audio.events.onMayPublish(false)

        room.setMic(false)

        assertEquals(listOf(true, false), audio.mic)
        assertFalse(room.state.value.micOn)
        assertFalse(room.state.value.micBusy)
    }

    @Test
    fun aNewConnectionWaitsForTheRoomAgain() = inTheRoom { room ->
        audio.events.onMayPublish(true)
        // The connection drops and the room joins again: the new connection is a listener's until the server says otherwise.
        audio.events.onLost("network")
        advanceTimeBy(reconnectDelay(0) + 1)
        assertEquals(Phase.Live, room.state.value.phase)

        room.setMic(true)

        assertEquals(emptyList(), audio.mic)
        audio.events.onMayPublish(true)
        assertEquals(listOf(true), audio.mic)
    }

    @Test
    fun leavingClosesTheRoomOnceAndTellsTheApi() = inTheRoom { room ->
        room.leave()

        assertEquals(1, audio.disconnects)
        assertEquals(1, asked("/leave"))
    }

    @Test
    fun aRoomTheMemberLeftCanBeJoinedAgain() = inTheRoom { room ->
        audio.events.onMayPublish(true)
        room.setMic(true)

        room.leave()

        // Out, with the microphone off, and offered the way back in: its screen may still be open.
        assertEquals(Phase.Prejoin, room.state.value.phase)
        assertFalse(room.state.value.micOn)
        assertFalse(room.state.value.inRoom)

        room.join()

        assertEquals(Phase.Live, room.state.value.phase)
        assertEquals(2, asked("/join"))
        // A second Leave is for the second visit only.
        room.leave()
        assertEquals(2, asked("/leave"))
        assertEquals(2, audio.disconnects)
    }

    @Test
    fun leavingARoomTheMemberIsNotInDoesNothing() = runTest {
        val room = GistRoom("g1", server.api, audio, MutableSharedFlow<PostChange>(), { said += it }, CoroutineScope(SupervisorJob() + dispatcher), debug = false)
        try {
            room.leave()

            assertEquals(Phase.Prejoin, room.state.value.phase)
            assertEquals(0, asked("/leave"))
            assertEquals(0, audio.disconnects)
        } finally {
            room.dispose()
        }
    }

    @Test
    fun theRoomIsFollowedOnlyWhileTheAppIsOnScreenAndRepliesOnlyWhileItsScreenIs() = runTest {
        var foreground = true
        val room = GistRoom("g1", server.api, audio, MutableSharedFlow<PostChange>(), { said += it }, CoroutineScope(SupervisorJob() + dispatcher), debug = false, foreground = { foreground })
        try {
            room.join()
            val views = asked("")
            val replies = asked("/replies")

            // Over another screen of the app: the room is still followed (the bar shows it), its replies are not.
            advanceTimeBy(LIVE_POLL_MS + 1)
            assertTrue(asked("") > views)
            assertEquals(replies, asked("/replies"))

            // Its own screen is open: replies too.
            room.shown(true)
            advanceTimeBy(LIVE_POLL_MS + 1)
            assertTrue(asked("/replies") > replies)

            // The app is off screen: nothing is asked, though the member is still in the room.
            foreground = false
            val quiet = server.requests.size
            advanceTimeBy(LIVE_POLL_MS * 2)
            assertEquals(quiet, server.requests.size)
            assertEquals(Phase.Live, room.state.value.phase)
        } finally {
            room.dispose()
        }
    }

    @Test
    fun lettingGoOfARoomTheMemberIsInLeavesIt() = runTest {
        val room = GistRoom("g1", server.api, audio, MutableSharedFlow<PostChange>(), { said += it }, CoroutineScope(SupervisorJob() + dispatcher), debug = false)
        room.join()

        room.dispose()

        assertEquals(1, audio.disconnects)
        assertEquals(1, asked("/leave"))
    }
}

package ng.heynaija.app.gists

import io.ktor.http.HttpStatusCode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import ng.heynaija.app.platform.GistKeeper
import ng.heynaija.app.platform.GistNow

@OptIn(ExperimentalCoroutinesApi::class)
class GistRoomsTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Every connection the app made, in order: each connects at once and lets the member publish. */
    private class Connection(val events: GistAudioEvents) : GistAudio {
        var open = true

        override fun connect(url: String, token: String, done: (String?) -> Unit) {
            done(null)
            events.onMayPublish(true)
        }

        override fun setMicrophone(on: Boolean, done: (String?) -> Unit) = done(null)

        var heard = true

        override fun setHearing(on: Boolean) {
            heard = on
        }

        override fun disconnect() {
            open = false
        }
    }

    private val connections = mutableListOf<Connection>()
    private val audio = object : GistAudioFactory {
        override fun create(events: GistAudioEvents): GistAudio = Connection(events).also { connections += it }
    }

    /** What the system was told, in order: a [GistNow] for each keep, null for a stop. */
    private val told = mutableListOf<GistNow?>()
    private var leaveFromNotification: (() -> Unit)? = null
    private var hearFromLockScreen: ((Boolean) -> Unit)? = null
    private val keeper = object : GistKeeper {
        override fun keep(now: GistNow, leave: () -> Unit, hear: (Boolean) -> Unit) {
            told += now
            leaveFromNotification = leave
            hearFromLockScreen = hear
        }

        override fun stop() {
            told += null
        }
    }

    private fun gist(id: String) = """
        { "id": "$id", "title": "Gist $id", "status": "live", "community": null,
          "host": { "id": "me", "handle": "amaka", "displayName": "Amaka" }, "speakers": [],
          "locked": false, "requestsOpen": true, "listenerCap": 500, "listenerCount": 0, "peakListeners": 0,
          "startedAt": "2026-11-07T10:00:00.000Z", "endedAt": null,
          "viewer": { "role": "host", "requestedToSpeak": false } }
    """.trimIndent()

    /** The member hosts every Gist asked for. */
    private val server = FakeServer(dispatcher = dispatcher) { request ->
        val parts = request.url.encodedPath.removePrefix("/api/v1/gists/").split('/', limit = 2)
        val id = parts[0]
        when (parts.getOrNull(1)) {
            null -> HttpStatusCode.OK to gist(id)
            "join" -> HttpStatusCode.OK to """{ "url": "wss://rtc.test", "token": "jwt", "role": "host", "gist": ${gist(id)} }"""
            "sync", "leave" -> HttpStatusCode.OK to "{}"
            "requests" -> HttpStatusCode.OK to """{ "items": [] }"""
            "replies" -> HttpStatusCode.OK to """{ "replies": [], "cursor": null, "count": 0, "viewerCanReply": true, "viewerIsHost": true, "replyAudience": "everyone" }"""
            else -> HttpStatusCode.NotFound to """{ "message": "No ${request.url.encodedPath}" }"""
        }
    }

    private fun asked(path: String) = server.requests.count { it.url.encodedPath == "/api/v1/gists/$path" }

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val made = mutableListOf<GistRoom>()
    private val rooms = GistRooms(keeper, scope) { id, onPresence, foreground ->
        GistRoom(id, server.api, audio, MutableSharedFlow<PostChange>(), {}, scope, debug = false, onPresence, foreground).also { made += it }
    }

    /** Stops what the rooms left running (their polling), so nothing outlives the test's main dispatcher. */
    private fun withRooms(test: suspend TestScope.() -> Unit) = runTest {
        try {
            test()
        } finally {
            made.forEach { it.dispose() }
            scope.cancel()
        }
    }

    @Test
    fun aRoomTheMemberJoinedOutlivesItsScreen() = withRooms {
        val room = rooms.open("g1")
        room.join()
        assertSame(room, rooms.current.value)

        // Back out of the room's screen, to read something else.
        rooms.close("g1")

        assertSame(room, rooms.current.value)
        assertEquals(Phase.Live, room.state.value.phase)
        assertTrue(connections.single().open)
        assertEquals(0, asked("g1/leave"))
        // Opening its screen again shows the same room, still live.
        assertSame(room, rooms.open("g1"))
    }

    @Test
    fun aRoomNobodyIsInGoesWithItsLastScreen() = withRooms {
        val first = rooms.open("g1")
        // The same Gist opened from another tab shares the room.
        assertSame(first, rooms.open("g1"))

        rooms.close("g1")
        assertSame(first, rooms.open("g1"))
        rooms.close("g1")
        rooms.close("g1")

        assertNotSame(first, rooms.open("g1"))
        assertNull(rooms.current.value)
    }

    @Test
    fun leavingFromOutsideItsScreenLetsTheRoomGo() = withRooms {
        val room = rooms.open("g1")
        room.join()
        rooms.close("g1")

        // The bar's Leave.
        room.leave()

        assertNull(rooms.current.value)
        assertFalse(connections.single().open)
        assertEquals(1, asked("g1/leave"))
        assertNotSame(room, rooms.open("g1"))
    }

    @Test
    fun joiningAnotherGistLeavesTheFirst() = withRooms {
        val first = rooms.open("g1")
        first.join()
        rooms.close("g1")
        val second = rooms.open("g2")

        second.join()

        assertSame(second, rooms.current.value)
        assertEquals(Phase.Live, second.state.value.phase)
        assertEquals(1, asked("g1/leave"))
        assertEquals(listOf(false, true), connections.map { it.open })
        // The system is told about the second, and was never told the member is out.
        assertEquals("Gist g2", told.last()?.title)
        assertFalse(null in told)
    }

    @Test
    fun theSystemIsToldWhatTheRoomIsDoingAndCanLeaveIt() = withRooms {
        val room = rooms.open("g1")
        assertTrue(told.isEmpty())

        room.join()
        assertEquals(GistNow("g1", "Gist g1", micOn = false, reconnecting = false, host = "Amaka"), told.last())

        room.setMic(true)
        assertEquals(GistNow("g1", "Gist g1", micOn = true, reconnecting = false, host = "Amaka"), told.last())

        connections.last().events.onReconnecting()
        assertEquals(true, told.last()?.reconnecting)
        connections.last().events.onReconnected()
        assertEquals(false, told.last()?.reconnecting)

        // Leave, from the notification.
        leaveFromNotification!!()

        assertNull(told.last())
        assertNull(rooms.current.value)
        assertEquals(Phase.Prejoin, room.state.value.phase)
        assertEquals(1, asked("g1/leave"))
    }

    @Test
    fun pauseOnTheLockScreenSilencesTheGistAndTheMemberStaysIn() = withRooms {
        val room = rooms.open("g1")
        room.join()
        room.setMic(true)
        assertTrue(room.state.value.micOn)

        // Pause: on the lock screen, or by taking AirPods out.
        hearFromLockScreen!!(false)

        assertFalse(room.state.value.hearing)
        assertFalse(connections.last().heard)
        assertEquals(false, told.last()?.hearing)
        // They have stopped listening, so their microphone goes off; they are still in the room.
        assertFalse(room.state.value.micOn)
        assertEquals(Phase.Live, room.state.value.phase)
        assertSame(room, rooms.current.value)
        assertEquals(0, asked("g1/leave"))

        // A dropped connection comes back quiet.
        connections.last().events.onLost("network")
        advanceTimeBy(reconnectDelay(0) + 1)
        assertEquals(2, connections.size)
        assertEquals(Phase.Live, room.state.value.phase)
        assertFalse(connections.last().heard)

        // Play.
        hearFromLockScreen!!(true)

        assertTrue(room.state.value.hearing)
        assertTrue(connections.last().heard)
        assertEquals(true, told.last()?.hearing)
    }

    @Test
    fun talkingPlaysTheSoundAgainAndLeavingForgetsThePause() = withRooms {
        val room = rooms.open("g1")
        room.join()
        hearFromLockScreen!!(false)

        room.setMic(true)
        assertTrue(room.state.value.hearing)
        assertTrue(connections.last().heard)
        assertTrue(room.state.value.micOn)

        hearFromLockScreen!!(false)
        room.leave()

        assertTrue(room.state.value.hearing)
        // Out of the room, a stray pause changes nothing.
        room.setHearing(false)
        assertTrue(room.state.value.hearing)
    }

    @Test
    fun aGistThatEndsIsNoLongerTheMembers() = withRooms {
        val room = rooms.open("g1")
        room.join()

        connections.last().events.onLost("room_deleted")

        assertEquals(Phase.Ended, room.state.value.phase)
        assertNull(rooms.current.value)
        assertNull(told.last())
    }

    @Test
    fun signingOutLeavesTheGist() = withRooms {
        val room = rooms.open("g1")
        room.join()
        rooms.close("g1")

        rooms.leave()

        assertNull(rooms.current.value)
        assertEquals(1, asked("g1/leave"))
        assertFalse(connections.single().open)
    }

    @Test
    fun withTheAppOffScreenTheRoomIsNotPolledAndGetsAFreshLookOnComingBack() = withRooms {
        val room = rooms.open("g1")
        room.join()
        rooms.close("g1")
        rooms.setForeground(false)
        val before = server.requests.size

        advanceTimeBy(POLL_BASE_MS * 5)
        assertEquals(before, server.requests.size)
        assertEquals(Phase.Live, room.state.value.phase)

        rooms.setForeground(true)
        assertTrue(server.requests.size > before)
    }
}

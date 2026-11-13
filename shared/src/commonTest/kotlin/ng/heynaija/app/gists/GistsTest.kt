package ng.heynaija.app.gists

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import ng.heynaija.app.api.GistCommunity
import ng.heynaija.app.api.GistView
import ng.heynaija.app.api.GistViewer
import ng.heynaija.app.api.HeyNaijaApi
import ng.heynaija.app.api.LiveGist
import ng.heynaija.app.api.Me
import ng.heynaija.app.api.Person

private val host = Person("h1", "ada", "Ada")

private fun view(status: String = "live", role: String? = "listener", requestsOpen: Boolean = true, requested: Boolean = false) =
    GistView(id = "g1", title = "Jollof", status = status, host = host, requestsOpen = requestsOpen, viewer = GistViewer(role, requested))

class GistsTest {
    @Test
    fun aListenerMayAskToSpeakOnlyWhileRequestsAreOpen() {
        assertTrue(controlsFor(view()).request)
        assertFalse(controlsFor(view(requestsOpen = false)).request)
        val asked = controlsFor(view(requested = true))
        assertFalse(asked.request)
        assertTrue(asked.cancelRequest)
        assertFalse(asked.mic)
    }

    @Test
    fun theStageHasTheMicrophoneAndOnlyTheHostTheRoom() {
        val speaker = controlsFor(view(role = "speaker"))
        assertTrue(speaker.mic)
        assertFalse(speaker.moderate)
        val cohost = controlsFor(view(role = "cohost"))
        assertTrue(cohost.moderate)
        assertFalse(cohost.settings)
        val hostControls = controlsFor(view(role = "host"))
        assertTrue(hostControls.settings && hostControls.end && hostControls.appointCohost)
    }

    @Test
    fun aMemberWhoMayOnlyListenIsNeverOfferedToSpeak() {
        // Under 18, or no age on record: `gistStage` is false in /v1/me.
        val listening = controlsFor(view(), mayTakeStage = false)
        assertFalse(listening.request)
        assertFalse(listening.mic)
        // Nor told the host is not taking requests: that is only for someone who could otherwise ask.
        val closed = controlsFor(view(requestsOpen = false), mayTakeStage = false)
        assertFalse(closed.request)
        assertFalse(closed.requestsClosed)
        assertTrue(controlsFor(view(requestsOpen = false)).requestsClosed)
        assertFalse(controlsFor(view()).requestsClosed)
        // A request made before the rule can still be withdrawn.
        assertTrue(controlsFor(view(requested = true), mayTakeStage = false).cancelRequest)
        // A role the server still shows does not bring the microphone back.
        assertFalse(controlsFor(view(role = "speaker"), mayTakeStage = false).mic)
        // The host's own room controls do not depend on it; the API decides those.
        assertTrue(controlsFor(view(role = "host"), mayTakeStage = false).end)
    }

    @Test
    fun whetherAMemberMayTakeTheStageComesFromTheApiAndAnOlderServerMeansYes() {
        fun me(extra: String) = HeyNaijaApi.json.decodeFromString(Me.serializer(), """{ "id": "m1", "handle": "chidi", "features": { "gists": true }$extra }""")
        assertFalse(me(""", "gistStage": false""").mayTakeStage)
        assertTrue(me(""", "gistStage": true""").mayTakeStage)
        // A server from before the rule does not say, and let everyone with Gists on stage.
        assertTrue(me("").mayTakeStage)
        // Never without Gists themselves.
        assertFalse(HeyNaijaApi.json.decodeFromString(Me.serializer(), """{ "id": "m1", "handle": "chidi", "gistStage": true }""").mayTakeStage)
    }

    @Test
    fun nothingToDoOutsideALiveGistOrBeforeJoining() {
        assertEquals(GistControls(), controlsFor(view(status = "ended", role = "host")))
        assertEquals(GistControls(), controlsFor(view(role = null)))
    }

    @Test
    fun aNewRoleIsSaidAndResyncs() {
        val applied = applyView("listener", "live", view(role = "speaker"))
        assertEquals("You are now a speaker. Tap the microphone to talk.", applied.announcement)
        assertTrue(applied.resync)
        assertEquals("speaker", applied.role)
    }

    @Test
    fun theSameRoleSaysNothing() {
        val applied = applyView("listener", "live", view())
        assertNull(applied.announcement)
        assertFalse(applied.resync)
    }

    @Test
    fun theEndIsSaidOnce() {
        val first = applyView("listener", "live", view(status = "ended"))
        assertEquals("This Gist has ended.", first.announcement)
        assertTrue(first.ended)
        assertNull(applyView("listener", "ended", view(status = "ended")).announcement)
    }

    @Test
    fun pollingBacksOffToHalfAMinute() {
        assertEquals(4_000L, nextPollDelay(0))
        assertEquals(8_000L, nextPollDelay(1))
        assertEquals(30_000L, nextPollDelay(5))
        assertEquals(30_000L, nextPollDelay(50))
    }

    @Test
    fun refusalsByName() {
        assertEquals(JoinFailure.NotFound, joinFailure(404, null))
        assertEquals(JoinFailure.Full, joinFailure(409, "gist_full"))
        assertEquals(JoinFailure.Locked, joinFailure(409, "gist_locked"))
        assertEquals(JoinFailure.Ended, joinFailure(409, "gist_ended"))
        assertEquals(JoinFailure.Retry, joinFailure(503, "livekit_unavailable"))
    }

    @Test
    fun anAnswerFromBeforeTheMembersActionIsDropped() {
        val latest = Latest()
        val before = latest.begin()
        latest.noteAction()
        assertFalse(latest.accept(before))
        val after = latest.begin()
        assertTrue(latest.accept(after))
        assertFalse(latest.accept(after))
    }

    @Test
    fun wordsAsTheWebHasThem() {
        assertEquals("No listeners yet", listenerLabel(0))
        assertEquals("1 listener", listenerLabel(1))
        assertEquals("1,234 listeners", listenerLabel(1234))
        assertEquals("1,000,000", grouped(1_000_000))
        val gist = LiveGist("g1", "Jollof", host, GistCommunity("c1", "lagos", "Lagos"), listenerCount = 2)
        assertEquals("Hosted by Ada · in Lagos · 2 listeners", liveMeta(gist))
        assertEquals("Hosted by @tunde · No listeners yet", liveMeta(gist.copy(host = Person("h2", "tunde"), community = null, listenerCount = 0)))
    }

    @Test
    fun timeLiveTicksInMinutesThenHours() {
        val start = "2026-11-07T10:00:00Z"
        assertEquals("0:05", elapsedLabel(start, Instant.parse("2026-11-07T10:00:05Z")))
        assertEquals("12:03", elapsedLabel(start, Instant.parse("2026-11-07T10:12:03Z")))
        assertEquals("1:02:03", elapsedLabel(start, Instant.parse("2026-11-07T11:02:03Z")))
        assertEquals("", elapsedLabel(null, Instant.parse("2026-11-07T11:02:03Z")))
    }

    @Test
    fun aTitleIsNeededAndShort() {
        assertEquals("Give your Gist a title.", titleProblem("   "))
        assertEquals("Keep the title to 120 characters or fewer.", titleProblem("a".repeat(121)))
        assertNull(titleProblem("Jollof wars"))
    }

    @Test
    fun whoMayReplyInTheHostsWordsOrNeutrally() {
        assertEquals("People you follow can reply", gistReplyAudienceNote("following", "ada", viewerIsHost = true))
        assertEquals("People @ada follows can reply", gistReplyAudienceNote("following", "ada", viewerIsHost = false))
        assertEquals("Replies off", gistReplyAudienceNote("off", "ada", viewerIsHost = false))
    }
}

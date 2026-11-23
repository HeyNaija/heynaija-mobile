package ng.heynaija.app.games.trivia

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import ng.heynaija.app.api.HeyNaijaApi
import ng.heynaija.app.api.TriviaRound

class TriviaTest {
    @Test
    fun placesAndTimesReadAsTheWebWritesThem() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "102nd"), listOf(1, 2, 3, 4, 11, 12, 13, 21, 102).map(::ordinal))
        assertEquals("2.41 s", seconds(2_410))
        assertEquals("0.05 s", seconds(57))
        assertEquals("12.00 s", seconds(12_000))
        assertEquals("0.00 s", seconds(-5))
    }

    @Test
    fun aRoundBeforeItsQuestionOpensHasNoText() {
        // As the API sends it during the countdown: the question is coming, its text is not.
        val json = """{"id":"r1","title":"Anime night","status":"live","startsAt":"2026-11-15T19:00:00.000Z","questions":3,
            "host":{"handle":"sam"},"version":4,"serverTime":"2026-11-15T19:00:01.000Z",
            "you":{"host":false,"canAnswer":true,"points":0},
            "current":{"id":"q1","position":1,"opensAt":"2026-11-15T19:00:03.000Z","closesAt":"2026-11-15T19:00:23.000Z","kind":"choice","prompt":null,"choices":null,"yourAnswer":null},
            "last":null,"standings":[],"yourStanding":null,"final":false}"""
        val round = HeyNaijaApi.json.decodeFromString(TriviaRound.serializer(), json)
        assertEquals(null, round.current?.prompt)
        assertEquals(null, round.current?.choices)
        assertTrue(round.you.canAnswer)
        assertEquals(false, round.over)
        assertEquals(null, round.current?.image)
    }

    @Test
    fun aQuestionsPictureComesWithItOnceItOpens() {
        val picture = """{"src":"/media/posts/m1","alt":"A green-white-green flag","width":900,"height":450,"animated":false}"""
        val json = """{"id":"r1","title":"Flags","status":"live","startsAt":"2026-11-15T19:00:00.000Z","questions":2,
            "host":{"handle":"sam"},"version":5,"serverTime":"2026-11-15T19:00:04.000Z",
            "you":{"host":false,"canAnswer":true,"points":0},
            "current":{"id":"q1","position":1,"opensAt":"2026-11-15T19:00:03.000Z","closesAt":"2026-11-15T19:00:23.000Z","kind":"choice",
              "prompt":"Whose flag is this?","choices":[{"id":1,"text":"Ireland"},{"id":0,"text":"Nigeria"}],"image":$picture,"yourAnswer":null},
            "last":{"id":"q0","position":0,"prompt":"Warm-up","kind":"typed","answer":"Lagos","image":$picture},
            "standings":[],"yourStanding":null,"final":false}"""
        val round = HeyNaijaApi.json.decodeFromString(TriviaRound.serializer(), json)
        val image = round.current?.image
        assertEquals("/media/posts/m1", image?.src)
        assertEquals("A green-white-green flag", image?.alt)
        assertEquals(900, image?.width)
        assertEquals("/media/posts/m1", round.last?.image?.src)
    }

    private val at = Instant.parse("2026-11-22T19:13:00.000Z").toEpochMilliseconds()

    /** A round as the API sends it, with [current] as its question (or null). */
    private fun round(status: String = "live", startsAt: String = "2026-11-22T19:00:00.000Z", current: String? = null) =
        HeyNaijaApi.json.decodeFromString(TriviaRound.serializer(), """{"id":"r1","title":"Shonen Showdown","status":"$status","startsAt":"$startsAt",
            "questions":30,"host":{"handle":"sam"},"version":19,"serverTime":"2026-11-22T19:13:00.000Z",
            "you":{"host":false,"canAnswer":true,"points":0},"current":$current,"last":null,"standings":[],"yourStanding":null,"final":false}""")

    private fun question(opensAt: String, closesAt: String, prompt: String?) =
        """{"id":"q18","position":18,"opensAt":"$opensAt","closesAt":"$closesAt","kind":"choice","prompt":${prompt?.let { "\"$it\"" } ?: "null"},"choices":null,"yourAnswer":null}"""

    @Test
    fun aLateReplyIsGivenUpOnTwoSecondsAfterTheQuestionOpensOrCloses() {
        // The countdown: the text comes as the question opens, 5 s from now.
        val countdown = round(current = question("2026-11-22T19:13:05.000Z", "2026-11-22T19:13:25.000Z", null))
        assertEquals(7_000, waitLimit(countdown, at, 0))
        // The question on screen: the next reply comes as it closes, 15 s from now.
        val open = round(current = question("2026-11-22T19:12:50.000Z", "2026-11-22T19:13:15.000Z", "Who is this?"))
        assertEquals(17_000, waitLimit(open, at, 0))
        // Open by the server's clock but no text here: the server answers at once, so a short wait.
        val missing = round(current = question("2026-11-22T19:12:58.000Z", "2026-11-22T19:13:18.000Z", null))
        assertEquals(LEAST_MS, waitLimit(missing, at, 0))
        // Never past the server's own 25 seconds, with room to spare.
        val far = round(current = question("2026-11-22T19:14:30.000Z", "2026-11-22T19:14:50.000Z", null))
        assertEquals(HOLD_MS, waitLimit(far, at, 0))
    }

    @Test
    fun withoutAQuestionTheScreenAsksAgainEveryTenSecondsWhileTheRoundIsOn() {
        // Before the host starts it, the round can start at any moment.
        assertEquals(NO_TIMES_MS, waitLimit(round(status = "scheduled"), at, 0))
        assertEquals(NO_TIMES_MS, waitLimit(round(status = "live"), at, 0))
        // The host checking typed answers: the results can wait a little.
        assertEquals(HOLD_MS, waitLimit(round(status = "reviewing"), at, 0))
    }

    @Test
    fun eachRequestGivenUpOnInARowGetsLongerSoASlowLineStillGetsThrough() {
        val missing = round(current = question("2026-11-22T19:12:58.000Z", "2026-11-22T19:13:18.000Z", null))
        assertEquals(listOf(4_000L, 8_000L, 16_000L, 30_000L, 30_000L), (0..4).map { waitLimit(missing, at, it) })
    }

    @Test
    fun duringARoundAFailedRequestIsTriedAgainWithinTwoSeconds() {
        val live = round(current = question("2026-11-22T19:13:05.000Z", "2026-11-22T19:13:25.000Z", null))
        assertEquals(listOf(1_000L, 2_000L, 2_000L, 2_000L), (1..4).map { retryPause(live, at, it) })
        // Starting within 10 minutes counts as on.
        assertEquals(2_000L, retryPause(round(status = "scheduled", startsAt = "2026-11-22T19:20:00.000Z"), at, 5))
        // A round days away, or none loaded yet, backs off as before, up to 30 seconds.
        val later = round(status = "scheduled", startsAt = "2026-11-25T19:00:00.000Z")
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), (1..7).map { retryPause(later, at, it) })
        assertEquals(30_000L, retryPause(null, at, 9))
    }
}

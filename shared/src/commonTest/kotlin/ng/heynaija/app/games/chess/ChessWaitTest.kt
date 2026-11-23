package ng.heynaija.app.games.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import ng.heynaija.app.api.ChessGame
import ng.heynaija.app.api.HeyNaijaApi

class ChessWaitTest {
    /** A game as the API sends it: a 3+2 blitz game, or a correspondence game with [days]. */
    private fun game(finished: Boolean = false, days: Int? = null) =
        HeyNaijaApi.json.decodeFromString(ChessGame.serializer(), """{"id":"AbCd1234","white":{"handle":"ada"},"black":{"handle":"bayo"},
            "you":"white","rated":false,"speed":"${if (days == null) "blitz" else "correspondence"}","time":"${if (days == null) "3+2" else "$days days"}",
            "clock":${if (days == null) """{"limit":180,"increment":2}""" else "null"},"days":${days ?: "null"},
            "status":"${if (finished) "mate" else "started"}","finished":$finished,"moves":["e2e4","e7e5"],"version":7,"startedAt":"2026-11-23T19:00:00.000Z"}""")

    @Test
    fun inAGameWithAClockARequestIsGivenUpOnAfterTenSeconds() {
        assertEquals(10_000L, waitLimit(game()))
        // Nothing on screen yet: the server answers at once.
        assertEquals(PLAYING_MS, waitLimit(null))
    }

    @Test
    fun inACorrespondenceGameOrOneThatIsOverARequestWaitsOutTheServersHoldWithRoomToSpare() {
        assertEquals(30_000L, waitLimit(game(days = 3)))
        assertEquals(HOLD_MS, waitLimit(game(finished = true)))
    }

    @Test
    fun whileAGameGoesOnAFailedRequestIsTriedAgainWithinTwoSeconds() {
        assertEquals(listOf(1_000L, 2_000L, 2_000L, 2_000L), (1..4).map { retryPause(game(), it) })
        assertEquals(listOf(1_000L, 2_000L, 2_000L), (1..3).map { retryPause(game(days = 3), it) })
    }

    @Test
    fun onceAGameIsOverOrBeforeOneLoadsAFailedRequestBacksOffAsBeforeUpToThirtySeconds() {
        val over = game(finished = true)
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), (1..7).map { retryPause(over, it) })
        assertEquals(30_000L, retryPause(null, 9))
    }
}

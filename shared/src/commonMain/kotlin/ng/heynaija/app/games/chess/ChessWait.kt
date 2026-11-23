package ng.heynaija.app.games.chess

import kotlin.math.max
import kotlin.math.min
import ng.heynaija.app.api.ChessGame

/*
 * How long a game screen waits for the server before asking again. The website does
 * the same (apps/web/lib/chess-wait.ts in heynaija).
 *
 * The server holds each request until the game changes, or 25 seconds. On patchy
 * mobile data a reply can be lost on the way while the phone still thinks the request
 * is open, and then a move shows late or not at all. A Trivia player on 3G missed whole
 * questions that way (2026-11-22). A move has no set time to come, so the screen gives
 * up on a request after a fixed time and asks again: the server answers at once if the
 * game moved on meanwhile.
 */

/** The server holds a request 25 seconds at most: a reply later than this is not coming. */
internal const val HOLD_MS = 30_000L

/** In a game with a clock, a lost reply costs a player their time: give up on a request after this. */
internal const val PLAYING_MS = 10_000L

/** A player's own move or decision comes back from Lichess within a moment. One this late was lost. */
internal const val LATE_MS = 3_000L

/** After a failed request, wait 1 second, then 2, 4… up to this. */
internal const val RETRY_MAX_MS = 30_000L

/** …but while the game goes on, at most this. */
internal const val RETRY_LIVE_MS = 2_000L

/**
 * How long to wait for the next reply. With no [game] on screen yet the server answers at
 * once, so the shorter wait.
 */
internal fun waitLimit(game: ChessGame?): Long =
    if (game == null || (!game.finished && game.clock != null)) PLAYING_MS else HOLD_MS

/** How long to wait after the [failures]th failed request in a row. */
internal fun retryPause(game: ChessGame?, failures: Int): Long {
    val grown = min(1_000L shl min(max(failures - 1, 0), 5), RETRY_MAX_MS)
    return if (game != null && !game.finished) min(grown, RETRY_LIVE_MS) else grown
}

package ng.heynaija.app.games.trivia

import kotlin.math.max
import kotlin.math.min
import kotlin.time.Instant
import ng.heynaija.app.api.TriviaRound

/*
 * How long a round screen waits for the server before asking again. The website does
 * the same (apps/web/lib/trivia-wait.ts in heynaija).
 *
 * The server holds each request until the round changes: at the latest when the next
 * question opens or closes, or after 25 seconds. On patchy mobile data a reply can be
 * lost on the way while the phone still thinks the request is open. Waiting out a long
 * time limit then costs a whole question (a tester missed two on 3G, 2026-11-22), so the
 * screen gives up on a reply that is late and asks again.
 */

/** The server holds a request 25 seconds at most: a reply later than this is not coming. */
internal const val HOLD_MS = 30_000L

/** A question opening or closing gets a reply at once. One this late after it was lost. */
internal const val LATE_MS = 2_000L

/** Never give up on a request sooner than this, doubled for each one given up on in a row, so a slow line still gets through. */
internal const val LEAST_MS = 4_000L

/** Before the host starts the round there is no time to go by, and it can start at any moment. */
internal const val NO_TIMES_MS = 10_000L

/** After a failed request, wait 1 second, then 2, 4… up to this. */
internal const val RETRY_MAX_MS = 30_000L

/** …but while a round is on, or starts within 10 minutes, at most this. */
internal const val RETRY_LIVE_MS = 2_000L

/**
 * How long to wait for the next reply, by the server's clock ([serverNow]). [cuts] is how
 * many requests in a row were given up on.
 */
internal fun waitLimit(round: TriviaRound, serverNow: Long, cuts: Int): Long {
    val least = min(LEAST_MS shl min(cuts, 3), HOLD_MS)
    val c = round.current
    val wait = when {
        // Waiting for the text as it opens, or for the close once it is on screen.
        c != null -> millisOf(if (c.prompt == null) c.opensAt else c.closesAt)?.let { max(0, it - serverNow) + LATE_MS } ?: HOLD_MS
        round.status == "scheduled" || round.status == "live" -> NO_TIMES_MS
        else -> HOLD_MS
    }
    return wait.coerceIn(least, HOLD_MS)
}

/** How long to wait after the [failures]th failed request in a row. */
internal fun retryPause(round: TriviaRound?, serverNow: Long, failures: Int): Long {
    val grown = min(1_000L shl min(max(failures - 1, 0), 5), RETRY_MAX_MS)
    return if (round != null && onNow(round, serverNow)) min(grown, RETRY_LIVE_MS) else grown
}

private fun onNow(round: TriviaRound, serverNow: Long): Boolean =
    round.status == "live" || round.current != null ||
        (round.status == "scheduled" && (millisOf(round.startsAt) ?: Long.MAX_VALUE) - serverNow < 10 * 60_000L)

private fun millisOf(iso: String): Long? = runCatching { Instant.parse(iso).toEpochMilliseconds() }.getOrNull()

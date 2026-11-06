package ng.heynaija.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/**
 * "now", "5m", "3h", "2d", then the day: "26 Oct", with the year once it is not
 * this year's. The same words as the web (apps/web/app/ui/time.ts), in the
 * reader's own time zone.
 */
fun timeAgo(at: Instant, now: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val seconds = (now - at).inWholeSeconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "now"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86400 -> "${seconds / 3600}h"
        seconds < 86400 * 7 -> "${seconds / 86400}d"
        else -> {
            val day = at.toLocalDateTime(zone).date
            val label = "${day.day} ${MONTHS[day.month.ordinal]}"
            if (day.year == now.toLocalDateTime(zone).year) label else "$label ${day.year}"
        }
    }
}

/** A clip's length: "0:47", "1:19". */
fun clipLength(durationMs: Long): String {
    val seconds = durationMs / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/** One clock for a screen's times, so "now" turns into "1m" on time. */
@Composable
fun rememberNow(): Instant {
    val now by produceState(Clock.System.now()) {
        while (true) {
            delay(15.seconds)
            value = Clock.System.now()
        }
    }
    return now
}

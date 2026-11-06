package ng.heynaija.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

class TimeTest {
    private val now = Instant.parse("2026-11-06T12:00:00Z")
    private val lagos = TimeZone.of("Africa/Lagos")

    private fun ago(iso: String) = timeAgo(Instant.parse(iso), now, lagos)

    @Test
    fun shortAgesForTheLastWeek() {
        assertEquals("now", ago("2026-11-06T11:59:30Z"))
        assertEquals("5m", ago("2026-11-06T11:55:00Z"))
        assertEquals("3h", ago("2026-11-06T09:00:00Z"))
        assertEquals("6d", ago("2026-10-31T12:00:00Z"))
    }

    @Test
    fun theDayAfterAWeekAndTheYearOnceItIsNotThisOne() {
        assertEquals("26 Oct", ago("2026-10-26T10:52:00Z"))
        assertEquals("26 Oct 2025", ago("2025-10-26T10:52:00Z"))
    }

    @Test
    fun theDayIsTheReadersDay() {
        // 23:30 UTC on 20 October is already 21 October in Lagos (UTC+1).
        assertEquals("21 Oct", ago("2026-10-20T23:30:00Z"))
        assertEquals("20 Oct", timeAgo(Instant.parse("2026-10-20T23:30:00Z"), now, TimeZone.UTC))
    }

    @Test
    fun aClockAheadOfTheServerIsNow() {
        assertEquals("now", ago("2026-11-06T12:00:20Z"))
    }

    @Test
    fun clipLengths() {
        assertEquals("0:47", clipLength(47_500))
        assertEquals("1:19", clipLength(79_435))
        assertEquals("3:00", clipLength(180_000))
    }
}

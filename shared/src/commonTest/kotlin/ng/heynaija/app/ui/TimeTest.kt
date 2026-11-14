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

    @Test
    fun aPostsOwnTimeIsOnATwelveHourClockAsOnTheWeb() {
        assertEquals("9:41 am · 7 Nov 2026", postedAt(Instant.parse("2026-11-07T08:41:00Z"), lagos))
        assertEquals("12:30 pm · 7 Nov 2026", postedAt(Instant.parse("2026-11-07T11:30:00Z"), lagos))
        assertEquals("2:05 pm · 7 Nov 2026", postedAt(Instant.parse("2026-11-07T13:05:00Z"), lagos))
        // Just after midnight in Lagos is 12, not 0.
        assertEquals("12:05 am · 8 Nov 2026", postedAt(Instant.parse("2026-11-07T23:05:00Z"), lagos))
    }

    @Test
    fun theWaitForACommunitysNextAnnouncementIsSaidInWholeHours() {
        fun wait(iso: String?) = announcementWait(iso, now)
        assertEquals(null, wait(null))
        assertEquals(null, wait("2026-11-06T12:00:00Z"))
        assertEquals(null, wait("2026-11-06T11:00:00Z"))
        assertEquals(null, wait("soon"))
        assertEquals("in less than an hour", wait("2026-11-06T12:29:00Z"))
        assertEquals("in about an hour", wait("2026-11-06T12:45:00.000Z"))
        assertEquals("in about 5 hours", wait("2026-11-06T17:10:00Z"))
        assertEquals("in about 24 hours", wait("2026-11-07T12:00:00Z"))
    }
}

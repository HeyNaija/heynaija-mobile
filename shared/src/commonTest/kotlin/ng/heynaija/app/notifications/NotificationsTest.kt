package ng.heynaija.app.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import ng.heynaija.app.api.Notification
import ng.heynaija.app.api.NotificationPage

class NotificationsTest {
    private fun n(id: String, read: Boolean = false) = Notification(id = id, kind = "like", createdAt = "2026-11-10T12:00:00Z", read = read)

    private fun shown(vararg items: Notification, cursor: String? = null) = NotificationsState(items.toList(), cursor, loaded = true)

    @Test
    fun newOnesGoAboveAndOlderPagesStay() {
        // Two pages were loaded; c, d and e are on the newest page again, below two new ones.
        val before = shown(n("c"), n("d"), n("e"), n("f"), n("g"), cursor = "after-g")
        val after = withNewest(before, NotificationPage(listOf(n("a"), n("b"), n("c", true), n("d", true), n("e", true)), cursor = "after-e"))
        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g"), after.items.map { it.id })
        assertEquals("after-g", after.cursor)
    }

    @Test
    fun thoseSeenBelowShowAsReadToo() {
        val before = shown(n("c"), n("d"), n("e"), cursor = "after-e")
        val after = withNewest(before, NotificationPage(listOf(n("a"), n("b"), n("c", true)), cursor = "after-c"))
        assertEquals(listOf(false, false, true, true, true), after.items.map { it.read })
    }

    @Test
    fun belowStaysUnreadWhenTheWholeNewPageIs() {
        val before = shown(n("c"), n("d"), cursor = "after-d")
        val after = withNewest(before, NotificationPage(listOf(n("a"), n("b"), n("c")), cursor = "after-c"))
        assertEquals(listOf(false, false, false, false), after.items.map { it.read })
    }

    @Test
    fun theNewPageAloneWhenNothingWasLoadedBelowIt() {
        val before = shown(n("b"), n("c"))
        val after = withNewest(before, NotificationPage(listOf(n("a"), n("b", true), n("c", true)), cursor = "after-c"))
        assertEquals(listOf("a", "b", "c"), after.items.map { it.id })
        assertEquals("after-c", after.cursor)
    }

    @Test
    fun startsAgainWithNothingInCommon() {
        val before = shown(n("x"), n("y"), cursor = "after-y")
        val after = withNewest(before, NotificationPage(listOf(n("a"), n("b")), cursor = "after-b"))
        assertEquals(listOf("a", "b"), after.items.map { it.id })
        assertEquals("after-b", after.cursor)
    }

    @Test
    fun oneGoneFromTheServerGoesFromTheList() {
        // d's post was deleted.
        val before = shown(n("b"), n("c"), n("d"), n("e"), n("f"), cursor = "after-f")
        val after = withNewest(before, NotificationPage(listOf(n("a"), n("b", true), n("c", true), n("e", true)), cursor = "after-e"))
        assertEquals(listOf("a", "b", "c", "e", "f"), after.items.map { it.id })
    }
}

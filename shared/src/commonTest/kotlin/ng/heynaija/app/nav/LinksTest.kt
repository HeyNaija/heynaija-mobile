package ng.heynaija.app.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinksTest {
    private val web = "https://staging.heynaija.ng"
    private val post = "0b5f0c1e-6d0f-4a43-9b8e-1b9a2b3c4d5e"
    private val gist = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d"

    @Test
    fun theLinksPushNotificationsCarryOpenTheirScreens() {
        assertEquals(Route.Thread(post), routeOf("/p/$post", web))
        assertEquals(Route.Thread(post, gist = gist), routeOf("/gists/$gist?reply=$post#post-$post", web))
        assertEquals(Route.Gist(gist), routeOf("/gists/$gist", web))
        assertEquals(Route.Profile("ada"), routeOf("$web/u/Ada", web))
        assertEquals(Route.Notifications, routeOf("/notifications", web))
        assertEquals(Route.Settings, routeOf("$web/settings", web))
        assertEquals(Route.Community("lagos-tech"), routeOf("/c/lagos-tech", web))
        assertEquals(Route.Hashtag("jollof"), routeOf("/hashtag/Jollof", web))
        assertEquals(Route.Home, routeOf("/", web))
    }

    @Test
    fun anythingElseIsNotOpenedInTheApp() {
        assertNull(routeOf("https://evil.example/p/$post", web))
        assertNull(routeOf("https://staging.heynaija.ng.evil.example/p/$post", web))
        assertNull(routeOf("/p/not-a-post", web))
        assertNull(routeOf("/about", web))
        assertNull(routeOf("/u/../settings", web))
    }

    @Test
    fun aTabsOwnScreenOpensAsThatTabAndTheRestOverNotifications() {
        val navigator = Navigator()
        navigator.openFromNotification(Route.Thread(post))
        assertEquals(Tab.Notifications, navigator.tab)
        assertEquals(listOf(Route.Notifications, Route.Thread(post)), navigator.stack.map { it.route })
        navigator.openFromNotification(Route.Home)
        assertEquals(Tab.Home, navigator.tab)
        assertEquals(listOf(Route.Home), navigator.stack.map { it.route })
    }
}

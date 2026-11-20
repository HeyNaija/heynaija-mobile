package ng.heynaija.app.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import ng.heynaija.app.gistLink

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
    fun gamesPagesOpenTheirScreensAndKenoNothingYet() {
        assertEquals(Route.Games, routeOf("/games", web))
        assertEquals(Route.Chess(), routeOf("$web/games/chess", web))
        assertEquals(Route.Chess(), routeOf("/games/chess#challenge-$post", web))
        assertEquals(Route.ChessGame("aBcD1234"), routeOf("/games/chess/games/aBcD1234", web))
        assertEquals(Route.GamesLeaderboard(), routeOf("/games/leaderboard", web))
        assertEquals(Route.GamesScoring, routeOf("/games/scoring", web))
        assertNull(routeOf("/games/keno", web))
        assertEquals(Route.Chess(tab = "competitions"), routeOf("/games/chess?tab=competitions", web))
        assertEquals(Route.Chess(), routeOf("/games/chess?tab=nonsense", web))
        assertEquals(Route.Competitions, routeOf("/games/competitions", web))
        assertEquals(Route.Competition(post), routeOf("$web/games/competitions/$post", web))
        assertEquals(Route.ChessTeam("lagos-chess"), routeOf("/games/chess/teams/lagos-chess", web))
        assertNull(routeOf("/games/competitions/new", web))
        assertNull(routeOf("/games/chess/callback?code=x&state=y", web))
        assertNull(routeOf("/games/chess/games/not-an-id!", web))
        assertTrue(Route.ChessGame("aBcD1234").gamesLink)
        assertFalse(Route.Thread(post).gamesLink)
    }

    @Test
    fun triviaLinksOpenTheRoundsAndARoundButNotHosting() {
        assertEquals(Route.Trivia(), routeOf("/games/trivia", web))
        assertEquals(Route.Trivia(tab = "leaderboard"), routeOf("/games/trivia?tab=leaderboard", web))
        // The Arbiter's thread links to the round on the website.
        assertEquals(Route.TriviaRound(post), routeOf("$web/games/trivia/rounds/$post", web))
        assertNull(routeOf("/games/trivia/rounds/$post/host", web))
        assertNull(routeOf("/games/trivia/host", web))
        assertNull(routeOf("/games/trivia/rounds/not-a-round", web))
        assertTrue(Route.TriviaRound(post).gamesLink)
        assertTrue(Route.Trivia().gamesLink)
    }

    @Test
    fun aSharedGistLinkIsTheWebsPageForItAndOpensTheGist() {
        val link = gistLink(web, gist)
        assertEquals("$web/gists/$gist", link)
        assertEquals(Route.Gist(gist), routeOf(link, web))
    }

    @Test
    fun goingBackToAScreenTheTabHasOpenClosesWhatIsAboveItInsteadOfOpeningItAgain() {
        val navigator = Navigator()
        navigator.push(Route.Gist(gist))
        navigator.push(Route.Profile("ada"))
        navigator.push(Route.Thread(post))

        navigator.returnTo(Route.Gist(gist))
        assertEquals(listOf(Route.Home, Route.Gist(gist)), navigator.stack.map { it.route })

        // Already on top: nothing moves.
        navigator.returnTo(Route.Gist(gist))
        assertEquals(listOf(Route.Home, Route.Gist(gist)), navigator.stack.map { it.route })

        // Another tab has not got it open: it opens there, over what that tab shows.
        navigator.select(Tab.Search)
        navigator.returnTo(Route.Gist(gist))
        assertEquals(listOf(Route.Search, Route.Gist(gist)), navigator.stack.map { it.route })
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

    @Test
    fun everyPageTheSiteSendsToTheAppHasAScreen() {
        // The pages apps/web/lib/app-links.ts claims (web repository) and the Android manifest lists.
        assertEquals(Route.Thread(post), routeOf("$web/p/$post", web))
        assertEquals(Route.Thread(post, gist = gist), routeOf("$web/gists/$gist?reply=$post", web))
        assertEquals(Route.Gist(gist), routeOf("$web/gists/$gist", web))
        assertEquals(Route.Profile("ada"), routeOf("$web/u/ada", web))
        assertEquals(Route.Community("lagos-tech"), routeOf("$web/c/lagos-tech", web))
        assertEquals(Route.Hashtag("jollof"), routeOf("$web/hashtag/jollof", web))
        assertEquals(Route.Notifications, routeOf("$web/notifications", web))
        assertEquals(Route.Bookmarks, routeOf("$web/bookmarks", web))
        // Another server's link: a debug build on staging given one to heynaija.ng.
        assertNull(routeOf("https://heynaija.ng/p/$post", web))
    }

    @Test
    fun aLinkToTheSiteOpensOverWhateverIsOpen() {
        val navigator = Navigator()
        navigator.select(Tab.Search)
        navigator.openFromLink(Route.Thread(post))
        assertEquals(Tab.Search, navigator.tab)
        assertEquals(listOf(Route.Search, Route.Thread(post)), navigator.stack.map { it.route })
        // The same link again does not open the post twice.
        navigator.openFromLink(Route.Thread(post))
        navigator.openFromLink(Route.Profile("ada"))
        assertEquals(listOf(Route.Search, Route.Thread(post), Route.Profile("ada")), navigator.stack.map { it.route })
        // A tab's own page opens as that tab, at its top.
        navigator.openFromLink(Route.Notifications)
        assertEquals(Tab.Notifications, navigator.tab)
        assertEquals(listOf(Route.Notifications), navigator.stack.map { it.route })
        navigator.openFromLink(Route.Search)
        assertEquals(listOf(Route.Search), navigator.stack.map { it.route })
    }

    @Test
    fun aLinkIsKeptUntilTheAppHasOpenedIt() {
        val link = "$web/p/$post"
        WebLinks.open(link)
        WebLinks.handled("$web/u/ada")
        assertEquals(link, WebLinks.opened.value)
        WebLinks.handled(link)
        assertNull(WebLinks.opened.value)
    }

    @Test
    fun signingInKnowsTheScreenItWasAskedFor() {
        val navigator = Navigator()
        assertNull(navigator.beforeSignIn)
        navigator.openFromLink(Route.Gist(gist))
        assertNull(navigator.beforeSignIn)
        navigator.push(Route.SignIn)
        assertEquals(Route.Gist(gist), navigator.beforeSignIn)
        // "Join with an invite" on the sign-in form swaps it for the sign-up one.
        navigator.replace(Route.SignUp)
        assertEquals(Route.Gist(gist), navigator.beforeSignIn)
    }

    @Test
    fun reportASafetyConcernOpensAgainAfterSigningIn() {
        // A visitor goes Settings → Report a safety concern → Sign in; once signed in, the report form is back.
        val navigator = Navigator()
        navigator.push(Route.Settings)
        navigator.push(Route.ReportConcern)
        navigator.push(Route.SignIn)
        assertEquals(Route.ReportConcern, navigator.beforeSignIn)
        assertTrue(Route.ReportConcern.opensAgainAfterSignIn)
        assertTrue(Route.Gist(gist).opensAgainAfterSignIn)
        assertFalse(Route.Settings.opensAgainAfterSignIn)
    }

    @Test
    fun aWeeklyHighlightsPushOpensItsConversation() {
        // It arrives like any other push, with the tag "highlights" and the path of a post (ADR 0027 in the web repository).
        val navigator = Navigator()
        navigator.openFromNotification(routeOf("/p/$post", web)!!)
        assertEquals(Tab.Notifications, navigator.tab)
        assertEquals(listOf(Route.Notifications, Route.Thread(post)), navigator.stack.map { it.route })
    }
}

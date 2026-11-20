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
    fun gamesPagesOpenTheirScreensAndKenoInTheBrowser() {
        assertEquals(Route.Games, routeOf("/games", web))
        assertEquals(Route.Chess(), routeOf("$web/games/chess", web))
        assertEquals(Route.Chess(), routeOf("/games/chess#challenge-$post", web))
        assertEquals(Route.ChessGame("aBcD1234"), routeOf("/games/chess/games/aBcD1234", web))
        assertEquals(Route.GamesLeaderboard(), routeOf("/games/leaderboard", web))
        assertEquals(Route.GamesScoring, routeOf("/games/scoring", web))
        assertNull(routeOf("/games/keno", web))
        assertEquals(LinkTarget.Browser, linkTarget("/games/keno/rounds/42"))
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
    fun thePagesTheAppHadBeforeStillOpenTheirScreens() {
        assertEquals(Route.Thread(post), routeOf("$web/p/$post", web))
        assertEquals(Route.Thread(post, gist = gist), routeOf("$web/gists/$gist?reply=$post", web))
        assertEquals(Route.Gist(gist), routeOf("$web/gists/$gist", web))
        assertEquals(Route.Profile("ada"), routeOf("$web/u/ada", web))
        assertEquals(Route.Community("lagos-tech"), routeOf("$web/c/lagos-tech", web))
        assertEquals(Route.Hashtag("jollof"), routeOf("$web/hashtag/jollof", web))
        assertEquals(Route.Notifications, routeOf("$web/notifications", web))
        assertEquals(Route.Bookmarks, routeOf("$web/bookmarks", web))
        // Another server's link: a release build on staging is never given one, and a debug build reads it as its own (sitePath).
        assertNull(routeOf("https://heynaija.ng/p/$post", web))
    }

    /**
     * A copy of PAGES in apps/web/lib/app-links.ts (web repository): every page of
     * the site, and where it opens. When the site adds a page it says there; when
     * the app gets a screen for one, both lists change.
     */
    private val pages = mapOf(
        "/" to "app",
        "/about" to "browser",
        "/about/discover" to "browser",
        "/about/highlights" to "browser",
        "/about/keke" to "browser",
        "/auth-error" to "website",
        "/birth-date" to "website",
        "/bookmarks" to "app",
        "/c" to "app",
        "/c/[slug]" to "app",
        "/charter" to "browser",
        "/child-safety" to "browser",
        "/email-off" to "website",
        "/games" to "app",
        "/games/chess" to "app",
        "/games/chess/callback" to "website",
        "/games/chess/games/[id]" to "app",
        "/games/chess/teams/[slug]" to "app",
        "/games/competitions" to "app",
        "/games/competitions/[id]" to "app",
        "/games/competitions/[id]/edit" to "browser",
        "/games/competitions/new" to "browser",
        "/games/keno" to "website",
        "/games/keno/leaderboard" to "website",
        "/games/keno/rounds" to "website",
        "/games/keno/rounds/[number]" to "website",
        "/games/keno/tickets" to "website",
        "/games/leaderboard" to "app",
        "/games/scoring" to "app",
        "/games/trivia" to "app",
        "/games/trivia/host" to "browser",
        "/games/trivia/rounds/[id]" to "app",
        "/games/trivia/rounds/[id]/edit" to "browser",
        "/games/trivia/rounds/[id]/host" to "browser",
        "/games-not-open" to "website",
        "/games-sign-in" to "website",
        "/gist-sign-in" to "website",
        "/gists/[id]" to "app",
        "/gists/new" to "browser",
        "/governance" to "browser",
        "/governance/constitution" to "browser",
        "/governance/membership" to "browser",
        "/governance/verification" to "browser",
        "/hashtag/[tag]" to "app",
        "/ideas" to "browser",
        "/ideas/new" to "browser",
        "/join" to "app",
        "/licence" to "browser",
        "/lichess/return" to "website",
        "/login" to "website",
        "/logout" to "website",
        "/mod" to "browser",
        "/notifications" to "app",
        "/p/[id]" to "app",
        "/p/[id]/quote" to "app",
        "/p/[id]/votes" to "browser",
        "/privacy" to "browser",
        "/progress" to "browser",
        "/report" to "browser",
        "/rules" to "browser",
        "/search" to "app",
        "/settings" to "app",
        "/settings/delete" to "app",
        "/settings/delete/refused/[reason]" to "browser",
        "/settings/delete/scheduled" to "browser",
        "/settings/export" to "website",
        "/signup" to "website",
        "/terms" to "browser",
        "/topics" to "browser",
        "/u/[handle]" to "app",
        "/verification" to "website",
        "/welcome" to "website",
        "/.well-known/apple-app-site-association" to "website",
        "/.well-known/assetlinks.json" to "website",
    )

    /** A real address for a page's folder path. */
    private fun example(page: String): String = page
        .replace("/games/chess/games/[id]", "/games/chess/games/aBcD1234")
        .replace("[id]", post).replace("[slug]", "lagos-tech").replace("[handle]", "ada")
        .replace("[tag]", "jollof").replace("[number]", "42").replace("[reason]", "owner")

    @Test
    fun everyPageOfTheSiteOpensItsScreenOrTheBrowser() {
        for ((page, opens) in pages) {
            val target = linkTarget(example(page))
            when (opens) {
                "app" -> assertTrue(target != LinkTarget.Browser, "$page has a screen in the app")
                // The phone gives the app these only on Android 14 and older: the browser opens them, or Lichess's answer is read.
                "website" -> assertTrue(target == LinkTarget.Browser || target is LinkTarget.LichessAnswer, "$page stays on the website")
                else -> assertEquals(LinkTarget.Browser, target, "$page has no screen in the app yet")
            }
        }
    }

    @Test
    fun theSitesOtherPagesWithAScreenOpenIt() {
        assertEquals(Route.Communities, routeOf("$web/c", web))
        assertEquals(Route.DeleteAccount, routeOf("/settings/delete", web))
        assertEquals(LinkTarget.Screen(Route.Search), linkTarget("/search"))
        assertEquals(LinkTarget.Search("jollof rice"), linkTarget("/search?q=jollof+rice"))
        assertEquals(LinkTarget.Search("ọ̀rọ̀ & more"), linkTarget("/search?q=%E1%BB%8D%CC%80r%E1%BB%8D%CC%80%20%26%20more"))
        assertEquals(LinkTarget.Screen(Route.Search), linkTarget("/search?q=%20"))
        assertEquals(Route.SignUp(invite = "HN-NJ2F-XC26-VJJ9"), routeOf("$web/join?code=hn-nj2f-xc26-vjj9", web))
        assertEquals(Route.SignUp(), routeOf("/join", web))
        assertEquals(LinkTarget.Quote(post), linkTarget("/p/$post/quote"))
        assertEquals(LinkTarget.Browser, linkTarget("/p/$post/votes"))
        assertEquals(Route.Home, routeOf("$web/?feed=discover", web))
        assertEquals(Route.Home, routeOf(web, web))
    }

    @Test
    fun lichessAnswersThroughTheSiteAreReadAsTheAppsOwn() {
        val answer = linkTarget("/lichess/return?code=abc%2Fdef&state=xyz") as LinkTarget.LichessAnswer
        assertEquals("ng.heynaija.app://lichess/return?code=abc%2Fdef&state=xyz", answer.answer)
        assertEquals("abc/def", ng.heynaija.app.games.chess.lichessAnswer(answer.answer)?.code)
        assertEquals(LinkTarget.LichessAnswer("ng.heynaija.app://lichess/return?error=access_denied"), linkTarget("/lichess/return?error=access_denied"))
        assertEquals(LinkTarget.LichessAnswer("ng.heynaija.app://lichess/return?error=invalid_request"), linkTarget("/lichess/return"))
    }

    @Test
    fun aLinkIsToTheSiteOnlyWhenItIsOnItsAddress() {
        assertEquals("/p/$post", sitePath("/p/$post", web))
        assertEquals("/p/$post?x=1#y", sitePath("$web/p/$post?x=1#y", web))
        assertEquals("/", sitePath(web, web))
        assertEquals("/?feed=discover", sitePath("$web?feed=discover", web))
        assertNull(sitePath("//evil.example/p/$post", web))
        assertNull(sitePath("https://staging.heynaija.ng.evil.example/p/$post", web))
        assertNull(sitePath("https://staging.heynaija.ngx/p/$post", web))
        assertNull(sitePath("https://heynaija.ng/p/$post", web))
        // A debug build on staging or the dev stack is given heynaija.ng links: they are its own.
        assertEquals("/p/$post", sitePath("https://heynaija.ng/p/$post", web, orSite = true))
        assertEquals("/rules", sitePath("https://heynaija.ng/rules", "https://heynaija.ng"))
        assertEquals("/rules", sitePath("http://heynaija.ng/rules", "https://heynaija.ng"))
    }

    @Test
    fun theScreensOnlyAMemberCanUseAskToSignInFirst() {
        assertTrue(Route.Notifications.needsAccount)
        assertTrue(Route.Bookmarks.needsAccount)
        assertTrue(Route.DeleteAccount.needsAccount)
        assertTrue(Route.TriviaRound(post).needsAccount)
        assertFalse(Route.Thread(post).needsAccount)
        assertFalse(Route.Profile("ada").needsAccount)
        assertFalse(Route.Gist(gist).needsAccount)
        assertFalse(Route.Settings.needsAccount)
        assertFalse(Route.Search.needsAccount)
    }

    @Test
    fun aLinkThatAskedToSignInFirstIsKeptUntilTheFormCloses() {
        val navigator = Navigator()
        val round = "$web/games/trivia/rounds/$post"
        navigator.signInFirst(round)
        assertEquals(Route.SignIn, navigator.top.route)
        assertEquals(round, navigator.afterSignIn)
        // "Join with an invite" swaps the form; the link is still waiting.
        navigator.replace(Route.SignUp())
        assertEquals(round, navigator.afterSignIn)
        // A second link while the form is open takes over, without a second form.
        navigator.signInFirst("$web/notifications")
        assertEquals(listOf(Route.Home, Route.SignUp()), navigator.stack.map { it.route })
        assertEquals("$web/notifications", navigator.afterSignIn)
        // Closing the form forgets it.
        navigator.pop()
        assertNull(navigator.afterSignIn)
        navigator.signInFirst(round)
        navigator.reset()
        assertNull(navigator.afterSignIn)
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
        navigator.replace(Route.SignUp())
        assertEquals(Route.Gist(gist), navigator.beforeSignIn)
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

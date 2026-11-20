package ng.heynaija.app.nav

import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ng.heynaija.app.games.chess.LICHESS_RETURN_HOST
import ng.heynaija.app.games.chess.LICHESS_RETURN_SCHEME

/**
 * Links to the site that the system opens the app with instead of the browser
 * (App Links on Android, Universal Links on iOS). The app claims every page of
 * heynaija.ng; AppState.openLink decides what each one does (the rule in
 * docs/deployment/app-links.md in the web repository).
 */
object WebLinks {
    private val _opened = MutableStateFlow<String?>(null)
    /** The link the app was opened with, until it has been opened. */
    val opened: StateFlow<String?> = _opened.asStateFlow()

    fun open(link: String) {
        _opened.value = link
    }

    /** The app opened [link]; the same link again opens it again. */
    fun handled(link: String) {
        _opened.compareAndSet(link, null)
    }
}

/** The site the apps claim every link to: production's web address. */
const val SITE = "https://heynaija.ng"

/**
 * The path of a link to the site at [web], with its query and fragment (`/p/…?…#…`),
 * or null for a link to anywhere else. A path alone (`/p/…`, as push notifications
 * carry) is one. With [orSite], a link to heynaija.ng counts too, as one to [web]:
 * a debug build on staging or the dev stack is only ever given heynaija.ng links.
 */
fun sitePath(link: String, web: String, orSite: Boolean = false): String? {
    if (link.startsWith("/")) return link.takeUnless { it.startsWith("//") }
    val bases = buildList {
        add(web)
        if (web == SITE || orSite) {
            add(SITE)
            add("http://heynaija.ng")
        }
    }
    for (base in bases) {
        if (!link.startsWith(base)) continue
        val rest = link.substring(base.length)
        if (rest.isEmpty()) return "/"
        when (rest.first()) {
            '/' -> return rest
            '?', '#' -> return "/$rest"
        }
    }
    return null
}

/**
 * What a link to the site does in the app, by the rule (docs/deployment/app-links.md in the
 * web repository): a screen the app has, or the browser.
 */
sealed interface LinkTarget {
    /** A screen the app has for the page. */
    data class Screen(val route: Route) : LinkTarget

    /** Search (`/search?q=…`), with the words from the link. */
    data class Search(val words: String) : LinkTarget

    /** Writing a quote of a post (`/p/:id/quote`): its screen opens once the post has loaded. */
    data class Quote(val postId: String) : LinkTarget

    /**
     * Lichess's answer after a member connected their account from the app, which
     * Lichess sends to the site's `/lichess/return`: [answer] is the app's own
     * address for it, as that page would have passed it on (games/chess/Lichess.kt).
     */
    data class LichessAnswer(val answer: String) : LinkTarget

    /** A page the app has no screen for, or one that stays on the website: the browser opens it. */
    data object Browser : LinkTarget
}

/** What the page at [path] (from [sitePath]) does in the app. */
fun linkTarget(path: String): LinkTarget {
    val withoutFragment = path.substringBefore('#')
    val query = withoutFragment.substringAfter('?', "")
        .split('&')
        .filter { '=' in it }
        .associate { it.substringBefore('=') to it.substringAfter('=') }
    val parts = withoutFragment.substringBefore('?').split('/').filter { it.isNotEmpty() }
    return when {
        parts == listOf("search") ->
            query["q"]?.let(::queryValue)?.trim()?.takeIf { it.isNotEmpty() }?.let { LinkTarget.Search(it.take(80)) }
                ?: LinkTarget.Screen(Route.Search)
        // An invite link, as the web's /join reads it.
        parts == listOf("join") -> LinkTarget.Screen(Route.SignUp(invite = query["code"]?.let(::queryValue)?.trim()?.take(40)?.uppercase() ?: ""))
        parts.size == 3 && parts[0] == "p" && ID.matches(parts[1]) && parts[2] == "quote" -> LinkTarget.Quote(parts[1])
        parts == listOf("lichess", "return") -> LinkTarget.LichessAnswer(lichessReturn(query))
        else -> screenOf(parts, query)?.let { LinkTarget.Screen(it) } ?: LinkTarget.Browser
    }
}

/**
 * The screen for a link to the site: a path such as `/p/…`, as push
 * notifications carry, or a full address on [web]. Null for a link the app has no
 * screen for, or one to somewhere else.
 */
fun routeOf(link: String, web: String): Route? =
    when (val target = sitePath(link, web)?.let(::linkTarget)) {
        is LinkTarget.Screen -> target.route
        is LinkTarget.Search -> Route.Search
        else -> null
    }

/** Exactly a post's, a Gist's or a Games round's ID. */
private val ID = Regex("^[0-9a-fA-F-]{36}$")

private fun screenOf(parts: List<String>, query: Map<String, String>): Route? = when {
    parts.isEmpty() -> Route.Home
    parts == listOf("notifications") -> Route.Notifications
    parts == listOf("settings") -> Route.Settings
    parts == listOf("settings", "delete") -> Route.DeleteAccount
    parts == listOf("bookmarks") -> Route.Bookmarks
    parts == listOf("c") -> Route.Communities
    parts.size == 2 && parts[0] == "hashtag" && Regex("^\\w{1,50}$").matches(parts[1]) -> Route.Hashtag(parts[1].lowercase())
    parts.size == 2 && parts[0] == "p" && ID.matches(parts[1]) -> Route.Thread(parts[1])
    // A reply in a Gist opens as the conversation in focus (?reply=), as on the web.
    parts.size == 2 && parts[0] == "gists" && ID.matches(parts[1]) ->
        query["reply"]?.takeIf { ID.matches(it) }?.let { Route.Thread(it, gist = parts[1]) } ?: Route.Gist(parts[1])
    parts.size == 2 && parts[0] == "u" && Regex("^[A-Za-z0-9_]{1,20}$").matches(parts[1]) -> Route.Profile(parts[1].lowercase())
    parts.size == 2 && parts[0] == "c" && Regex("^[a-z0-9]+(-[a-z0-9]+)*$").matches(parts[1]) -> Route.Community(parts[1])
    parts.firstOrNull() == "games" -> gamesRoute(parts.drop(1), query)
    else -> null
}

/** A value from a link's query, with its `%xx` and `+` turned back into what they stand for. */
private fun queryValue(raw: String): String = runCatching { raw.decodeURLQueryComponent(plusIsSpace = true) }.getOrDefault(raw)

/** The app's own address for Lichess's answer, made as the site's `/lichess/return` makes it. */
private fun lichessReturn(query: Map<String, String>): String {
    val code = query["code"]?.let(::queryValue)?.takeIf { it.isNotBlank() }
    val state = query["state"]?.let(::queryValue)?.takeIf { it.isNotBlank() }
    val back = if (code != null && state != null) {
        "code=${code.take(500).encodeURLParameter()}&state=${state.take(200).encodeURLParameter()}"
    } else {
        "error=${if (query["error"] == "access_denied") "access_denied" else "invalid_request"}"
    }
    return "$LICHESS_RETURN_SCHEME://$LICHESS_RETURN_HOST/return?$back"
}

/**
 * Games' pages (ADR 0028 in the web repository): `/games`, its leaderboard and
 * scoring pages, chess and Trivia (playing; hosting stays on the website). Keno
 * has no screen in the apps, and must not have one while the Apple developer
 * account is an individual's (Platform.keno), so its links open in the browser.
 * Whether the member has Games is checked where the link is opened
 * (AppState.openLink), not here.
 */
private fun gamesRoute(parts: List<String>, query: Map<String, String>): Route? = when {
    parts.isEmpty() -> Route.Games
    parts == listOf("leaderboard") -> Route.GamesLeaderboard()
    parts == listOf("scoring") -> Route.GamesScoring
    parts == listOf("chess") -> Route.Chess(tab = query["tab"]?.takeIf { it in GAME_TABS } ?: "play")
    parts == listOf("trivia") -> Route.Trivia(tab = query["tab"]?.takeIf { it in GAME_TABS } ?: "play")
    parts.size == 3 && parts[0] == "trivia" && parts[1] == "rounds" && ID.matches(parts[2]) -> Route.TriviaRound(parts[2])
    parts == listOf("competitions") -> Route.Competitions
    parts.size == 2 && parts[0] == "competitions" && ID.matches(parts[1]) -> Route.Competition(parts[1])
    parts.size == 3 && parts[0] == "chess" && parts[1] == "teams" && Regex("^[a-z0-9-]{1,60}$").matches(parts[2]) -> Route.ChessTeam(parts[2])
    // A game's id is Lichess's: eight letters and digits.
    parts.size == 3 && parts[0] == "chess" && parts[1] == "games" && Regex("^[A-Za-z0-9]{8,12}$").matches(parts[2]) -> Route.ChessGame(parts[2])
    else -> null
}

/** The tabs every game's page has, in the web's order (docs/games/GAMES.md). */
val GAME_TABS = listOf("play", "leaderboard", "competitions", "posts")

/** A Games screen, which only a member with Games may open. */
val Route.gamesLink: Boolean
    get() = this is Route.Games || this is Route.GamesLeaderboard || this is Route.GamesScoring || this is Route.Chess ||
        this is Route.ChessGame || this is Route.Competitions || this is Route.Competition || this is Route.ChessTeam ||
        this is Route.Trivia || this is Route.TriviaRound

/**
 * Screens only a member can use. Someone signed out who opens a link to one signs
 * in first, then the screen opens. Posts, profiles, communities, Gists and search
 * show to anyone, as on the web.
 */
val Route.needsAccount: Boolean
    get() = gamesLink || this is Route.Notifications || this is Route.Bookmarks || this is Route.DeleteAccount ||
        this is Route.EditProfile || this is Route.NotificationSettings || this is Route.SensitiveSettings

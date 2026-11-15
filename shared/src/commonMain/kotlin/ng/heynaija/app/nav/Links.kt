package ng.heynaija.app.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Links to the site that the system opens the app with instead of the browser
 * (App Links on Android, Universal Links on iOS). The site's own files say which
 * (apps/web/lib/app-links.ts in the web repository), and the Android manifest
 * lists the same pages: those [routeOf] has a screen for.
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

/**
 * The screen for a link to the site: a path such as `/p/…`, as push
 * notifications carry, or a full address on [web]. Null for a link the app has no
 * screen for, or one to somewhere else.
 */
fun routeOf(link: String, web: String): Route? {
    val rest = when {
        link.startsWith("/") -> link
        link.startsWith("$web/") -> link.removePrefix(web)
        else -> return null
    }
    val path = rest.substringBefore('#').substringBefore('?')
    val query = rest.substringBefore('#').substringAfter('?', "")
        .split('&')
        .filter { '=' in it }
        .associate { it.substringBefore('=') to it.substringAfter('=') }
    val parts = path.split('/').filter { it.isNotEmpty() }
    val id = Regex("^[0-9a-fA-F-]{36}$")
    return when {
        parts.isEmpty() -> Route.Home
        parts == listOf("notifications") -> Route.Notifications
        parts == listOf("settings") -> Route.Settings
        parts == listOf("bookmarks") -> Route.Bookmarks
        parts.size == 2 && parts[0] == "hashtag" && Regex("^\\w{1,50}$").matches(parts[1]) -> Route.Hashtag(parts[1].lowercase())
        parts.size == 2 && parts[0] == "p" && id.matches(parts[1]) -> Route.Thread(parts[1])
        // A reply in a Gist opens as the conversation in focus (?reply=), as on the web.
        parts.size == 2 && parts[0] == "gists" && id.matches(parts[1]) ->
            query["reply"]?.takeIf { id.matches(it) }?.let { Route.Thread(it, gist = parts[1]) } ?: Route.Gist(parts[1])
        parts.size == 2 && parts[0] == "u" && Regex("^[A-Za-z0-9_]{1,20}$").matches(parts[1]) -> Route.Profile(parts[1].lowercase())
        parts.size == 2 && parts[0] == "c" && Regex("^[a-z0-9]+(-[a-z0-9]+)*$").matches(parts[1]) -> Route.Community(parts[1])
        parts.firstOrNull() == "games" -> gamesRoute(parts.drop(1), query)
        else -> null
    }
}

/**
 * Games' pages (ADR 0028 in the web repository): `/games`, its leaderboard and
 * scoring pages, chess and Trivia (playing; hosting stays on the website). Keno
 * has no screen in the apps yet, so its links open nothing. Whether the member has Games is checked where the link is opened
 * ([gamesLink]), not here.
 */
private fun gamesRoute(parts: List<String>, query: Map<String, String>): Route? = when {
    parts.isEmpty() -> Route.Games
    parts == listOf("leaderboard") -> Route.GamesLeaderboard()
    parts == listOf("scoring") -> Route.GamesScoring
    parts == listOf("chess") -> Route.Chess(tab = query["tab"]?.takeIf { it in GAME_TABS } ?: "play")
    parts == listOf("trivia") -> Route.Trivia(tab = query["tab"]?.takeIf { it in GAME_TABS } ?: "play")
    parts.size == 3 && parts[0] == "trivia" && parts[1] == "rounds" && Regex("^[0-9a-fA-F-]{36}$").matches(parts[2]) -> Route.TriviaRound(parts[2])
    parts == listOf("competitions") -> Route.Competitions
    parts.size == 2 && parts[0] == "competitions" && Regex("^[0-9a-fA-F-]{36}$").matches(parts[1]) -> Route.Competition(parts[1])
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

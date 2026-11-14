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
        else -> null
    }
}

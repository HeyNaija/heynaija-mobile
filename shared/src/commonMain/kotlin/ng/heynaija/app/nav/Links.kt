package ng.heynaija.app.nav

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

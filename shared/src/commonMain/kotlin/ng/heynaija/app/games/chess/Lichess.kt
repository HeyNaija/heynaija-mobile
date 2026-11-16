package ng.heynaija.app.games.chess

import androidx.compose.runtime.Composable
import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where Lichess's answer comes back to the app after a member says yes or no to
 * connecting their account. Lichess sends it to the website
 * (`/lichess/return` in the web repository), which passes the code and state on to
 * this address; the app then finishes the link with its own session. The code is
 * no use to anything else that catches it: the PKCE verifier never leaves the API.
 */
const val LICHESS_RETURN_SCHEME = "ng.heynaija.app"
const val LICHESS_RETURN_HOST = "lichess"

/** Lichess's answer as the app was opened with it: Android's browser hands it to MainActivity, iOS's sheet to [LichessLink]. */
object LichessReturn {
    private val _received = MutableStateFlow<String?>(null)
    val received: StateFlow<String?> = _received.asStateFlow()

    fun deliver(link: String) {
        if (isLichessReturn(link)) _received.value = link
    }

    fun handled(link: String) {
        _received.compareAndSet(link, null)
    }
}

fun isLichessReturn(link: String) = link.startsWith("$LICHESS_RETURN_SCHEME://$LICHESS_RETURN_HOST")

data class LichessAnswer(val code: String?, val state: String?, val error: String?)

/** The code and state in what Lichess sent back, or why there are none; null for any other address. */
fun lichessAnswer(link: String): LichessAnswer? {
    if (!isLichessReturn(link)) return null
    val url = runCatching { Url(link) }.getOrNull() ?: return null
    fun param(name: String) = url.parameters[name]?.takeIf { it.isNotBlank() }
    return LichessAnswer(param("code")?.take(500), param("state")?.take(200), param("error"))
}

/** Opens Lichess's page for connecting an account: the browser on Android, iOS's sign-in sheet on iOS. */
interface LichessLink {
    fun open(url: String)
}

@Composable
expect fun rememberLichessLink(): LichessLink

/** The API's refusals for chess, in the web's words (apps/web/app/(main)/games/chess/page.tsx). */
fun chessNotice(code: String?): String? = when (code) {
    "link_expired" -> "That link to Lichess ran out or was not yours. Start again."
    "already_linked" -> "That Lichess account is already connected to another HeyNaija member."
    "not_available" -> "Connecting Lichess is not available here yet."
    "reconnect" -> "Lichess no longer accepts your connection. Disconnect and connect Lichess again."
    "lichess_busy" -> "Lichess is busy. Try again in a minute."
    "lichess_unavailable" -> "Lichess did not answer. Try again in a moment."
    "lichess_rejected" -> "Lichess would not make that game. Their Lichess connection may have lapsed."
    "not_found" -> "That challenge is no longer open."
    "not_linked" -> "Connect your Lichess account first."
    "too_many", "rate_limited" -> "That was a lot of requests in a short time. Try again later."
    else -> null
}

package ng.heynaija.app.gists

import kotlin.time.Instant
import ng.heynaija.app.api.GistView
import ng.heynaija.app.api.GistViewer
import ng.heynaija.app.api.LiveGist

// The rules a Gist's room follows, after apps/web/lib/gists.ts and gist-session.ts:
// no Compose and no LiveKit here, so commonTest covers them.

/**
 * What the member's role lets them do. The API checks every one again; this only
 * decides what to draw, so a listener is never shown a microphone.
 */
data class GistControls(
    val mic: Boolean = false,
    val request: Boolean = false,
    val cancelRequest: Boolean = false,
    val moderate: Boolean = false,
    val settings: Boolean = false,
    val end: Boolean = false,
    val appointCohost: Boolean = false,
)

fun controlsFor(status: String, requestsOpen: Boolean, viewer: GistViewer): GistControls {
    val role = viewer.role
    if (status != "live" || role == null) return GistControls()
    return GistControls(
        mic = role == "host" || role == "cohost" || role == "speaker",
        request = role == "listener" && requestsOpen && !viewer.requestedToSpeak,
        cancelRequest = role == "listener" && viewer.requestedToSpeak,
        moderate = role == "host" || role == "cohost",
        settings = role == "host",
        end = role == "host",
        appointCohost = role == "host",
    )
}

fun controlsFor(view: GistView) = controlsFor(view.status, view.requestsOpen, view.viewer)

/**
 * A polled view folded into what the room last knew. [announcement] is set only
 * when something changed; [resync] asks the room to call /sync, so LiveKit's
 * permissions follow the new role.
 */
data class Applied(val role: String?, val status: String, val ended: Boolean, val announcement: String?, val resync: Boolean)

private fun roleSentence(role: String) = when (role) {
    "host" -> "You are the host."
    "cohost" -> "You are now a co-host. Tap the microphone to talk."
    "speaker" -> "You are now a speaker. Tap the microphone to talk."
    else -> "You are now a listener. Your microphone is off."
}

fun applyView(prevRole: String?, prevStatus: String, view: GistView): Applied {
    val role = view.viewer.role
    val ended = view.status == "ended"
    var announcement: String? = null
    var resync = false
    if (ended && prevStatus != "ended") {
        announcement = "This Gist has ended."
    } else if (!ended && role != null && prevRole != null && role != prevRole) {
        announcement = roleSentence(role)
        resync = true
    }
    return Applied(role ?: prevRole, view.status, ended, announcement, resync)
}

const val POLL_BASE_MS = 4_000L
const val POLL_MAX_MS = 30_000L

/** The wait before the next look at the room; failures back off. */
fun nextPollDelay(failures: Int): Long {
    var delay = POLL_BASE_MS
    repeat(failures.coerceIn(0, 10)) { delay *= 2 }
    return delay.coerceAtMost(POLL_MAX_MS)
}

/** How often Home asks for the Live now list while it is on screen. */
const val LIVE_POLL_MS = 30_000L

const val RECONNECT_ATTEMPTS = 8

fun reconnectDelay(attempt: Int): Long {
    var delay = 1_000L
    repeat(attempt.coerceIn(0, 4)) { delay *= 2 }
    return delay.coerceAtMost(15_000L)
}

enum class JoinFailure { NotFound, Full, Locked, Ended, Retry }

/** Which room state a refused join or reconnect leads to. */
fun joinFailure(status: Int, code: String?): JoinFailure = when {
    status == 404 -> JoinFailure.NotFound
    status == 409 && code == "gist_full" -> JoinFailure.Full
    status == 409 && code == "gist_locked" -> JoinFailure.Locked
    status == 409 && code == "gist_ended" -> JoinFailure.Ended
    else -> JoinFailure.Retry
}

/**
 * Orders polled views. Answers arrive in any order, so each request takes a ticket
 * when it starts; an answer applies only if it started after the last one applied
 * and after the member's last action (a view fetched before the action may predate
 * it, and must not undo it, least of all as a role downgrade).
 */
class Latest {
    private var issued = 0
    private var applied = 0
    private var floor = 0

    fun begin() = ++issued

    fun accept(ticket: Int): Boolean {
        if (ticket <= applied || ticket <= floor) return false
        applied = ticket
        return true
    }

    /** An action of the member's has finished: views asked for before now are out of date. */
    fun noteAction() {
        floor = issued
    }
}

/** 1,234: the web writes counts the Nigerian way, with commas. */
fun grouped(n: Int): String {
    val digits = n.toString().removePrefix("-")
    val out = StringBuilder()
    digits.forEachIndexed { i, c ->
        if (i > 0 && (digits.length - i) % 3 == 0) out.append(',')
        out.append(c)
    }
    return if (n < 0) "-$out" else out.toString()
}

fun listenerLabel(n: Int) = when {
    n <= 0 -> "No listeners yet"
    n == 1 -> "1 listener"
    else -> "${grouped(n)} listeners"
}

/** The line under a Live now title: who hosts it, in which community, how many listen. */
fun liveMeta(gist: LiveGist) =
    listOfNotNull("Hosted by ${gist.host.name}", gist.community?.let { "in ${it.name}" }, listenerLabel(gist.listenerCount)).joinToString(" · ")

/** "12:03" under an hour, "1:02:03" after; empty before the Gist has started. */
fun elapsedLabel(startedAt: String?, now: Instant): String {
    val started = startedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return ""
    val seconds = (now - started).inWholeSeconds.coerceAtLeast(0)
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    fun two(n: Long) = n.toString().padStart(2, '0')
    return if (h > 0) "$h:${two(m)}:${two(s)}" else "$m:${two(s)}"
}

/** What the API accepts for a title (GistsService.create). */
const val TITLE_MAX = 120

fun titleProblem(title: String): String? {
    val t = title.trim()
    if (t.isEmpty()) return "Give your Gist a title."
    if (t.length > TITLE_MAX) return "Keep the title to $TITLE_MAX characters or fewer."
    return null
}

/** Who may reply to a Gist, as its host chose, in the web's words. */
val GIST_REPLY_AUDIENCES = listOf(
    "everyone" to "Everyone can reply",
    "following" to "People you follow can reply",
    "mentioned" to "Only people you mention can reply",
    "off" to "Replies off",
)

/** The note over a Gist's replies: the host's own wording for the host, a neutral one for everyone else. */
fun gistReplyAudienceNote(audience: String, hostHandle: String, viewerIsHost: Boolean): String {
    val label = GIST_REPLY_AUDIENCES.firstOrNull { it.first == audience }?.second ?: "Everyone can reply"
    if (viewerIsHost) return label
    return when (audience) {
        "following" -> "People @$hostHandle follows can reply"
        "mentioned" -> "People @$hostHandle mentions can reply"
        else -> label
    }
}

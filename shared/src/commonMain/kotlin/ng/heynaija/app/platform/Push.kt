package ng.heynaija.app.platform

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Push notifications on this device (ADR 0023 in the web repository): the token
 * the push service gave this installation, and the key the server seals what it
 * sends with, so that the push service cannot read it.
 */
interface PushDevice {
    /** Which push service the token is from: "fcm". */
    val service: String

    /** This installation's token, or null when the push service cannot give one now (offline, no Google Play). */
    suspend fun token(): String?

    /** Gives the token up, so that the push service refuses whatever is still sent to it. */
    suspend fun deleteToken()

    /** Whether the system lets the app show notifications. */
    fun allowed(): Boolean

    /** 32 random bytes in base64url, made the first time and kept in secure storage. */
    fun key(): String

    /** Makes a new key next time: after signing out, so the next member's are sealed with another. */
    fun forgetKey()
}

/**
 * What happens to push outside the app's screens, for the app to act on: the
 * system's push code runs on its own, with or without the app open.
 */
object PushEvents {
    private val _newToken = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** The push service gave this installation a new token: the server needs it. */
    val newToken: SharedFlow<String> = _newToken.asSharedFlow()

    private val _arrived = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** A notification was shown: the unread count has moved. */
    val arrived: SharedFlow<Unit> = _arrived.asSharedFlow()

    private val _opened = MutableStateFlow<String?>(null)
    /** The link of a notification that was tapped, until the app has opened it. */
    val opened: StateFlow<String?> = _opened.asStateFlow()

    fun tokenChanged(token: String) {
        _newToken.tryEmit(token)
    }

    fun shown() {
        _arrived.tryEmit(Unit)
    }

    fun open(link: String) {
        _opened.value = link
    }

    /** The app opened [link]; a later tap on the same notification opens it again. */
    fun handled(link: String) {
        _opened.compareAndSet(link, null)
    }
}

/** Asking to show notifications, and the system's settings for them when the member said no before. */
class NotificationPermission(val ask: () -> Unit, val openSettings: () -> Unit)

/**
 * Asks to show notifications where the system asks (Android 13 and later), and
 * tells [onResult] whether they may show. Where it does not ask, the answer is
 * whether they are on in the system's settings.
 */
@Composable
expect fun rememberNotificationPermission(onResult: (allowed: Boolean) -> Unit): NotificationPermission

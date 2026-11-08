@file:OptIn(ExperimentalForeignApi::class)

package ng.heynaija.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSISOLatin1StringEncoding
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.stringWithContentsOfFile
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenNotificationSettingsURLString
import platform.UIKit.registerForRemoteNotifications
import platform.UIKit.unregisterForRemoteNotifications
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * The app group the app shares with its notification service extension
 * (iosApp/NotificationService): the push key in the Keychain, and the member
 * signed in, in the group's defaults. The extension names it too.
 */
const val APP_GROUP = "group.ng.heynaija.app"

/**
 * Push through Apple's push service (ADR 0026 in the web repository). The token
 * is iOS's for this installation, asked for only once the member has push on. The
 * key is ours, made here and kept in the Keychain under [APP_GROUP], where the
 * notification service extension reads it to open what arrives, before iOS shows
 * it.
 */
class ApnsPushDevice(private val shared: KeyValueStore) : PushDevice {
    override val service = if (apsSandbox()) "apns_sandbox" else "apns"

    override suspend fun token(): String? = ApnsTokens.request()

    override suspend fun deleteToken() {
        // Apple then refuses what the server still sends here, and the next sign-in asks for a new token.
        UIApplication.sharedApplication.unregisterForRemoteNotifications()
        ApnsTokens.forget()
    }

    override suspend fun allowed(): Boolean = suspendCancellableCoroutine { continuation ->
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus
            continuation.resume(
                status == UNAuthorizationStatusAuthorized || status == UNAuthorizationStatusProvisional || status == UNAuthorizationStatusEphemeral,
            )
        }
    }

    override fun key(): String = shared.get(KEY) ?: newKey().also { shared.set(KEY, it) }

    override fun forgetKey() = shared.set(KEY, null)

    companion object {
        /** The Keychain item the key is kept in; NotificationService.swift reads the same. */
        const val KEY = "push_key"

        /** 32 random bytes in base64url, as the server takes a key. */
        private fun newKey(): String {
            val bytes = ByteArray(32)
            val status = bytes.usePinned { SecRandomCopyBytes(kSecRandomDefault, bytes.size.toULong(), it.addressOf(0)) }
            check(status == 0) { "no random bytes for the push key ($status)" }
            return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)
        }
    }
}

/**
 * Whether iOS gives this build its push token from Apple's sandbox: one run in the
 * simulator, or signed for development as Xcode signs a build it runs on a phone.
 * App Store and TestFlight builds carry no provisioning profile, and use production.
 */
private fun apsSandbox(): Boolean {
    if (NSProcessInfo.processInfo.environment["SIMULATOR_UDID"] != null) return true
    val path = NSBundle.mainBundle.pathForResource("embedded", ofType = "mobileprovision") ?: return false
    // The profile is a signed plist; its XML is readable as it is.
    val profile = NSString.stringWithContentsOfFile(path, encoding = NSISOLatin1StringEncoding, error = null) ?: return false
    return "<key>aps-environment</key><string>development</string>" in profile.filterNot(Char::isWhitespace)
}

/**
 * What iOS tells the app delegate (iOSApp.swift) about registering for remote
 * notifications, for [ApnsPushDevice]: iOS answers each request there, not to the
 * code that asked.
 */
object ApnsTokens {
    private val waiting = mutableListOf<CompletableDeferred<String?>>()
    private var last: String? = null

    /** This installation's token: registers with Apple, as Apple asks apps to at each start. Null if iOS has not answered within 30 seconds. */
    internal suspend fun request(): String? {
        val answer = CompletableDeferred<String?>()
        waiting += answer
        UIApplication.sharedApplication.registerForRemoteNotifications()
        return withTimeoutOrNull(30_000) { answer.await() }.also { waiting -= answer }
    }

    internal fun forget() {
        last = null
    }

    /** iOS registered the app with Apple's push service. */
    fun registered(token: NSData) {
        val hex = token.toByteArray().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        val changed = last != null && last != hex
        last = hex
        answer(hex)
        // Apple gave it a new token while the app was open: the server needs it.
        if (changed) PushEvents.tokenChanged(hex)
    }

    /** iOS could not register: no network, or a build without the push entitlement (the simulator without a team). */
    fun failed(error: NSError) {
        println("HeyNaija: iOS did not register for push: ${error.localizedDescription}")
        answer(null)
    }

    private fun answer(token: String?) {
        waiting.toList().forEach { it.complete(token) }
        waiting.clear()
    }
}

@Composable
actual fun rememberNotificationPermission(onResult: (allowed: Boolean) -> Unit): NotificationPermission {
    val latest = rememberUpdatedState(onResult)
    return remember {
        NotificationPermission(
            ask = {
                // Asked once; after that iOS answers at once with what the member chose, or set in Settings.
                UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { granted, _ ->
                    dispatch_async(dispatch_get_main_queue()) { latest.value(granted) }
                }
            },
            openSettings = {
                NSURL.URLWithString(UIApplicationOpenNotificationSettingsURLString)?.let {
                    UIApplication.sharedApplication.openURL(it, options = emptyMap<Any?, Any>(), completionHandler = null)
                }
            },
        )
    }
}

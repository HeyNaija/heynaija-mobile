package ng.heynaija.app.platform

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlinx.serialization.Serializable
import ng.heynaija.app.api.HeyNaijaApi

@Composable
actual fun rememberNotificationPermission(onResult: (allowed: Boolean) -> Unit): NotificationPermission {
    val context = LocalContext.current
    val latest = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { latest.value(it) }
    return remember(context) {
        NotificationPermission(
            ask = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                // Before Android 13 there is nothing to ask: only the system's settings turn them off.
                else latest.value(NotificationManagerCompat.from(context).areNotificationsEnabled())
            },
            openSettings = {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                }
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
        )
    }
}

/** A push notification as the server sealed it (fcm-sender.ts in the web repository), opened. */
@Serializable
data class PushNote(val title: String, val body: String = "", val url: String, val tag: String, val member: String)

/**
 * The sealing the server does for this installation: AES-256-GCM under a key
 * the app made, sent as base64url of a 12-byte nonce, the ciphertext and the
 * 16-byte tag. What the push service carries, it cannot read.
 */
object SealedPush {
    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    /** A new key: 32 random bytes in base64url, as the server takes it. */
    fun newKey(): String = base64.encode(ByteArray(32).also { SecureRandom().nextBytes(it) })

    /** [sealed] opened with [key], or null if it was not sealed with it or was changed on the way. */
    fun open(key: String, sealed: String): PushNote? = runCatching {
        val bytes = base64.decode(sealed)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(base64.decode(key), "AES"), GCMParameterSpec(128, bytes, 0, 12))
        val text = cipher.doFinal(bytes, 12, bytes.size - 12).decodeToString()
        HeyNaijaApi.json.decodeFromString(PushNote.serializer(), text)
    }.getOrNull()
}

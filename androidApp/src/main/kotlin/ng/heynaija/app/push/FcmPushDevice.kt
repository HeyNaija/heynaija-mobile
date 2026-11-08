package ng.heynaija.app.push

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import ng.heynaija.app.platform.KeyValueStore
import ng.heynaija.app.platform.PushDevice
import ng.heynaija.app.platform.SealedPush

/**
 * Push through Firebase Cloud Messaging (ADR 0023 in the web repository). The
 * token is Firebase's for this installation, and asked for only once the member
 * has push on: Firebase's own start-up token is switched off in the manifest.
 * The key is ours, made here and kept in the Keystore-backed store, where
 * [PushService] reads it to open what arrives.
 */
class FcmPushDevice private constructor(private val context: Context, private val secure: KeyValueStore) : PushDevice {
    override val service = "fcm"

    override suspend fun token(): String? = try {
        FirebaseMessaging.getInstance().run {
            // From now on Firebase keeps the token fresh, and says so in onNewToken.
            isAutoInitEnabled = true
            token.await()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // No Google Play services, or offline: the next start tries again.
        null
    }

    override suspend fun deleteToken() {
        try {
            FirebaseMessaging.getInstance().run {
                isAutoInitEnabled = false
                deleteToken().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline: the token stays, and PushService drops what a signed-out phone is sent.
        }
    }

    override suspend fun allowed(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    override fun key(): String = secure.get(KEY) ?: SealedPush.newKey().also { secure.set(KEY, it) }

    override fun forgetKey() = secure.set(KEY, null)

    companion object {
        const val KEY = "push_key"

        /** Push on this phone, or null in a build made without google-services.json. */
        fun orNull(context: Context, secure: KeyValueStore): PushDevice? =
            if (FirebaseApp.getApps(context).isEmpty()) null else FcmPushDevice(context.applicationContext, secure)
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        val error = task.exception
        if (error == null && task.isSuccessful) continuation.resume(task.result)
        else continuation.resumeWithException(error ?: IllegalStateException("Firebase did not finish"))
    }
}

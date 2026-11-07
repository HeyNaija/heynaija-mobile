package ng.heynaija.app.push

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import ng.heynaija.app.MainActivity
import ng.heynaija.app.R
import ng.heynaija.app.platform.PushEvents
import ng.heynaija.app.platform.PushNote
import ng.heynaija.app.platform.SealedPush
import ng.heynaija.app.platform.androidSecureStore
import ng.heynaija.app.platform.androidSettingsStore
import ng.heynaija.app.savedMemberId

/**
 * Where Firebase hands over a push, open or not: the server's sealed message
 * (ADR 0023 in the web repository), shown as the notification it describes, or
 * dropped when it is not for the member signed in here.
 */
class PushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        PushEvents.tokenChanged(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val secure = androidSecureStore(applicationContext)
        val member = savedMemberId(secure, androidSettingsStore(applicationContext))
        if (member == null) {
            // Signed out, and the server was not told: make Firebase refuse what follows.
            FirebaseMessaging.getInstance().deleteToken()
            return
        }
        val key = secure.get(FcmPushDevice.KEY) ?: return
        val note = message.data["sealed"]?.let { SealedPush.open(key, it) } ?: return
        // Someone else's: the app registers this phone for whoever is signed in when it next starts.
        if (note.member != member) return
        show(applicationContext, note)
        PushEvents.shown()
    }

    companion object {
        const val EXTRA_LINK = "ng.heynaija.app.LINK"
        private const val CHANNEL = "notifications"

        /** One notification per tag: a newer one replaces it, as on the web. Tapping it opens its link in the app. */
        fun show(context: Context, note: PushNote) {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName("Notifications")
                    .setDescription("Replies, mentions, likes, reposts, follows and moderation notices")
                    .build(),
            )
            val open = Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_LINK, note.url)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // A request code per tag, so that each notification keeps its own link.
            val tap = PendingIntent.getActivity(context, note.tag.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.accent))
                .setContentTitle(note.title)
                .apply { if (note.body.isNotEmpty()) setContentText(note.body).setStyle(NotificationCompat.BigTextStyle().bigText(note.body)) }
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setShowWhen(true)
                .build()
            manager.notify(note.tag, 0, notification)
        }
    }
}

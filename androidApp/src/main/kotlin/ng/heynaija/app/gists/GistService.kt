package ng.heynaija.app.gists

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import ng.heynaija.app.R
import ng.heynaija.app.platform.GistKeeper
import ng.heynaija.app.platform.GistNow

/**
 * Tells Android a Gist is playing, so that it goes on with the app off screen or
 * the phone locked: [GistService] runs in the foreground, with a notification
 * that names the Gist, says whether the microphone is on, and can leave it.
 * Without it Android stops an app's sound, and its microphone, soon after the
 * app leaves the screen.
 */
class AndroidGistKeeper(private val context: Context) : GistKeeper {
    override fun keep(now: GistNow, leave: () -> Unit) {
        val starting = GistService.now == null
        GistService.now = now
        GistService.leave = leave
        if (starting) {
            // A member joins from the app's own screen, so the app is in front and may start it.
            try {
                ContextCompat.startForegroundService(context, Intent(context, GistService::class.java))
            } catch (e: Exception) {
                // Android refused (the app was not in front after all): the Gist plays while the app is open, as before.
                GistService.now = null
                GistService.leave = null
            }
        } else {
            GistService.running?.show(now)
        }
    }

    override fun release() {
        GistService.now = null
        GistService.leave = null
        GistService.running?.finish()
    }
}

/**
 * The foreground service behind a Gist the member is in. It holds nothing itself:
 * the room lives in the app (GistRooms); this only keeps the process in the
 * foreground and shows the notification. Its type is media playback while the
 * member listens, and microphone too while theirs is on, which Android 14 and
 * later ask to be declared (and Play Console to be told why: docs/release.md).
 */
class GistService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = this
    }

    override fun onDestroy() {
        if (running === this) running = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_LEAVE) {
            // The notification's button. The room tells the keeper it is out, which ends this service.
            leave?.invoke() ?: finish()
            return START_NOT_STICKY
        }
        val now = now
        if (now == null) {
            // The member left before the service got going. Android still wants its notification once it was asked for one.
            show(GistNow("", "", micOn = false, reconnecting = false))
            finish()
        } else {
            show(now)
        }
        // If the system kills the app the room is gone with it: nothing to come back to.
        return START_NOT_STICKY
    }

    /** The app was swiped away from the recent apps: out of the Gist, as closing the page is on the web. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        leave?.invoke()
        finish()
        super.onTaskRemoved(rootIntent)
    }

    /** The types this service is in the foreground with now; null before it is. */
    private var types: Int? = null

    fun show(now: GistNow) {
        val notification = notification(now)
        // Microphone only while it is on, and only with the permission: Android refuses the type otherwise.
        val microphone = now.micOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val playback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        val wanted = if (microphone) playback or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else playback
        if (types == wanted) {
            // Only the words changed. The service keeps its types: asking again for the microphone's with the app
            // off screen could be refused, and must not cost a member who is speaking their microphone.
            if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                try {
                    NotificationManagerCompat.from(this).notify(NOTIFICATION, notification)
                } catch (e: SecurityException) {
                    // Notifications are not allowed: nothing to update.
                }
            }
            return
        }
        types = try {
            ServiceCompat.startForeground(this, NOTIFICATION, notification, wanted)
            wanted
        } catch (e: SecurityException) {
            // The microphone type was refused (the app is not in front, or the permission went): keep the sound.
            ServiceCompat.startForeground(this, NOTIFICATION, notification, playback)
            playback
        }
    }

    fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(now: GistNow) = NotificationCompat.Builder(this, channel(this))
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(ContextCompat.getColor(this, R.color.accent))
        .setContentTitle(now.title.ifBlank { "Gist" })
        .setContentText(
            when {
                now.reconnecting -> "Reconnecting…"
                now.micOn -> "You are in this Gist. Your microphone is on."
                else -> "You are in this Gist."
            },
        )
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        // Tapping it brings the app back as it was: the room, or the bar that leads to it.
        .setContentIntent(
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            },
        )
        .addAction(
            0,
            "Leave",
            PendingIntent.getService(
                this,
                1,
                Intent(this, GistService::class.java).setAction(ACTION_LEAVE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    companion object {
        private const val ACTION_LEAVE = "ng.heynaija.app.gists.LEAVE"
        private const val CHANNEL = "gist"
        private const val NOTIFICATION = 17

        // The Gist the member is in and the way out of it, as the app last said (AndroidGistKeeper). Main thread only.
        internal var now: GistNow? = null
        internal var leave: (() -> Unit)? = null
        internal var running: GistService? = null

        /** Quiet: it is there to show the Gist is playing and to leave it, not to announce anything. */
        private fun channel(context: Context): String {
            NotificationManagerCompat.from(context).createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName("Gist you are in")
                    .setDescription("Shows while you are in a Gist, so it keeps playing when you leave the app or lock your phone")
                    .setShowBadge(false)
                    .build(),
            )
            return CHANNEL
        }
    }
}

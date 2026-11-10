package ng.heynaija.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ng.heynaija.app.nav.WebLinks
import ng.heynaija.app.platform.PushEvents
import ng.heynaija.app.platform.androidPlatform
import ng.heynaija.app.push.FcmPushDevice
import ng.heynaija.app.push.PushService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val debug = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val platform = androidPlatform(applicationContext, debug) { secure -> FcmPushDevice.orNull(applicationContext, secure) }
        // Started by tapping a notification or a link; not again when the activity is only recreated.
        if (savedInstanceState == null) opened(intent)
        setContent { App(platform) }
    }

    /** A notification or a link tapped while the app was open. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        opened(intent)
    }

    private fun opened(intent: Intent?) {
        intent?.getStringExtra(PushService.EXTRA_LINK)?.let(PushEvents::open)
        // A heynaija.ng link (the manifest's App Links filter).
        if (intent?.action == Intent.ACTION_VIEW) intent.dataString?.let(WebLinks::open)
    }
}

package ng.heynaija.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ng.heynaija.app.nav.WebLinks
import ng.heynaija.app.platform.PushEvents
import ng.heynaija.app.push.PushService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // One state for the process, not one per activity: turning the phone must not start the app again.
        val app = AppHolder.get(applicationContext)
        if (savedInstanceState == null) {
            // A fresh start (not the activity made again): every tab at its top, as the app has always opened.
            // The Gist the member is in, if any, is not a screen: it stays, with its bar.
            app.navigator.reset()
            // Started by tapping a notification or a link; not again when the activity is only recreated.
            opened(intent)
        }
        setContent { App(app) }
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

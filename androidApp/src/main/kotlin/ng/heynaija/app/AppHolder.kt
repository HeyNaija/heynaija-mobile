package ng.heynaija.app

import android.content.Context
import android.content.pm.ApplicationInfo
import ng.heynaija.app.gists.AndroidGistKeeper
import ng.heynaija.app.platform.androidPlatform
import ng.heynaija.app.push.FcmPushDevice

/**
 * The app's state, one for the whole process. Android makes the activity again
 * whenever the phone is turned, the theme or the text size changes; a state made
 * with the activity went with it, which put the app back on Home and, in a Gist,
 * left its sound playing with nothing on screen to stop it. Held here, the app
 * comes back as it was, and the Gist the member is in is still theirs to leave.
 */
object AppHolder {
    private var state: AppState? = null

    /** Called on the main thread, from the activity. */
    fun get(context: Context): AppState = state ?: run {
        val app = context.applicationContext
        val debug = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        AppState(
            androidPlatform(
                app,
                debug,
                push = { secure -> FcmPushDevice.orNull(app, secure) },
                gistKeeper = AndroidGistKeeper(app),
            ),
        ).also {
            it.start()
            state = it
        }
    }
}

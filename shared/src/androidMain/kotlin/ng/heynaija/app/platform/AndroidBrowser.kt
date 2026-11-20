package ng.heynaija.app.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Browser

/**
 * Opens a page in the browser the phone opens other websites in, never in this
 * app. The app claims every heynaija.ng link, so a plain view intent for one
 * would come straight back here; naming the browser's package skips that.
 */
class AndroidBrowser(private val context: Context) : WebBrowser {
    override fun open(url: String): Boolean {
        val browser = browserPackage() ?: return false
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(browser)
            // Says the app sent it, so the browser does not hand it back.
            .putExtra(Browser.EXTRA_APPLICATION_ID, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(view) }.isSuccess
    }

    /** The default browser; with none chosen, the first one installed. */
    private fun browserPackage(): String? {
        // A site no app claims, so only browsers answer it (the manifest's <queries> lets the app see them).
        val anySite = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/")).addCategory(Intent.CATEGORY_BROWSABLE)
        val pm = context.packageManager
        val browsers = pm.queryIntentActivities(anySite, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName }
        val default = pm.resolveActivity(anySite, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        return default?.takeIf { it in browsers } ?: browsers.firstOrNull()
    }
}

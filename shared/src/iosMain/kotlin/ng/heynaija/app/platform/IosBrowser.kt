package ng.heynaija.app.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * Safari, or whichever browser the member chose. iOS does not hand an app its own
 * Universal Link back when the app opens it, so this lands in the browser.
 */
object SafariBrowser : WebBrowser {
    override fun open(url: String): Boolean {
        val page = NSURL.URLWithString(url) ?: return false
        UIApplication.sharedApplication.openURL(page, options = emptyMap<Any?, Any>(), completionHandler = null)
        return true
    }
}

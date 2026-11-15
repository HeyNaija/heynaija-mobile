package ng.heynaija.app.games.chess

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject

/**
 * Lichess's page in iOS's sign-in sheet, as Google sign-in is (IosGoogle.kt). The
 * sheet closes itself when the website passes Lichess's answer on to
 * `ng.heynaija.app://lichess`, and the answer goes to [LichessReturn]. Closing the
 * sheet without answering changes nothing.
 */
@Composable
actual fun rememberLichessLink(): LichessLink = remember { SheetLichessLink() }

private class SheetLichessLink : LichessLink {
    // The session must outlive open(), and its presenter with it.
    private var session: ASWebAuthenticationSession? = null
    private val presenter = LichessSheetPresenter()

    override fun open(url: String) {
        val page = NSURL.URLWithString(url) ?: return
        session?.cancel()
        val started = ASWebAuthenticationSession(uRL = page, callbackURLScheme = LICHESS_RETURN_SCHEME) { back, _ ->
            session = null
            back?.absoluteString?.let(LichessReturn::deliver)
        }
        started.presentationContextProvider = presenter
        // Not the sign-in cookies of Safari: a Lichess account signed in there is offered, which is what a member expects.
        started.prefersEphemeralWebBrowserSession = false
        session = started
        if (!started.start()) session = null
    }
}

private class LichessSheetPresenter : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): UIWindow =
        UIApplication.sharedApplication.keyWindow ?: UIWindow()
}

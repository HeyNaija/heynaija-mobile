package ng.heynaija.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Sign in with Apple, on iOS (ADR 0026 in the web repository): Apple's own sheet
 * gives an ID token for the app, which Kratos checks against Apple's keys, and
 * a one-time code for the server to keep a token it can revoke with.
 */
interface AppleSignIn {
    suspend fun signIn(): SignInResult

    /** Apple's own button, as its guidelines ask: "Sign up with Apple" when [joining], otherwise "Continue with Apple". */
    @Composable
    fun Button(onClick: () -> Unit, joining: Boolean, enabled: Boolean, modifier: Modifier)
}

/** Sign in with Apple where the app offers it: iOS. Null on Android. */
@Composable
expect fun rememberAppleSignIn(): AppleSignIn?

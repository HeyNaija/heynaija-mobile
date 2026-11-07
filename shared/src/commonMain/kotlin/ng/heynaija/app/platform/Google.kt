package ng.heynaija.app.platform

import androidx.compose.runtime.Composable
import ng.heynaija.app.api.GoogleToken

/** What asking Google for an ID token came to. */
sealed interface GoogleResult {
    data class Signed(val token: GoogleToken) : GoogleResult
    /** The member closed Google's sheet: nothing to say. */
    data object Cancelled : GoogleResult
    data class Failed(val reason: String) : GoogleResult
}

/** The platform's own Google sign-in: an ID token for Kratos's Google client [clientId], with a fresh nonce. */
fun interface GoogleSignIn {
    suspend fun signIn(clientId: String): GoogleResult
}

/**
 * Google sign-in where the app offers it: Android, through Credential Manager.
 * Null on iOS, where offering Google also means offering Sign in with Apple
 * (App Store Review Guideline 4.8), which waits for the Apple developer account.
 */
@Composable
expect fun rememberGoogleSignIn(): GoogleSignIn?

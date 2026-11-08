package ng.heynaija.app.platform

import androidx.compose.runtime.Composable
import ng.heynaija.app.api.IdToken

/** What asking a platform's own sign-in (Google's, Apple's) for an ID token came to. */
sealed interface SignInResult {
    data class Signed(val token: IdToken) : SignInResult
    /** The member closed the sheet: nothing to say. */
    data object Cancelled : SignInResult
    data class Failed(val reason: String) : SignInResult
}

/** The platform's own Google sign-in: an ID token for Kratos's Google client [clientId], with a fresh nonce. */
fun interface GoogleSignIn {
    suspend fun signIn(clientId: String): SignInResult
}

/**
 * Google sign-in where the app offers it: Android, through Credential Manager.
 * Null on iOS, which offers Sign in with Apple ([rememberAppleSignIn]); Google
 * there needs an iOS client in Kratos first (ADR 0026 in the web repository).
 */
@Composable
expect fun rememberGoogleSignIn(): GoogleSignIn?

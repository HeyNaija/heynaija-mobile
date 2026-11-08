package ng.heynaija.app.platform

import androidx.compose.runtime.Composable
import ng.heynaija.app.api.IdToken
import ng.heynaija.app.api.Server

/** What asking a platform's own sign-in (Google's, Apple's) for an ID token came to. */
sealed interface SignInResult {
    data class Signed(val token: IdToken) : SignInResult
    /** The member closed the sheet: nothing to say. */
    data object Cancelled : SignInResult
    data class Failed(val reason: String) : SignInResult
}

/**
 * The platform's own Google sign-in: an ID token that [server]'s Kratos takes,
 * with a fresh nonce. Android asks for one for Kratos's web client; iOS signs in
 * as its own iOS client, which Kratos also takes.
 */
interface GoogleSignIn {
    /** Whether [server] has the Google client this platform signs in for. */
    fun offeredOn(server: Server): Boolean

    suspend fun signIn(server: Server): SignInResult
}

/**
 * Google sign-in: Credential Manager on Android, Google's page in iOS's sign-in
 * sheet on iOS (ADR 0026 in the web repository), beside Sign in with Apple.
 */
@Composable
expect fun rememberGoogleSignIn(): GoogleSignIn?

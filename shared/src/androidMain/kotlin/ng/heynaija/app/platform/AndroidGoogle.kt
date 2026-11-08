package ng.heynaija.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import ng.heynaija.app.api.IdToken

/**
 * "Sign in with Google" through Credential Manager: Google's own sheet, then an
 * ID token whose audience is Kratos's web client, so Kratos accepts it. Google
 * checks the app's package and signing certificate against an Android client in
 * the same Google Cloud project (docs/deployment/google-sign-in.md in the web
 * repository); without one it answers with an error, not a token.
 */
@Composable
actual fun rememberGoogleSignIn(): GoogleSignIn? {
    val context = LocalContext.current
    return remember(context) {
        GoogleSignIn { clientId ->
            val nonce = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(ByteArray(24).also { SecureRandom().nextBytes(it) })
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).setNonce(nonce).build())
                .build()
            try {
                val credential = CredentialManager.create(context).getCredential(context, request).credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    SignInResult.Signed(IdToken("google", GoogleIdTokenCredential.createFrom(credential.data).idToken, nonce))
                } else {
                    SignInResult.Failed("Google did not sign you in. Try again, or use your email.")
                }
            } catch (e: GetCredentialCancellationException) {
                SignInResult.Cancelled
            } catch (e: NoCredentialException) {
                SignInResult.Failed("There is no Google account on this phone. Add one in Android's settings, or use your email.")
            } catch (e: GetCredentialException) {
                println("HeyNaija: Google sign-in failed: ${e.type} ${e.message}")
                SignInResult.Failed("Google sign-in did not work. Try again, or use your email.")
            }
        }
    }
}

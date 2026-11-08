@file:OptIn(ExperimentalForeignApi::class)

package ng.heynaija.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.parameters
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import ng.heynaija.app.api.HeyNaijaApi
import ng.heynaija.app.api.IdToken
import ng.heynaija.app.api.Server
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorCodeCanceledLogin
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSURL
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject

@Composable
actual fun rememberGoogleSignIn(): GoogleSignIn? = remember { WebGoogleSignIn() }

/**
 * Google on iOS (ADR 0026 in the web repository), with no Google SDK: Google's own
 * page in iOS's sign-in sheet, as the iOS OAuth client [Server.googleIosClientId],
 * with PKCE and a nonce. Google sends the sheet back to the client's reversed ID
 * with a one-time code, which this trades for an ID token. The token is for the
 * iOS client, which Kratos's Google provider also takes; nothing secret is involved.
 */
private class WebGoogleSignIn : GoogleSignIn {
    // The session must outlive start(), and its presenter with it.
    private var session: ASWebAuthenticationSession? = null
    private val presenter = SheetPresenter()

    override fun offeredOn(server: Server): Boolean = server.googleIosClientId != null

    override suspend fun signIn(server: Server): SignInResult {
        val clientId = server.googleIosClientId ?: return SignInResult.Failed("Google sign-in is not available here. Use your email.")
        // Google's iOS clients take their own ID, reversed, as the address to come back to.
        val scheme = "com.googleusercontent.apps." + clientId.removeSuffix(".apps.googleusercontent.com")
        val redirect = "$scheme:/oauth2redirect"
        val verifier = randomText(32)
        val nonce = randomText(24)
        val state = randomText(16)
        val page = URLBuilder("https://accounts.google.com/o/oauth2/v2/auth").apply {
            parameters.append("client_id", clientId)
            parameters.append("redirect_uri", redirect)
            parameters.append("response_type", "code")
            parameters.append("scope", "openid email")
            parameters.append("code_challenge", sha256(verifier))
            parameters.append("code_challenge_method", "S256")
            parameters.append("nonce", nonce)
            parameters.append("state", state)
            parameters.append("prompt", "select_account")
        }.buildString()

        val back = when (val answer = sheet(page, scheme)) {
            is Sheet.Back -> Url(answer.url)
            Sheet.Closed -> return SignInResult.Cancelled
            is Sheet.Failed -> {
                println("HeyNaija: Google's sign-in sheet failed: ${answer.reason}")
                return SignInResult.Failed("Google sign-in did not work. Try again, or use your email.")
            }
        }
        val code = back.parameters["code"]
        if (back.parameters["state"] != state || code == null) {
            // Google says why in `error`; access_denied is the member saying no.
            return if (back.parameters["error"] == "access_denied") SignInResult.Cancelled
            else SignInResult.Failed("Google did not sign you in. Try again, or use your email.")
        }

        return try {
            val idToken = HttpClient().use { client ->
                val answer = client.submitForm(
                    "https://oauth2.googleapis.com/token",
                    parameters {
                        append("client_id", clientId)
                        append("code", code)
                        append("code_verifier", verifier)
                        append("redirect_uri", redirect)
                        append("grant_type", "authorization_code")
                    },
                )
                (HeyNaijaApi.json.parseToJsonElement(answer.bodyAsText()).jsonObject["id_token"] as? JsonPrimitive)?.contentOrNull
            }
            if (idToken != null) SignInResult.Signed(IdToken("google", idToken, nonce))
            else SignInResult.Failed("Google did not sign you in. Try again, or use your email.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SignInResult.Failed("Something went wrong. Check your connection and try again.")
        }
    }

    private sealed interface Sheet {
        data class Back(val url: String) : Sheet
        data object Closed : Sheet
        data class Failed(val reason: String) : Sheet
    }

    private suspend fun sheet(page: String, scheme: String): Sheet = suspendCancellableCoroutine { continuation ->
        val url = NSURL.URLWithString(page)
        if (url == null) {
            continuation.resume(Sheet.Failed("not a URL"))
            return@suspendCancellableCoroutine
        }
        val started = ASWebAuthenticationSession(uRL = url, callbackURLScheme = scheme) { back, error ->
            session = null
            val answer = when {
                back != null -> Sheet.Back(back.absoluteString ?: "")
                error?.code == ASWebAuthenticationSessionErrorCodeCanceledLogin -> Sheet.Closed
                else -> Sheet.Failed(error?.localizedDescription ?: "no answer")
            }
            if (continuation.isActive) continuation.resume(answer)
        }
        started.presentationContextProvider = presenter
        session = started
        continuation.invokeOnCancellation { started.cancel() }
        if (!started.start()) {
            session = null
            if (continuation.isActive) continuation.resume(Sheet.Failed("the sheet did not open"))
        }
    }
}

/** The window the sign-in sheet opens over. */
private class SheetPresenter : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): UIWindow =
        UIApplication.sharedApplication.keyWindow ?: UIWindow()
}

private val base64url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

/** [bytes] random bytes in base64url. */
private fun randomText(bytes: Int): String {
    val random = ByteArray(bytes)
    random.usePinned { SecRandomCopyBytes(kSecRandomDefault, random.size.toULong(), it.addressOf(0)) }
    return base64url.encode(random)
}

/** PKCE's S256 challenge: SHA-256 of the verifier, in base64url. */
private fun sha256(text: String): String {
    val input = text.encodeToByteArray()
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
    input.usePinned { inPinned ->
        digest.usePinned { outPinned -> CC_SHA256(inPinned.addressOf(0), input.size.convert(), outPinned.addressOf(0)) }
    }
    return base64url.encode(digest.asByteArray())
}

@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package ng.heynaija.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.UIKitView
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import ng.heynaija.app.api.IdToken
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDButton
import platform.AuthenticationServices.ASAuthorizationAppleIDButtonStyle
import platform.AuthenticationServices.ASAuthorizationAppleIDButtonTypeContinue
import platform.AuthenticationServices.ASAuthorizationAppleIDButtonTypeSignUp
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationErrorCanceled
import platform.AuthenticationServices.ASAuthorizationErrorUnknown
import platform.AuthenticationServices.ASAuthorizationScopeEmail
import platform.Foundation.NSError
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.UIKit.UIApplication
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import platform.objc.sel_registerName

@Composable
actual fun rememberAppleSignIn(): AppleSignIn? = remember { IosAppleSignIn() }

/**
 * Sign in with Apple through AuthenticationServices: Apple's sheet, then an
 * identity token for the app's bundle ID, which Kratos checks against Apple's
 * keys, and a one-time code for the server (ADR 0026 in the web repository). The
 * nonce goes to Apple as it is, so the token carries the one Kratos is sent;
 * Kratos compares them as they are.
 */
private class IosAppleSignIn : AppleSignIn {
    // AuthenticationServices holds its delegate weakly: this keeps it until Apple answers.
    private var waiting: AppleDelegate? = null

    override suspend fun signIn(): SignInResult = suspendCancellableCoroutine { continuation ->
        val nonce = nonce()
        val request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = listOf(ASAuthorizationScopeEmail)
        request.nonce = nonce
        val delegate = AppleDelegate(nonce) { result ->
            waiting = null
            if (continuation.isActive) continuation.resume(result)
        }
        waiting = delegate
        val controller = ASAuthorizationController(authorizationRequests = listOf(request))
        controller.delegate = delegate
        controller.presentationContextProvider = delegate
        controller.performRequests()
    }

    @Composable
    override fun Button(onClick: () -> Unit, joining: Boolean, enabled: Boolean, modifier: Modifier) {
        val latest = rememberUpdatedState(onClick)
        val tap = remember { ButtonTap { latest.value() } }
        val dark = HeyNaija.colors.dark
        // The words and colours are fixed when the button is made: a new one for a change.
        key(joining, dark) {
            UIKitView(
                factory = {
                    ASAuthorizationAppleIDButton(
                        authorizationButtonType = if (joining) ASAuthorizationAppleIDButtonTypeSignUp else ASAuthorizationAppleIDButtonTypeContinue,
                        authorizationButtonStyle = if (dark) ASAuthorizationAppleIDButtonStyle.ASAuthorizationAppleIDButtonStyleWhite else ASAuthorizationAppleIDButtonStyle.ASAuthorizationAppleIDButtonStyleBlack,
                    ).apply {
                        // Radii.control, as HeyNaija's other buttons.
                        cornerRadius = 8.0
                        addTarget(tap, sel_registerName("tapped"), UIControlEventTouchUpInside)
                    }
                },
                // Compose leaves a hole for the button; clipped to its corners, the page shows round them.
                modifier = modifier.clip(Radii.control),
                update = { button ->
                    button.enabled = enabled
                    button.alpha = if (enabled) 1.0 else 0.5
                },
            )
        }
    }
}

/** The button's tap, as UIKit's target and action. */
private class ButtonTap(private val onTap: () -> Unit) : NSObject() {
    @ObjCAction
    fun tapped() = onTap()
}

private class AppleDelegate(private val nonce: String, private val done: (SignInResult) -> Unit) :
    NSObject(), ASAuthorizationControllerDelegateProtocol, ASAuthorizationControllerPresentationContextProvidingProtocol {

    @ObjCSignatureOverride
    override fun authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization: ASAuthorization) {
        val credential = didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential
        val token = credential?.identityToken?.toByteArray()?.decodeToString()
        if (token == null) {
            done(SignInResult.Failed("Apple did not sign you in. Try again, or use your email."))
            return
        }
        done(SignInResult.Signed(IdToken("apple", token, nonce, appleCode = credential.authorizationCode?.toByteArray()?.decodeToString())))
    }

    @ObjCSignatureOverride
    override fun authorizationController(controller: ASAuthorizationController, didCompleteWithError: NSError) {
        done(
            when (didCompleteWithError.code) {
                ASAuthorizationErrorCanceled -> SignInResult.Cancelled
                // What iOS says when no Apple Account is signed in on the phone.
                ASAuthorizationErrorUnknown -> SignInResult.Failed("Sign in to your Apple Account in the phone’s Settings to continue with Apple, or use your email.")
                else -> {
                    println("HeyNaija: Sign in with Apple failed: ${didCompleteWithError.code} ${didCompleteWithError.localizedDescription}")
                    SignInResult.Failed("Sign in with Apple did not work. Try again, or use your email.")
                }
            },
        )
    }

    override fun presentationAnchorForAuthorizationController(controller: ASAuthorizationController): UIWindow =
        UIApplication.sharedApplication.keyWindow ?: UIWindow()
}

/** 24 random bytes in base64url, a fresh one for each sign-in. */
private fun nonce(): String {
    val bytes = ByteArray(24)
    bytes.usePinned { SecRandomCopyBytes(kSecRandomDefault, bytes.size.toULong(), it.addressOf(0)) }
    return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)
}

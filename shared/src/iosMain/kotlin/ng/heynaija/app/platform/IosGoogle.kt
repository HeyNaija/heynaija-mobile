package ng.heynaija.app.platform

import androidx.compose.runtime.Composable

/** Not on iOS until Sign in with Apple is offered beside it (App Store Review Guideline 4.8). */
@Composable
actual fun rememberGoogleSignIn(): GoogleSignIn? = null

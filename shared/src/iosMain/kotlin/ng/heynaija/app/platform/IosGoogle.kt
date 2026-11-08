package ng.heynaija.app.platform

import androidx.compose.runtime.Composable

/** Not on iOS yet: Kratos needs an iOS client for Google first (ADR 0026 in the web repository). Apple is offered instead. */
@Composable
actual fun rememberGoogleSignIn(): GoogleSignIn? = null

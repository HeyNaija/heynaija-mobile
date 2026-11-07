package ng.heynaija.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** iOS has no push yet (it needs the Apple developer account), so nothing offers it there. */
@Composable
actual fun rememberNotificationPermission(onResult: (allowed: Boolean) -> Unit): NotificationPermission =
    remember { NotificationPermission(ask = { onResult(false) }, openSettings = {}) }

package ng.heynaija.app.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ng.heynaija.app.AppState
import ng.heynaija.app.platform.NotificationPermission
import ng.heynaija.app.platform.rememberNotificationPermission
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.ui.Card
import ng.heynaija.app.ui.PrimaryButton
import ng.heynaija.app.ui.SecondaryButton

/**
 * Turning push on here: asks the system first where it asks, then registers.
 * [blocked] is true once the system said no, when only its settings can allow them.
 */
class PushSwitch(val turnOn: () -> Unit, val turnOff: () -> Unit, val blocked: Boolean, val permission: NotificationPermission)

@Composable
fun rememberPushSwitch(app: AppState): PushSwitch {
    var blocked by remember { mutableStateOf(false) }
    val permission = rememberNotificationPermission { allowed ->
        blocked = !allowed
        app.scope.launch { app.setPushOff(!allowed) }
    }
    return PushSwitch(
        turnOn = {
            blocked = false
            app.scope.launch { app.setPushOff(false) }
            permission.ask()
        },
        turnOff = { app.scope.launch { app.setPushOff(true) } },
        blocked = blocked,
        permission = permission,
    )
}

/**
 * Offered once at the top of Notifications, as the web offers it once on the
 * home page (ADR 0011): never asked for before the member wants it.
 */
@Composable
fun PushOffer(app: AppState) {
    val switch = rememberPushSwitch(app)
    val colors = HeyNaija.colors
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Get notified on this phone", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = colors.ink)
            Text(
                if (switch.blocked) "Notifications are off for HeyNaija in your phone’s settings. Allow them there to see replies, mentions and follows as they happen."
                else "See replies, mentions and follows as they happen. What reaches you here is not emailed as well.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (switch.blocked) PrimaryButton("Open settings", switch.permission.openSettings)
                else PrimaryButton("Turn on", switch.turnOn)
                SecondaryButton("Not now", app::dismissPushOffer)
            }
        }
    }
}

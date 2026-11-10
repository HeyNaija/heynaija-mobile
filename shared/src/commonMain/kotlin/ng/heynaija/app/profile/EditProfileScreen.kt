package ng.heynaija.app.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ng.heynaija.app.LocalApp
import ng.heynaija.app.launchCatching
import ng.heynaija.app.media.uploadAvatar
import ng.heynaija.app.platform.rememberImagePicker
import ng.heynaija.app.reasonOf
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.ui.Avatar
import ng.heynaija.app.ui.Field
import ng.heynaija.app.ui.PrimaryButton
import ng.heynaija.app.ui.SecondaryButton
import ng.heynaija.app.ui.TopBar

private const val NAME_MAX = 50
private const val BIO_MAX = 160

/** The member's name, bio and photo. The handle changes on the web, where Kratos asks for it. */
@Composable
fun EditProfileScreen() {
    val app = LocalApp.current
    val me = app.me ?: return
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(me.displayName ?: "") }
    var bio by remember { mutableStateOf(me.bio) }
    var saving by remember { mutableStateOf(false) }
    var photoBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val pick = rememberImagePicker { images ->
        val image = images.firstOrNull() ?: return@rememberImagePicker
        photoBusy = true
        scope.launchCatching(app, "The photo could not be uploaded. Try again.") {
            try {
                val path = uploadAvatar(app.api, image)
                app.updateMe { it.copy(avatar = path) }
                app.say("Profile photo updated")
            } finally {
                photoBusy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar("Edit profile", onBack = { app.navigator.pop() })
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Avatar(me.handle, app.me?.avatar, 72.dp)
                    if (photoBusy) CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 2.dp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Change photo", { pick(1) }, enabled = !photoBusy)
                    if (app.me?.avatar != null) {
                        SecondaryButton("Remove photo", {
                            scope.launchCatching(app, "The photo could not be removed.") {
                                app.api.removeAvatar()
                                app.updateMe { it.copy(avatar = null) }
                            }
                        }, enabled = !photoBusy, danger = true)
                    }
                }
            }
            Field(
                name,
                { if (it.length <= NAME_MAX) name = it },
                label = "Name",
                placeholder = "How your name is shown",
            )
            Field(
                bio,
                { if (it.length <= BIO_MAX) bio = it },
                label = "Bio",
                placeholder = "A line or two about you",
                singleLine = false,
                minLines = 3,
            )
            Text("${bio.length} / $BIO_MAX", style = MaterialTheme.typography.labelSmall, color = HeyNaija.colors.muted)
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = HeyNaija.colors.danger) }
            PrimaryButton("Save", {
                saving = true
                error = null
                scope.launch {
                    try {
                        app.api.updateProfile(name.trim(), bio.trim())
                        // Shown at once; /v1/me then gives the name and bio as the server keeps them.
                        app.updateMe { it.copy(displayName = name.trim().ifEmpty { null }, bio = bio.trim()) }
                        app.refreshMe()
                        app.say("Profile saved")
                        app.navigator.pop()
                    } catch (e: Exception) {
                        error = reasonOf(e) ?: "Your profile could not be saved. Try again."
                    } finally {
                        saving = false
                    }
                }
            }, Modifier.padding(top = 4.dp), busy = saving)
        }
    }
}

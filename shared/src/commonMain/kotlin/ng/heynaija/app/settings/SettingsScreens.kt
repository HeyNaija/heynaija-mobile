package ng.heynaija.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlinx.coroutines.launch
import ng.heynaija.app.LocalApp
import ng.heynaija.app.api.ApiException
import ng.heynaija.app.api.AuthStep
import ng.heynaija.app.api.Deletion
import ng.heynaija.app.api.NOTIFICATION_KINDS
import ng.heynaija.app.api.Server
import ng.heynaija.app.api.Servers
import ng.heynaija.app.nav.Route
import ng.heynaija.app.posts.PostList
import ng.heynaija.app.posts.PostListViewModel
import ng.heynaija.app.reasonOf
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.ui.Card
import ng.heynaija.app.ui.Confirm
import ng.heynaija.app.ui.Field
import ng.heynaija.app.notifications.rememberPushSwitch
import ng.heynaija.app.ui.Icons
import ng.heynaija.app.ui.Loading
import ng.heynaija.app.ui.MenuRow
import ng.heynaija.app.ui.PrimaryButton
import ng.heynaija.app.ui.SecondaryButton
import ng.heynaija.app.ui.SectionTitle
import ng.heynaija.app.ui.TopBar
import ng.heynaija.app.ui.postedAt

/** A card of rows with hairlines between them. */
@Composable
private fun Group(rows: List<@Composable () -> Unit>) {
    Card {
        Column {
            rows.forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider(color = HeyNaija.colors.line)
                row()
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    val app = LocalApp.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var signingOut by remember { mutableStateOf(false) }
    val web = Servers.current.web
    val open = { path: String -> { runCatching { uri.openUri("$web$path") }; Unit } }

    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", onBack = { app.navigator.pop() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (app.me != null) {
                SectionTitle("YOUR ACCOUNT")
                Group(listOf(
                    { MenuRow("Edit profile", Icons.User) { app.navigator.push(Route.EditProfile) } },
                    { MenuRow("Bookmarks", Icons.Bookmark) { app.navigator.push(Route.Bookmarks) } },
                    { MenuRow("Notifications", Icons.Bell) { app.navigator.push(Route.NotificationSettings) } },
                    { MenuRow("Email, handle and sign-in", Icons.Mail, detail = "On the web, for now") { open("/settings")() } },
                ))
            }
            SectionTitle("HEYNAIJA")
            Group(listOf(
                { MenuRow("Community rules", Icons.Shield, onClick = open("/rules")) },
                { MenuRow("Privacy notice", Icons.Info, onClick = open("/privacy")) },
                { MenuRow("About HeyNaija", Icons.Globe, onClick = open("/about")) },
                { MenuRow("Licences", Icons.Award, onClick = open("/licence")) },
            ))
            if (app.platform.debug) {
                SectionTitle("SERVER (DEBUG BUILDS ONLY)")
                Group(Server.all.map { server ->
                    {
                        MenuRow(server.label, detail = server.api, trailing = {
                            RadioButton(server == Servers.current, { app.switchServer(server) }, colors = RadioButtonDefaults.colors(selectedColor = HeyNaija.colors.accent))
                        }) { app.switchServer(server) }
                    }
                })
            }
            if (app.me != null) {
                Spacer(Modifier.height(6.dp))
                Group(listOf(
                    { MenuRow(if (signingOut) "Signing out..." else "Sign out", Icons.Leave) { if (!signingOut) { signingOut = true; scope.launch { app.signOut() } } } },
                    { MenuRow("Delete your account", danger = true) { app.navigator.push(Route.DeleteAccount) } },
                ))
            }
            Text(
                "HeyNaija for ${if (app.platform.debug) "testing" else "phones"}. Free software under the AGPL.",
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.labelSmall,
                color = HeyNaija.colors.faint,
            )
        }
    }
}

/** Which activity notifies the member, and whether it is emailed too. */
@Composable
fun NotificationSettingsScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var prefs by remember { mutableStateOf<Map<String, Boolean>?>(null) }
    var failed by remember { mutableStateOf(false) }
    val push by app.push.collectAsStateWithLifecycle()
    val switch = rememberPushSwitch(app)
    LaunchedEffect(Unit) {
        try {
            prefs = app.api.notificationPreferences()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        }
    }
    fun set(key: String, on: Boolean) {
        val before = prefs
        prefs = before?.plus(key to on)
        scope.launch {
            try {
                prefs = app.api.setNotificationPreference(key, on)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                prefs = before
                app.say(reasonOf(e) ?: "That did not save. Try again.")
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("Notifications", onBack = { app.navigator.pop() })
        val current = prefs
        when {
            current == null && failed -> Text("Your settings could not be loaded.", Modifier.padding(20.dp), color = HeyNaija.colors.muted)
            current == null -> Loading()
            else -> Column(Modifier.verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionTitle("NOTIFY ME ABOUT")
                Group(NOTIFICATION_KINDS.map { (key, label) ->
                    { Toggle(label, current[key] ?: true) { set(key, it) } }
                })
                SectionTitle("EMAIL")
                Group(listOf {
                    Toggle("Email me what I missed", current["email"] ?: false, detail = "At most one email every half hour, only for what you have not seen in the app.") { set("email", it) }
                })
                if (push.available) {
                    SectionTitle("ON THIS PHONE")
                    Group(buildList {
                        add {
                            Toggle(
                                "Push notifications",
                                push.on,
                                detail = when {
                                    push.on -> "Shown here as they happen, and not emailed as well."
                                    switch.blocked -> "Notifications are off for HeyNaija in your phone’s settings."
                                    else -> "Off. Your notifications wait in the app, and email if you chose it."
                                },
                            ) { if (it) switch.turnOn() else switch.turnOff() }
                        }
                        if (switch.blocked) add { MenuRow("Open your phone’s settings", Icons.Gear) { switch.permission.openSettings() } }
                    })
                } else {
                    Text(
                        "Notifications on your phone are not available in this version of the app. The count on the bell shows what is new.",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = HeyNaija.colors.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, detail: String? = null, onChange: (Boolean) -> Unit) {
    val colors = HeyNaija.colors
    MenuRow(label, detail = detail, trailing = {
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.accentInk))
    }) { onChange(!on) }
}

@Composable
fun BookmarksScreen() {
    val app = LocalApp.current
    val viewModel = viewModel(key = "bookmarks") {
        PostListViewModel(load = { cursor -> app.api.bookmarks(cursor) }, changes = app.changes, keep = { it.viewerBookmarked })
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("Bookmarks", onBack = { app.navigator.pop() })
        PostList(viewModel, empty = "Posts you bookmark are kept here, for you only.")
    }
}

/**
 * Asking for the account to be deleted (docs/adr/0016 in the web repository):
 * the member types their handle, and must have signed in within the last few
 * minutes, so the app asks for an emailed code first when the API says so. The
 * account goes after a grace period, during which this screen can call it off.
 */
@Composable
fun DeleteAccountScreen() {
    val app = LocalApp.current
    val me = app.me ?: return
    val scope = rememberCoroutineScope()
    val colors = HeyNaija.colors
    var deletion by remember { mutableStateOf<Deletion?>(null) }
    var handle by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    // Signing in again: null until the API asks for it.
    var reauth by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { deletion = runCatching { app.api.deletion() }.getOrNull() }

    fun request() {
        busy = true
        error = null
        scope.launch {
            try {
                deletion = app.api.requestDeletion(handle)
                reauth = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.code == "reauth_required") {
                    reauth = app.kratos.startLogin(app.currentToken)
                    codeSent = false
                } else error = reasonOf(e) ?: "That did not go through. Try again."
            } catch (e: Exception) {
                error = "That did not go through. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar("Delete your account", onBack = { app.navigator.pop() })
        val current = deletion
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when {
                current == null -> Loading(Modifier.height(200.dp))
                current.state == "scheduled" -> {
                    val day = current.scheduledFor?.let { postedAt(Instant.parse(it)).substringAfter(" · ") } ?: "soon"
                    Text("Your account will be deleted on $day.", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                    Text("Until then you can change your mind. After that day it cannot be undone.", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                    PrimaryButton("Keep my account", {
                        busy = true
                        scope.launch {
                            try {
                                app.api.cancelDeletion()
                                deletion = app.api.deletion()
                                app.say("Your account will not be deleted.")
                            } catch (e: Exception) {
                                error = reasonOf(e) ?: "That did not go through. Try again."
                            } finally {
                                busy = false
                            }
                        }
                    }, busy = busy)
                }
                reauth != null -> {
                    Text("First, confirm it is you", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                    Text("Enter the email you sign in with, and we will send you a code.", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                    Field(email, { email = it }, "Email", keyboardType = KeyboardType.Email, enabled = !codeSent)
                    if (codeSent) Field(code, { code = it.filter(Char::isDigit).take(6) }, "Code", keyboardType = KeyboardType.NumberPassword)
                    error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.danger) }
                    PrimaryButton(if (codeSent) "Confirm" else "Email me a code", {
                        val flow = reauth ?: return@PrimaryButton
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                val step = if (!codeSent) app.kratos.sendLoginCode(flow, email.trim(), app.currentToken)
                                else app.kratos.finishLogin(flow, email.trim(), code.trim(), app.currentToken)
                                when (step) {
                                    AuthStep.Sent -> if (!codeSent) codeSent = true else error = "That code is not right."
                                    is AuthStep.Done -> { busy = false; request() }
                                    is AuthStep.Refused -> error = step.messages.values.firstOrNull() ?: "That did not work."
                                    AuthStep.Expired -> { reauth = app.kratos.startLogin(app.currentToken); codeSent = false; error = "That took too long. Try again." }
                                    // Only Google's sign-in answers these.
                                    is AuthStep.ConfirmEmail, is AuthStep.NoAccount -> error = "That did not work."
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = "That did not go through. Check your connection and try again."
                            } finally {
                                busy = false
                            }
                        }
                    }, Modifier.fillMaxWidth(), enabled = email.contains('@') && (!codeSent || code.length == 6), busy = busy)
                }
                else -> {
                    Text("This deletes your posts, replies, likes and profile.", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                    Text(
                        "Your account is kept for ${current.graceDays} days first, in case you change your mind. You can download your data on the web before you go.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                    Field(handle, { handle = it }, "Type your handle, @${me.handle}, to confirm", placeholder = me.handle)
                    error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.danger) }
                    SecondaryButton("Delete my account", { confirm = true }, Modifier.fillMaxWidth(), enabled = handle.trim().removePrefix("@").equals(me.handle, ignoreCase = true) && !busy, danger = true)
                }
            }
        }
    }
    if (confirm) {
        Confirm(
            title = "Delete your account?",
            text = "It goes in ${deletion?.graceDays ?: 14} days. You can cancel until then.",
            action = "Delete",
            onConfirm = { confirm = false; request() },
            onDismiss = { confirm = false },
        )
    }
}

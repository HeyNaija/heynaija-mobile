package ng.heynaija.app.auth

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ng.heynaija.app.LocalApp
import ng.heynaija.app.api.AuthStep
import ng.heynaija.app.api.Servers
import ng.heynaija.app.api.SignupExtras
import ng.heynaija.app.nav.Route
import ng.heynaija.app.reasonOf
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii
import ng.heynaija.app.ui.Field
import ng.heynaija.app.ui.Icons
import ng.heynaija.app.ui.PrimaryButton
import ng.heynaija.app.ui.SecondaryButton
import ng.heynaija.app.ui.TopBar

private const val CONNECTION = "Something went wrong. Check your connection and try again."

/** A form screen: the close button, a heading, a line under it, and the fields. */
@Composable
private fun FormPage(title: String, lead: String, onClose: (() -> Unit)?, content: @Composable () -> Unit) {
    val colors = HeyNaija.colors
    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(null, onBack = onClose, backIcon = Icons.Close)
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = colors.ink)
            Text(lead, style = MaterialTheme.typography.bodyLarge, color = colors.muted)
            content()
        }
    }
}

@Composable
private fun FormError(message: String?) {
    if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium, color = HeyNaija.colors.danger)
}

private fun AuthStep.Refused.first(): String = messages[""] ?: messages.values.firstOrNull() ?: CONNECTION

/** Signing in with a code emailed to the member: no passwords (docs/adr/0003 in the web repository). */
@Composable
fun SignInScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var flow by rememberSaveable { mutableStateOf<String?>(null) }
    var sent by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun send() {
        busy = true
        error = null
        scope.launch {
            try {
                val id = app.kratos.startLogin()
                flow = id
                when (val step = app.kratos.sendLoginCode(id, email.trim())) {
                    AuthStep.Sent -> sent = true
                    is AuthStep.Refused -> error = step.messages["identifier"] ?: step.first()
                    AuthStep.Expired -> error = "That took too long. Try again."
                    is AuthStep.Done -> app.signedIn(step.token)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = CONNECTION
            } finally {
                busy = false
            }
        }
    }

    fun finish() {
        val id = flow ?: return
        busy = true
        error = null
        scope.launch {
            try {
                when (val step = app.kratos.finishLogin(id, email.trim(), code.trim())) {
                    is AuthStep.Done -> app.signedIn(step.token)
                    is AuthStep.Refused -> error = step.messages["code"] ?: step.first()
                    AuthStep.Expired -> {
                        sent = false
                        code = ""
                        error = "That code has expired. Send yourself a new one."
                    }
                    AuthStep.Sent -> error = "That code is not right. Check the email and try again."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = CONNECTION
            } finally {
                busy = false
            }
        }
    }

    if (!sent) {
        FormPage("Sign in", "We will email you a code. No password needed.", onClose = { app.navigator.pop() }) {
            Field(email, { email = it }, "Email", placeholder = "you@example.com", keyboardType = KeyboardType.Email, onDone = { if (email.contains('@')) send() })
            FormError(error)
            PrimaryButton("Email me a code", ::send, Modifier.fillMaxWidth(), enabled = email.contains('@'), busy = busy)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("New here? ", style = MaterialTheme.typography.bodyMedium, color = HeyNaija.colors.muted)
                Text(
                    "Join with an invite",
                    Modifier.clickable { app.navigator.replace(Route.SignUp) },
                    style = MaterialTheme.typography.labelLarge,
                    color = HeyNaija.colors.accentStrong,
                )
            }
            ServerNote()
        }
    } else {
        FormPage("Check your email", "We sent a code to ${email.trim()}. It works for 15 minutes.", onClose = { app.navigator.pop() }) {
            Field(code, { code = it.filter(Char::isDigit).take(6) }, "Code", placeholder = "6 digits", keyboardType = KeyboardType.NumberPassword, onDone = { if (code.length == 6) finish() })
            FormError(error)
            PrimaryButton("Sign in", ::finish, Modifier.fillMaxWidth(), enabled = code.length == 6, busy = busy)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Send a new code", { code = ""; send() })
                SecondaryButton("Use another email", { sent = false; code = ""; error = null })
            }
        }
    }
}

/** Which server a debug build is pointed at, so a tester is never confused about where they are signing in. */
@Composable
private fun ServerNote() {
    val app = LocalApp.current
    if (!app.platform.debug) return
    Text("Server: ${Servers.current.label}. Change it in Settings.", style = MaterialTheme.typography.labelSmall, color = HeyNaija.colors.faint)
}

private enum class JoinStep { Invite, Age, Account, Code }

/**
 * Joining, in the web's order (/join, then /signup): the invite, the date of
 * birth, then email and handle, then the emailed code. The invite and date go
 * with the signup as its transient payload, and the API decides again when the
 * account is made (ADR 0022 in the web repository).
 */
@Composable
fun SignUpScreen() {
    val app = LocalApp.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(JoinStep.Invite) }
    var invite by rememberSaveable { mutableStateOf("") }
    var birth by rememberSaveable(stateSaver = BirthDate.Saver) { mutableStateOf(BirthDate()) }
    var confirmed by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var handle by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var flow by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var fieldErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val extras = { SignupExtras(invite.trim(), birth.iso() ?: "") }

    fun run(block: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = reasonOf(e) ?: CONNECTION
            } finally {
                busy = false
            }
        }
    }

    fun sendCode() = run {
        fieldErrors = emptyMap()
        val id = app.kratos.startSignup()
        flow = id
        when (val answer = app.kratos.sendSignupCode(id, email.trim(), handle.trim().lowercase(), extras())) {
            AuthStep.Sent -> { step = JoinStep.Code; code = "" }
            is AuthStep.Refused -> {
                fieldErrors = answer.messages
                error = answer.messages[""] ?: answer.messages.filterKeys { it !in setOf("traits.email", "traits.handle") }.values.firstOrNull()
            }
            AuthStep.Expired -> error = "That took too long. Try again."
            is AuthStep.Done -> app.signedIn(answer.token)
        }
    }

    val close = { app.navigator.pop(); Unit }
    when (step) {
        JoinStep.Invite -> FormPage("Join HeyNaija", "HeyNaija is open by invite while it grows. Enter the code someone sent you.", close) {
            Field(invite, { invite = it.uppercase() }, "Invite code", placeholder = "HN-XXXX-XXXX-XXXX", onDone = { })
            FormError(error)
            PrimaryButton("Continue", {
                run {
                    if (app.api.inviteUsable(invite.trim())) step = JoinStep.Age
                    else error = "That invite code is not valid. Check it, or ask whoever invited you for a new one."
                }
            }, Modifier.fillMaxWidth(), enabled = invite.isNotBlank(), busy = busy)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Already a member? ", style = MaterialTheme.typography.bodyMedium, color = HeyNaija.colors.muted)
                Text("Sign in", Modifier.clickable { app.navigator.replace(Route.SignIn) }, style = MaterialTheme.typography.labelLarge, color = HeyNaija.colors.accentStrong)
            }
        }
        JoinStep.Age -> FormPage("Your date of birth", "HeyNaija is for adults while it is in beta.", { step = JoinStep.Invite }) {
            BirthDateFields(birth, { birth = it }, confirmed, { confirmed = it })
            FormError(error)
            PrimaryButton("Continue", {
                val date = birth.iso() ?: return@PrimaryButton
                run {
                    val (outcome, joinAge) = app.api.checkAge(date)
                    when (outcome) {
                        "allowed" -> step = JoinStep.Account
                        "too_young" -> error = "Sorry, you cannot join HeyNaija yet: it is open to people aged $joinAge and over."
                        else -> error = "That is not a real date. Check the day, month and year."
                    }
                }
            }, Modifier.fillMaxWidth(), enabled = birth.iso() != null && confirmed, busy = busy)
        }
        JoinStep.Account -> FormPage("Make your account", "We will email you a code to finish. No password needed.", { step = JoinStep.Age }) {
            Field(email, { email = it }, "Email", placeholder = "you@example.com", keyboardType = KeyboardType.Email, error = fieldErrors["traits.email"])
            Field(
                handle,
                { handle = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }.take(20) },
                "Handle",
                placeholder = "3 to 20 letters, numbers or _",
                error = fieldErrors["traits.handle"],
            )
            Text("People find you by @${handle.ifEmpty { "handle" }}. You can change it later on the web.", style = MaterialTheme.typography.labelSmall, color = HeyNaija.colors.muted)
            FormError(error)
            Text(
                "By joining you agree to the community rules, and to how we handle your data in the privacy notice.",
                style = MaterialTheme.typography.bodyMedium,
                color = HeyNaija.colors.muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Community rules", Modifier.clickable { runCatching { uri.openUri("${Servers.current.web}/rules") } }, style = MaterialTheme.typography.labelLarge, color = HeyNaija.colors.accentStrong)
                Text("Privacy notice", Modifier.clickable { runCatching { uri.openUri("${Servers.current.web}/privacy") } }, style = MaterialTheme.typography.labelLarge, color = HeyNaija.colors.accentStrong)
            }
            PrimaryButton("Email me a code", ::sendCode, Modifier.fillMaxWidth(), enabled = email.contains('@') && handle.length >= 3, busy = busy)
        }
        JoinStep.Code -> FormPage("Check your email", "We sent a code to ${email.trim()}. It works for 15 minutes.", { step = JoinStep.Account }) {
            Field(code, { code = it.filter(Char::isDigit).take(6) }, "Code", placeholder = "6 digits", keyboardType = KeyboardType.NumberPassword)
            FormError(error)
            PrimaryButton("Join HeyNaija", {
                val id = flow ?: return@PrimaryButton
                run {
                    when (val answer = app.kratos.finishSignup(id, email.trim(), handle.trim().lowercase(), extras(), code.trim())) {
                        is AuthStep.Done -> app.signedIn(answer.token)
                        is AuthStep.Refused -> error = answer.messages["code"] ?: answer.first()
                        AuthStep.Expired -> { step = JoinStep.Account; error = "That code has expired. Send yourself a new one." }
                        AuthStep.Sent -> error = "That code is not right. Check the email and try again."
                    }
                }
            }, Modifier.fillMaxWidth(), enabled = code.length == 6, busy = busy)
            SecondaryButton("Send a new code", ::sendCode)
        }
    }
}

/** A date of birth as three choices; 0 is not chosen yet. */
data class BirthDate(val day: Int = 0, val month: Int = 0, val year: Int = 0) {
    companion object {
        /** Kept as "day-month-year", so a half-chosen date survives leaving the screen. */
        val Saver = androidx.compose.runtime.saveable.Saver<BirthDate, String>(
            save = { "${it.day}-${it.month}-${it.year}" },
            restore = { saved -> saved.split('-').map { it.toInt() }.let { (d, m, y) -> BirthDate(d, m, y) } },
        )
    }

    /** YYYY-MM-DD once all three are chosen and the day fits the month. */
    fun iso(): String? {
        if (day == 0 || month == 0 || year == 0 || day > daysIn(month, year)) return null
        return "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
    }
}

private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

private fun daysIn(month: Int, year: Int): Int = when (month) {
    2 -> if (year == 0 || (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0))) 29 else 28
    4, 6, 9, 11 -> 30
    0 -> 31
    else -> 31
}

/**
 * Day, month and year from lists, as on the web (apps/web/app/ui/birth-date-fields.tsx):
 * a calendar opens on today, a long way from a birthday decades back, and the month by
 * name means 3/4 cannot be read as 4/3. Then the tick saying the date is real.
 */
@Composable
fun BirthDateFields(birth: BirthDate, onChange: (BirthDate) -> Unit, confirmed: Boolean, onConfirm: (Boolean) -> Unit) {
    val colors = HeyNaija.colors
    // Lagos's year, as on the web.
    val latest = remember { Clock.System.now().toLocalDateTime(TimeZone.of("Africa/Lagos")).year }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Date of birth", style = MaterialTheme.typography.labelLarge, color = colors.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Picker("Day", birth.day.takeIf { it > 0 }?.toString(), (1..daysIn(birth.month, birth.year)).map { it.toString() }, Modifier.weight(0.85f)) {
                onChange(birth.copy(day = it.toInt()))
            }
            Picker("Month", birth.month.takeIf { it > 0 }?.let { MONTHS[it - 1] }, MONTHS, Modifier.weight(1.4f)) {
                onChange(birth.copy(month = MONTHS.indexOf(it) + 1))
            }
            Picker("Year", birth.year.takeIf { it > 0 }?.toString(), (0..110).map { (latest - it).toString() }, Modifier.weight(1.05f)) {
                onChange(birth.copy(year = it.toInt()))
            }
        }
        Text("Only you can see it, in Settings. It is never shown on your profile.", style = MaterialTheme.typography.labelSmall, color = colors.muted)
        Row(Modifier.clickable { onConfirm(!confirmed) }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(confirmed, onConfirm, colors = CheckboxDefaults.colors(checkedColor = colors.accent, checkmarkColor = colors.accentInk))
            Text("This is my real date of birth", style = MaterialTheme.typography.bodyLarge, color = colors.ink)
        }
    }
}

/** A box that opens a list; it shows its own name ("Day") until something is chosen. */
@Composable
private fun Picker(label: String, value: String?, options: List<String>, modifier: Modifier, onPick: (String) -> Unit) {
    val colors = HeyNaija.colors
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(Radii.control)
                .border(1.dp, if (value == null) colors.lineStrong else colors.accent, Radii.control)
                .clickable(onClickLabel = label) { open = true }
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value ?: label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (value == null) colors.muted else colors.ink, maxLines = 1)
            Icon(Icons.Chevron, null, Modifier.size(16.dp), tint = colors.muted)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            for (option in options) {
                DropdownMenuItem(text = { Text(option) }, onClick = { open = false; onPick(option) })
            }
        }
    }
}

/**
 * For a member who joined before signup asked: the date of birth, before
 * anything else (ADR 0020). Under the minimum age the account waits for a
 * moderator, as on the web.
 */
@Composable
fun BirthDateScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var birth by rememberSaveable(stateSaver = BirthDate.Saver) { mutableStateOf(BirthDate()) }
    var confirmed by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    FormPage(
        "One thing before you go on",
        "HeyNaija now asks everyone for their date of birth, so the rules for younger members can apply. Members who joined before we asked give it once, here.",
        onClose = null,
    ) {
        BirthDateFields(birth, { birth = it }, confirmed, { confirmed = it })
        FormError(error)
        PrimaryButton("Save", {
            val date = birth.iso() ?: return@PrimaryButton
            busy = true
            error = null
            scope.launch {
                try {
                    when (app.api.giveBirthDate(date)) {
                        "not_a_date" -> error = "That is not a real date. Check the day, month and year."
                        "suspended" -> app.say("Thank you. Your account is paused until a moderator looks at it.")
                    }
                    app.refreshMe()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = reasonOf(e) ?: CONNECTION
                } finally {
                    busy = false
                }
            }
        }, Modifier.fillMaxWidth(), enabled = birth.iso() != null && confirmed, busy = busy)
        SecondaryButton("Sign out", { scope.launch { app.signOut() } })
    }
}

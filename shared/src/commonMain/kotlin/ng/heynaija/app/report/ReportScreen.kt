package ng.heynaija.app.report

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import ng.heynaija.app.LocalApp
import ng.heynaija.app.api.NEEDS_LABEL_REASON
import ng.heynaija.app.api.REPORT_REASONS
import ng.heynaija.app.reasonOf
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.ui.Field
import ng.heynaija.app.ui.Icons
import ng.heynaija.app.ui.PrimaryButton
import ng.heynaija.app.ui.TopBar

/** Reporting a post or a member to the moderators, with the web's reasons. */
@Composable
fun ReportScreen(target: Pair<String, String>, what: String) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val colors = HeyNaija.colors
    var reason by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // "Not marked sensitive" fits only a post, while marking is switched on (ADR 0020).
    val reasons = if (target.first == "postId" && app.me?.has("adultLabels") == true) {
        REPORT_REASONS.dropLast(1) + NEEDS_LABEL_REASON + REPORT_REASONS.last()
    } else {
        REPORT_REASONS
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar("Report", onBack = { app.navigator.pop() }, backIcon = Icons.Close)
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("What is wrong with $what?", style = MaterialTheme.typography.titleMedium, color = colors.ink)
            Text("A moderator reads every report. The person you report is not told who sent it.", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            Column {
                for ((key, label) in reasons) {
                    Row(Modifier.fillMaxWidth().clickable { reason = key }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(reason == key, { reason = key }, colors = RadioButtonDefaults.colors(selectedColor = colors.accent))
                        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.ink)
                    }
                }
            }
            Field(details, { if (it.length <= 500) details = it }, "Anything else the moderators should know (optional)", singleLine = false, minLines = 3)
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.danger) }
            PrimaryButton("Send report", {
                val chosen = reason ?: return@PrimaryButton
                busy = true
                error = null
                scope.launch {
                    try {
                        app.api.report(target, chosen, details.trim())
                        app.say("Thanks. A moderator will look at your report.")
                        app.navigator.pop()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = reasonOf(e) ?: "Your report did not go through. Try again."
                    } finally {
                        busy = false
                    }
                }
            }, Modifier.fillMaxWidth(), enabled = reason != null, busy = busy)
        }
    }
}

package ng.heynaija.app.gists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ng.heynaija.app.LocalApp
import ng.heynaija.app.nav.Route
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.ui.Card
import ng.heynaija.app.ui.PrimaryButton
import ng.heynaija.app.ui.SecondaryButton

/**
 * For someone signed out at a Gist, or a reply in one, while Gists are on: the API
 * answers 401 then, and 404 while they are off. The web's gist-sign-in page, the
 * same for every Gist, so it says nothing about this one. Signing in or joining
 * from here comes straight back to it (AppState.signedIn).
 */
@Composable
fun GistSignIn(modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("GISTS", style = MaterialTheme.typography.labelMedium, color = colors.accentStrong)
                Text("Sign in to join this Gist", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                Text(
                    "Gists are live audio conversations between HeyNaija members. Sign in and you will come straight back here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("Sign in", { app.navigator.push(Route.SignIn) }, Modifier.weight(1f))
                    SecondaryButton("Join with an invite", { app.navigator.push(Route.SignUp) }, Modifier.weight(1f))
                }
            }
        }
    }
}

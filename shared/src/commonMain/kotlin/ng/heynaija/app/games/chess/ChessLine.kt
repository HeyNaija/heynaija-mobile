package ng.heynaija.app.games.chess

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import ng.heynaija.app.LocalApp
import ng.heynaija.app.api.ChessMember
import ng.heynaija.app.nav.Route
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.ui.Icons
import ng.heynaija.app.ui.SecondaryButton

/**
 * A member's chess on their profile, for a viewer with Games: their Lichess name
 * and ratings, the game they are playing now, and Challenge. Nothing at all when
 * they have not connected Lichess, the viewer may not see them, or Games is off:
 * the API gives the same 404 for each.
 */
@Composable
fun ChessLine(handle: String, self: Boolean) {
    val app = LocalApp.current
    if (app.me?.has("games") != true) return
    val member by produceState<ChessMember?>(null, handle) {
        value = try {
            app.api.chessMember(handle)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
    val chess = member ?: return
    val colors = HeyNaija.colors
    Column {
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = colors.line)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Chess, null, Modifier.size(18.dp), tint = colors.muted)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("${chess.username} on Lichess", style = MaterialTheme.typography.bodyMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ratingsLine(chess.ratings)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!self && chess.canChallenge) {
                Spacer(Modifier.width(8.dp))
                SecondaryButton("Challenge", { app.navigator.push(Route.Chess(opponent = handle)) })
            }
        }
        chess.playing?.let { game ->
            Text(
                "Playing now · Watch",
                Modifier.padding(top = 8.dp).clickable { app.navigator.push(Route.ChessGame(game.id)) },
                style = MaterialTheme.typography.labelLarge,
                color = colors.accentStrong,
            )
        }
    }
}

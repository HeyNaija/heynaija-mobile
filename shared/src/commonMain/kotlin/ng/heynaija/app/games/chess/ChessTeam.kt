package ng.heynaija.app.games.chess

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import ng.heynaija.app.LocalApp
import ng.heynaija.app.api.ChessTeam
import ng.heynaija.app.games.competitionSection
import ng.heynaija.app.nav.Route
import ng.heynaija.app.posts.ListGutter
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.ui.Card
import ng.heynaija.app.ui.Empty
import ng.heynaija.app.ui.Icons
import ng.heynaija.app.ui.Loading
import ng.heynaija.app.ui.Problem
import ng.heynaija.app.ui.TopBar
import ng.heynaija.app.ui.plural

// A community's Lichess team and its tournaments (ADR 0028, phase 6), to read.
// Linking a team and making or cancelling tournaments are its moderators' tools and
// stay on the website.

private sealed interface TeamLoad {
    data object Loading : TeamLoad
    data object Missing : TeamLoad
    data object Failed : TeamLoad
    data class Loaded(val team: ChessTeam) : TeamLoad
}

@Composable
fun ChessTeamScreen(slug: String) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val uri = LocalUriHandler.current
    val load by produceState<TeamLoad>(TeamLoad.Loading, slug) {
        value = try {
            TeamLoad.Loaded(app.api.chessTeam(slug))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (ng.heynaija.app.games.notAvailable(e)) TeamLoad.Missing else TeamLoad.Failed
        }
    }
    val title = (load as? TeamLoad.Loaded)?.team?.community?.name?.let { "Chess for $it" } ?: "Chess"
    Column(Modifier.fillMaxSize()) {
        TopBar(title, onBack = { app.navigator.pop() })
        when (val l = load) {
            TeamLoad.Loading -> Loading()
            TeamLoad.Missing -> Problem("There is no chess here for this community.", null)
            TeamLoad.Failed -> Problem("This page could not be loaded. Check your connection and try again.", null)
            is TeamLoad.Loaded -> {
                val view = l.team
                LazyColumn(contentPadding = PaddingValues(start = ListGutter, end = ListGutter, top = ListGutter, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(ListGutter)) {
                    item {
                        Card {
                            Column(Modifier.padding(16.dp)) {
                                Text("Lichess team", style = MaterialTheme.typography.labelMedium, color = colors.muted)
                                val team = view.team
                                if (team == null) {
                                    Text("No Lichess team is linked to this community yet.", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = colors.ink)
                                } else {
                                    Row(
                                        Modifier.padding(top = 6.dp).clickable { runCatching { uri.openUri(team.url) } },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(team.name, style = MaterialTheme.typography.titleMedium, color = colors.ink)
                                            Text(plural(team.nbMembers, "member") + " on Lichess", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                                        }
                                        Icon(Icons.ExternalLink, "On Lichess", Modifier.size(18.dp), tint = colors.accentStrong)
                                    }
                                    Text(
                                        "Tournaments are for members of the team, and are played on lichess.org. Members of ${view.community.name} who have connected Lichess show in the standings.",
                                        Modifier.padding(top = 10.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.muted,
                                    )
                                }
                                if (view.you.communityModerator || view.you.siteModerator) {
                                    Text(
                                        "Moderators link the team and make tournaments on the HeyNaija website.",
                                        Modifier.padding(top = 10.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.muted,
                                    )
                                }
                            }
                        }
                    }
                    competitionSection("Coming up and live", view.tournaments.current)
                    competitionSection("Recent tournaments", view.tournaments.recent)
                    if (view.tournaments.current.isEmpty() && view.tournaments.recent.isEmpty()) {
                        item { Card { Empty("No tournaments yet.") } }
                    }
                }
            }
        }
    }
}

/**
 * "Chess on Lichess" on a community's page, for a member with Games: its team and
 * its next tournament. Nothing for a community without a team.
 */
@Composable
fun CommunityChessLine(slug: String) {
    val app = LocalApp.current
    if (app.me?.has("games") != true) return
    val team by produceState<ChessTeam?>(null, slug) {
        value = try {
            app.api.chessTeam(slug)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
    val view = team ?: return
    val linked = view.team ?: return
    val colors = HeyNaija.colors
    val next = view.tournaments.current.firstOrNull()
    Row(Modifier.padding(top = 12.dp).clickable { app.navigator.push(Route.ChessTeam(slug)) }, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Chess, null, Modifier.size(18.dp), tint = colors.muted)
        Spacer(Modifier.width(8.dp))
        Text(
            buildAnnotatedString {
                append("Chess on Lichess: ")
                withStyle(SpanStyle(color = colors.ink)) { append(linked.name) }
                next?.let {
                    append(" · Next: ")
                    withStyle(SpanStyle(color = colors.ink)) { append(it.name) }
                }
                append(" · ")
                withStyle(SpanStyle(color = colors.accentStrong, fontWeight = FontWeight.SemiBold)) { append("Tournaments") }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
        )
    }
}

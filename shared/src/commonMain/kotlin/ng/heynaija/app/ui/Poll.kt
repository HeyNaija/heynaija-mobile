package ng.heynaija.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlin.time.Instant
import ng.heynaija.app.LocalApp
import ng.heynaija.app.api.Poll
import ng.heynaija.app.api.Post
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii

/**
 * A poll: choices to press until the reader votes (or it closes), then each
 * choice's share. A poll with more than one answer collects choices first.
 */
@Composable
fun PollCard(post: Post, poll: Poll, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val results = poll.viewerVoted || poll.closed || app.me == null
    var chosen by remember(post.id) { mutableStateOf(setOf<String>()) }

    fun vote(ids: List<String>) {
        val voted = poll.copy(
            viewerVoted = true,
            totalVotes = poll.totalVotes + 1,
            options = poll.options.map { if (it.id in ids) it.copy(voteCount = it.voteCount + 1, viewerVoted = true) else it },
        )
        app.act(post, post.copy(poll = voted), "Your vote did not go through.") { app.api.vote(post.id, ids) }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (option in poll.options.sortedBy { it.position }) {
            val share = if (poll.totalVotes > 0) option.voteCount.toFloat() / poll.totalVotes else 0f
            val picked = option.id in chosen
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clip(Radii.control)
                    .border(1.dp, if (picked) colors.accent else colors.lineStrong, Radii.control)
                    .let { m ->
                        if (results) m else m.clickable {
                            app.requireMember {
                                if (poll.multiple) chosen = if (picked) chosen - option.id else chosen + option.id
                                else vote(listOf(option.id))
                            }
                        }
                    },
            ) {
                if (results) {
                    Box(Modifier.matchParentSize()) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(share).background(if (option.viewerVoted) colors.accentSoft else colors.panel))
                    }
                }
                Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (option.viewerVoted || picked) {
                        Icon(Icons.Check, null, Modifier.size(16.dp), tint = colors.accentStrong)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        option.text,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (option.viewerVoted) FontWeight.SemiBold else FontWeight.Normal,
                        color = colors.ink,
                    )
                    if (results) Text("${(share * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = colors.ink)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(plural(poll.totalVotes, "vote"), pollTime(poll)).joinToString(" · "),
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = colors.muted,
            )
            if (poll.multiple && !results) {
                PrimaryButton("Vote", { vote(chosen.toList()) }, enabled = chosen.isNotEmpty())
            }
        }
    }
}

private fun pollTime(poll: Poll): String? {
    if (poll.closed) return "Final results"
    val ends = poll.expiresAt?.let { Instant.parse(it) } ?: return null
    val left = ends - Clock.System.now()
    return when {
        left.inWholeDays >= 1 -> "${plural(left.inWholeDays.toInt(), "day")} left"
        left.inWholeHours >= 1 -> "${plural(left.inWholeHours.toInt(), "hour")} left"
        left.inWholeMinutes >= 1 -> "${plural(left.inWholeMinutes.toInt(), "minute")} left"
        else -> "Closing"
    }
}

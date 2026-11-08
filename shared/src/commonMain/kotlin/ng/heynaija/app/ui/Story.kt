package ng.heynaija.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ng.heynaija.app.LocalApp
import ng.heynaija.app.api.Post
import ng.heynaija.app.api.PostStory
import ng.heynaija.app.api.StoryDraft
import ng.heynaija.app.compose.asQuote
import ng.heynaija.app.nav.Route
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii

// Stories (web ADR 0024): one author's updates to a post, in order, shown once in feeds.

fun updatesLabel(n: Int) = if (n == 1) "1 update" else "$n updates"

/** Adding the next update to the story [this] post starts or belongs to. */
fun Post.storyDraft(): StoryDraft {
    val story = story
    return if (story != null && story.number > 0) StoryDraft(story.id, story.updates + 1, story.start)
    else StoryDraft(id, (story?.updates ?: 0) + 1, asQuote())
}

/** Where a post card's avatar sits across: its 14dp inset plus half a 40dp avatar. */
private val CARD_AXIS = 34.dp

/**
 * Above an update away from its story: the story's first post on one line, its
 * small avatar over the card's, and how many updates there are. [StoryJoin] draws
 * the short line from its bottom edge to the card under it.
 */
@Composable
fun StoryStart(story: PostStory, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val start = story.start
    val readable = start != null && !start.unavailable
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(Radii.card)
            .background(colors.surface)
            .border(1.dp, colors.line, Radii.card)
            .let { if (readable) it.clickable { app.navigator.push(Route.Thread(story.id)) } else it }
            .padding(start = if (readable) CARD_AXIS - 10.dp else 14.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (readable) Avatar(start.authorHandle ?: "", start.authorAvatar, 20.dp)
        Text(
            if (readable) start.text.replace(Regex("\\s+"), " ").trim().ifEmpty { "Photos" } else "The start of this story is no longer available.",
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(Icons.Story, null, Modifier.size(14.dp), tint = colors.accentStrong)
        Text(
            "Story · ${updatesLabel(story.updates)}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.accentStrong,
            maxLines = 1,
        )
    }
}

/** The short line in the gap between a story's start box and the update under it, on the card's avatar axis. */
@Composable
fun StoryJoin() {
    Box(Modifier.padding(start = CARD_AXIS - 1.dp).width(2.dp).height(8.dp).background(HeyNaija.colors.lineStrong))
}

/** Under a story's first post: how many updates and followers, and Follow story for everyone but its author. */
@Composable
fun StoryBar(post: Post, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val story = post.story ?: return
    val me = app.me
    Row(
        modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Story, null, Modifier.size(16.dp), tint = colors.accentStrong)
        Spacer(Modifier.width(6.dp))
        Text(
            buildString {
                append("Story · ${updatesLabel(story.updates)}")
                if (story.followers > 0) append(" · ${story.followers} following")
            },
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.muted,
        )
        when {
            me == null -> SecondaryButton("Follow story", { app.navigator.push(Route.SignIn) })
            me.id == post.authorId -> {}
            story.following -> SecondaryButton("Following", {
                app.act(post, post.copy(story = story.copy(following = false, followers = (story.followers - 1).coerceAtLeast(0))), "That did not save.") {
                    app.api.setStoryFollow(story.id, false)
                }
            })
            else -> PrimaryButton("Follow story", {
                app.act(post, post.copy(story = story.copy(following = true, followers = story.followers + 1)), "That did not save.") {
                    app.api.setStoryFollow(story.id, true)
                }
                app.say("You will hear about each new update")
            })
        }
    }
}

/** Inset of a spine's cards, and where its line runs across. */
private val SPINE_INDENT = 26.dp
private val SPINE_LINE = 12.dp
private const val SPINE_GAP_DP = 8

/**
 * One item on a story's spine: the line comes down the gutter from the item above
 * and curves into this one at [curveAt] below its top (its avatar's height for a
 * card; the middle when null). It runs on past it unless it is the [last]. Drawn
 * in the gap and the gutter, never across the card.
 */
@Composable
fun SpineItem(last: Boolean, curveAt: Dp? = null, content: @Composable () -> Unit) {
    val color = HeyNaija.colors.accent
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val gap = SPINE_GAP_DP.dp.toPx()
                val x = SPINE_LINE.toPx()
                val edge = SPINE_INDENT.toPx()
                val radius = 8.dp.toPx()
                val y = gap + (curveAt?.toPx() ?: ((size.height - gap) / 2))
                val stroke = 2.dp.toPx()
                // Every straight run is drawn as a line, so the spine is the same from one item to the next.
                drawLine(color, Offset(x, 0f), Offset(x, if (last) y - radius else size.height), stroke)
                val path = Path().apply {
                    moveTo(x, y - radius)
                    quadraticTo(x, y, x + radius, y)
                    lineTo(edge, y)
                }
                drawPath(path, color, style = Stroke(width = stroke))
            }
            .padding(start = SPINE_INDENT, top = SPINE_GAP_DP.dp),
    ) { content() }
}

/**
 * At the end of the spine, for its author: "Add update N…" with their face, edged
 * in dashes. It opens the sheet replies are written in, over this screen.
 */
@Composable
fun UpdatePrompt(post: Post) {
    val app = LocalApp.current
    val me = app.me ?: return
    val colors = HeyNaija.colors
    val next = (post.story?.updates ?: 0) + 1
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radii.card)
            .background(colors.surface)
            .drawBehind {
                val width = 1.dp.toPx()
                drawRoundRect(
                    colors.accent,
                    topLeft = Offset(width / 2, width / 2),
                    size = Size(size.width - width, size.height - width),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .clickable { app.addUpdate(post) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(me.handle, me.avatar, 32.dp)
        Text("Add update $next…", style = MaterialTheme.typography.bodyLarge, color = colors.faint)
    }
}

/** A button-sized row on the spine: unfolding earlier updates. */
@Composable
fun SpineButton(label: String, icon: ImageVector, accent: Boolean, onClick: () -> Unit) {
    val colors = HeyNaija.colors
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .clip(Radii.control)
            .background(if (accent) colors.accentSoft else colors.surface)
            .border(1.dp, if (accent) colors.accent else colors.line, Radii.control)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = if (accent) colors.accentStrong else colors.muted)
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (accent) colors.accentStrong else colors.muted)
    }
}

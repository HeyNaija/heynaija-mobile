package ng.heynaija.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.time.Instant
import ng.heynaija.app.api.Post
import ng.heynaija.app.api.PostImage
import ng.heynaija.app.api.PostVideo
import ng.heynaija.app.api.QuotedPost
import ng.heynaija.app.api.mediaUrl
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii

/** A white card with a hairline edge and no shadow: the surface every post sits on. */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = HeyNaija.colors
    Box(
        modifier
            .fillMaxWidth()
            .clip(Radii.card)
            .background(colors.surface)
            .border(1.dp, colors.line, Radii.card),
    ) { content() }
}

/** A post in a list, read-only for now: nothing on it can be pressed yet. */
@Composable
fun PostCard(post: Post, now: Instant, modifier: Modifier = Modifier) {
    val colors = HeyNaija.colors
    Card(modifier) {
        if (post.deleted) {
            Text(
                "This post is no longer available.",
                Modifier.padding(18.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = colors.muted,
            )
            return@Card
        }
        Column(Modifier.padding(18.dp)) {
            val reposter = post.repostedBy?.takeIf { post.reason == "repost" }
            if (reposter != null) {
                Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Repost, null, Modifier.size(13.dp), tint = colors.muted)
                    Spacer(Modifier.width(6.dp))
                    Text("@${reposter.handle} reposted", style = MaterialTheme.typography.labelSmall, color = colors.muted)
                }
            }
            Row {
                Avatar(post.authorHandle, post.authorAvatar, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Byline(post, now)
                    if (post.community != null) Chip(post.community.name)
                    if (post.text.isNotBlank()) {
                        // Long posts show their start in a list, as on the web.
                        Text(
                            post.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.ink,
                            maxLines = 12,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (post.images.isNotEmpty()) PostImages(post.images)
                    if (post.video != null) VideoPoster(post.video)
                    if (post.quote != null) QuotedPostCard(post.quote)
                    Counts(post)
                }
            }
        }
    }
}

@Composable
private fun Byline(post: Post, now: Instant) {
    val colors = HeyNaija.colors
    val ago = remember(post.createdAt, now) { timeAgo(Instant.parse(post.createdAt), now) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            post.authorName ?: "@${post.authorHandle}",
            Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.titleMedium,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (post.authorFounder) {
            Spacer(Modifier.width(6.dp))
            Chip("Founder")
        }
        Spacer(Modifier.width(6.dp))
        Text(
            if (post.authorName != null) "@${post.authorHandle} · $ago" else ago,
            Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.labelSmall,
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Sage wash, deep green label: communities and badges. */
@Composable
fun Chip(label: String) {
    val colors = HeyNaija.colors
    Text(
        label,
        Modifier.clip(Radii.chip).background(colors.accentSoft).padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = colors.accentStrong,
        maxLines = 1,
    )
}

/** One picture at its own shape (within limits); two to four in a grid of squares. */
@Composable
private fun PostImages(images: List<PostImage>) {
    if (images.size == 1) {
        val image = images.first()
        Picture(image, Modifier.fillMaxWidth().aspectRatio((image.width.toFloat() / image.height).coerceIn(0.8f, 2f)))
        return
    }
    Column(
        Modifier.clip(Radii.control).border(1.dp, HeyNaija.colors.line, Radii.control),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (row in images.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (image in row) Picture(image, Modifier.weight(1f).aspectRatio(1f), rounded = false)
            }
        }
    }
}

@Composable
private fun Picture(image: PostImage, modifier: Modifier, rounded: Boolean = true) {
    val colors = HeyNaija.colors
    val edge = if (rounded) Modifier.clip(Radii.control).border(1.dp, colors.line, Radii.control) else Modifier
    Box(modifier.then(edge).background(colors.panel)) {
        AsyncImage(
            // A GIF shows its first frame until it can be played.
            model = mediaUrl(image.src, if (image.animated) "still" else "640"),
            contentDescription = image.alt.ifBlank { null },
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        if (image.animated) Badge("GIF", Modifier.align(Alignment.BottomStart).padding(8.dp))
    }
}

/** The poster and length; playing it, with the platforms' own HLS players, comes next. */
@Composable
private fun VideoPoster(video: PostVideo) {
    val ratio = video.width.toFloat() / video.height
    val shown = ratio.coerceIn(0.8f, 16f / 9f)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(shown)
            .clip(Radii.control)
            .background(Color.Black)
            .border(1.dp, HeyNaija.colors.line, Radii.control),
    ) {
        AsyncImage(
            model = video.posterUrl,
            contentDescription = video.description.ifBlank { null },
            // A clip of an unusual shape is letterboxed; any other fills the frame,
            // so rounding leaves no sliver of black at the edge.
            contentScale = if (shown == ratio) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier.matchParentSize(),
        )
        Badge(clipLength(video.durationMs), Modifier.align(Alignment.BottomStart).padding(8.dp), Icons.Play)
    }
}

/** Light text on a dark scrim, over a picture. */
@Composable
private fun Badge(label: String, modifier: Modifier, icon: ImageVector? = null) {
    Row(
        modifier.clip(Radii.chip).background(Color(0xD9070D0B)).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(12.dp), tint = Color(0xFFEEF4F0))
            Spacer(Modifier.width(4.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFFEEF4F0))
    }
}

@Composable
private fun QuotedPostCard(quote: QuotedPost) {
    val colors = HeyNaija.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Radii.control)
            .background(colors.panel)
            .border(1.dp, colors.line, Radii.control)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (quote.unavailable || quote.authorHandle == null) {
            Text("This post is unavailable.", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(quote.authorHandle, quote.authorAvatar, 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                quote.authorName ?: "@${quote.authorHandle}",
                Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.labelLarge,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (quote.authorName != null) {
                Spacer(Modifier.width(6.dp))
                Text("@${quote.authorHandle}", style = MaterialTheme.typography.labelSmall, color = colors.muted, maxLines = 1)
            }
        }
        Text(
            quote.text.ifBlank { if (quote.images.isNotEmpty()) "Post with pictures" else "" },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Counts(post: Post) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 2.dp)) {
        Count(Icons.Bubble, post.replyCount, if (post.replyCount == 1) "1 reply" else "${post.replyCount} replies")
        Count(Icons.Repost, post.repostCount, if (post.repostCount == 1) "1 repost" else "${post.repostCount} reposts")
        Count(Icons.Heart, post.likeCount, if (post.likeCount == 1) "1 like" else "${post.likeCount} likes")
    }
}

@Composable
private fun Count(icon: ImageVector, count: Int, label: String) {
    val colors = HeyNaija.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, label, Modifier.size(18.dp), tint = colors.muted)
        if (count > 0) {
            Spacer(Modifier.width(6.dp))
            Text("$count", style = MaterialTheme.typography.labelSmall, color = colors.muted)
        }
    }
}

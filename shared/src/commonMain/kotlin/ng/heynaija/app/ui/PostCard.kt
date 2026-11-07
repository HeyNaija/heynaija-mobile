package ng.heynaija.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.time.Instant
import ng.heynaija.app.AppState
import ng.heynaija.app.LocalApp
import ng.heynaija.app.api.LinkPreview
import ng.heynaija.app.api.Post
import ng.heynaija.app.api.PostImage
import ng.heynaija.app.api.PostVideo
import ng.heynaija.app.api.QuotedPost
import ng.heynaija.app.api.mediaFile
import ng.heynaija.app.api.mediaUrl
import ng.heynaija.app.launchCatching
import ng.heynaija.app.media.VideoPlayer
import ng.heynaija.app.nav.Route
import ng.heynaija.app.platform.rememberShare
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii

/** A white card with a hairline edge and no shadow: the surface every post sits on. */
@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val colors = HeyNaija.colors
    Box(
        modifier
            .fillMaxWidth()
            .clip(Radii.card)
            .background(colors.surface)
            .border(1.dp, colors.line, Radii.card)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) { content() }
}

/** Lines of a long post shown in a list before "Show more"; its own page shows it all. */
private const val PREVIEW_LINES = 10

/**
 * A reply in a Gist: it opens, is answered and is liked through the Gist, and is
 * never reposted, bookmarked or shared. Its host may hide it.
 */
data class GistReplyContext(val id: String, val viewerIsHost: Boolean)

/**
 * A post as lists show it, laid out like the web's: the avatar sits beside the
 * name only, and the text, media and actions use the card's whole width.
 * [focused]: the post a thread is about, larger and complete. [compact]: a reply
 * in a thread, smaller and without the community chip. [gist]: a reply in a Gist.
 */
@Composable
fun PostCard(
    post: Post,
    now: Instant,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    compact: Boolean = false,
    pinned: Boolean = false,
    gist: GistReplyContext? = null,
) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    if (post.deleted || post.unavailable) {
        Card(modifier) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp).heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (post.hiddenByHost) "The host hid this reply." else "This post is no longer available.",
                    Modifier.weight(1f).padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = colors.muted,
                )
                if (post.hiddenByHost && gist?.viewerIsHost == true) {
                    TextButton(onClick = { app.hideGistReply(gist.id, post, hidden = false) }) {
                        Text("Show reply", style = MaterialTheme.typography.labelLarge, color = colors.accentStrong)
                    }
                }
            }
        }
        return
    }
    val open = { app.navigator.push(Route.Thread(post.id, post, gist?.id)) }
    val story = post.story
    // An update away from its story shows where it comes from (web ADR 0024).
    if (!focused && !compact && story != null && story.number > 0) {
        Column(modifier) {
            StoryStart(story)
            StoryJoin()
            PostSurface(post, now, Modifier, focused, compact, pinned, gist, open)
        }
        return
    }
    PostSurface(post, now, modifier, focused, compact, pinned, gist, open)
}

@Composable
private fun PostSurface(
    post: Post,
    now: Instant,
    modifier: Modifier,
    focused: Boolean,
    compact: Boolean,
    pinned: Boolean,
    gist: GistReplyContext?,
    open: () -> Unit,
) {
    Card(modifier, onClick = if (focused) null else open) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = if (compact) 10.dp else 14.dp)) {
            Column(Modifier.padding(end = 8.dp)) {
                ContextLine(post, pinned)
                if (!focused && !compact) ReplyContextLine(post)
            }
            Header(post, now, focused, compact, gist)
            Column(Modifier.padding(end = 8.dp)) {
                Body(post, focused, compact, open)
            }
            if (focused) FocusedMeta(post)
            Actions(post, compact, gist)
        }
    }
}

/** Why the post is here: pinned, reposted, or what Discover saw in it. */
@Composable
private fun ContextLine(post: Post, pinned: Boolean) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val reposter = post.repostedBy?.takeIf { post.reason == "repost" }
    val (icon, text, handle) = when {
        pinned -> Triple(Icons.Pin, if (post.pinnedInCommunity && !post.pinnedOnProfile) "Pinned by the moderators" else "Pinned", null)
        reposter != null -> Triple(Icons.Repost, if (reposter.id == app.me?.id) "You reposted" else "@${reposter.handle} reposted", reposter.handle)
        post.reason == "network" && post.via != null -> {
            val via = post.via
            val action = when (via.action) { "quoted" -> "quoted this"; else -> via.action }
            val others = if (via.others > 0) ", and ${if (via.others == 1) "1 other" else "${via.others} others"} you follow" else ""
            Triple(null, "@${via.handle} $action$others", via.handle)
        }
        post.reason == "active" -> Triple(null, "Active on HeyNaija", null)
        post.reason == "recent" -> Triple(null, "New on HeyNaija", null)
        post.reason == "following" -> Triple(null, "You follow @${post.authorHandle}", null)
        post.reason == "community" && post.community != null -> Triple(null, "From ${post.community.name}, which you joined", null)
        else -> return
    }
    Row(
        Modifier.padding(bottom = 10.dp).let { m -> if (handle != null) m.clickable { app.navigator.push(Route.Profile(handle)) } else m },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(14.dp), tint = colors.muted)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** In a list, a reply says whom it answers. */
@Composable
private fun ReplyContextLine(post: Post) {
    if (post.replyParentId == null) return
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val parent = post.replyTo
    val label = when {
        parent == null || parent.unavailable -> "Replying to a post that is no longer available"
        else -> "Replying to @${parent.authorHandle}"
    }
    Row(
        Modifier
            .padding(bottom = 10.dp)
            .clickable { app.navigator.push(Route.Thread(post.replyParentId)) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Bubble, null, Modifier.size(14.dp), tint = colors.muted)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Header(post: Post, now: Instant, focused: Boolean, compact: Boolean, gist: GistReplyContext?) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val toProfile = { app.navigator.push(Route.Profile(post.authorHandle)) }
    val ago = remember(post.createdAt, now) { timeAgo(Instant.parse(post.createdAt), now) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(post.authorHandle, post.authorAvatar, if (compact) 32.dp else 40.dp, Modifier.clip(androidx.compose.foundation.shape.CircleShape).clickable(onClick = toProfile))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    post.authorName ?: "@${post.authorHandle}",
                    Modifier.weight(1f, fill = false).clickable(onClick = toProfile),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (post.authorFounder) {
                    Spacer(Modifier.width(4.dp))
                    FounderBadge()
                }
            }
            // A story's place, in the accent: an update's number, or "Story" on its first post in a list.
            val storyLabel = post.story?.let { s -> if (s.number > 0) "Update ${s.number}" else if (!focused) "Story" else null }
            Text(
                buildAnnotatedString {
                    if (post.authorName != null) append("@${post.authorHandle} · ")
                    append(if (focused) postedAt(Instant.parse(post.createdAt)) else ago)
                    if (storyLabel != null) {
                        append(" · ")
                        withStyle(SpanStyle(color = colors.accentStrong, fontWeight = FontWeight.SemiBold)) { append(storyLabel) }
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PostMenu(post, gist)
    }
    if (post.community != null && !compact && post.reason != "community") {
        Row(Modifier.padding(top = 8.dp)) {
            Chip(post.community.name) { app.navigator.push(Route.Community(post.community.slug)) }
        }
    }
}

@Composable
private fun Body(post: Post, focused: Boolean, compact: Boolean, open: () -> Unit) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val gap = if (compact) 8.dp else 10.dp
    if (post.text.isNotBlank()) {
        var cut by remember(post.id) { mutableStateOf(false) }
        PostText(
            post,
            Modifier.padding(top = gap),
            style = if (focused) MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize * 1.07f, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.07f) else MaterialTheme.typography.bodyLarge,
            maxLines = if (focused) Int.MAX_VALUE else PREVIEW_LINES,
            onTextLayout = { cut = it.hasVisualOverflow },
        )
        if (cut) {
            Text(
                "Show more",
                Modifier.padding(top = 4.dp).clickable(onClick = open),
                style = MaterialTheme.typography.labelLarge,
                color = colors.accentStrong,
            )
        }
    }
    post.poll?.let { PollCard(post, it, Modifier.padding(top = gap)) }
    if (post.images.isNotEmpty()) {
        MediaCover(post.coverMedia, Modifier.padding(top = gap)) {
            PostImages(post.images, Modifier.padding(top = gap)) { index -> app.navigator.push(Route.Images(post.images, index)) }
        }
    }
    post.video?.let { video -> MediaCover(post.coverMedia, Modifier.padding(top = gap)) { VideoPoster(video, post.id, Modifier.padding(top = gap)) } }
    post.quote?.let { QuotedPostCard(it, Modifier.padding(top = gap)) }
    if (post.linkPreview != null && post.images.isEmpty() && post.video == null && post.quote == null) {
        LinkCard(post.linkPreview, Modifier.padding(top = gap))
    }
    if (post.replyParentId == null && post.replyAudience != "everyone") {
        val note = when (post.replyAudience) {
            "following" -> if (post.authorId == app.me?.id) "People you follow can reply" else "People @${post.authorHandle} follows can reply"
            else -> "Only people mentioned can reply"
        }
        Row(Modifier.padding(top = gap), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Bubble, null, Modifier.size(14.dp), tint = colors.muted)
            Spacer(Modifier.width(6.dp))
            Text(note, style = MaterialTheme.typography.labelSmall, color = colors.muted)
        }
    }
}

/**
 * The pictures or video of a post marked sensitive (ADR 0020), held back behind one
 * quiet line until the reader taps Show, as on the web (media-cover.tsx). Only adults
 * are sent such a post; the API says when to cover it. Nothing of the media is drawn
 * until Show, rather than drawn blurred: Android before 12 cannot blur.
 */
@Composable
fun MediaCover(covered: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    if (!covered || shown) {
        content()
        return
    }
    val onScrim = Color(0xFFEEF4F0)
    Row(
        modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(Radii.control).background(Color(0xD9070D0B)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Sensitive, null, Modifier.size(18.dp), tint = onScrim)
        Spacer(Modifier.width(8.dp))
        Text("Sensitive content", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = onScrim)
        Text(
            "Show",
            Modifier
                .clip(androidx.compose.foundation.shape.CircleShape)
                .border(1.dp, onScrim.copy(alpha = 0.35f), androidx.compose.foundation.shape.CircleShape)
                .clickable { shown = true }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = onScrim,
        )
    }
}

/** On a thread's own post: the counts in words. */
@Composable
private fun FocusedMeta(post: Post) {
    val colors = HeyNaija.colors
    val parts = buildList {
        if (post.repostCount > 0) add(plural(post.repostCount, "repost"))
        if (post.likeCount > 0) add(plural(post.likeCount, "like"))
    }
    if (parts.isEmpty()) return
    Text(
        parts.joinToString("  ·  "),
        Modifier.padding(top = 12.dp, end = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = colors.muted,
    )
}

fun plural(count: Int, word: String) = if (count == 1) "1 $word" else "$count ${word}s"

/** Sage wash, deep green label: communities and badges. */
@Composable
fun Chip(label: String, onClick: (() -> Unit)? = null) {
    val colors = HeyNaija.colors
    Text(
        label,
        Modifier
            .clip(Radii.chip)
            .background(colors.accentSoft)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = colors.accentStrong,
        maxLines = 1,
    )
}

/** One picture at its own shape (within limits); two to four in a grid of squares. */
@Composable
fun PostImages(images: List<PostImage>, modifier: Modifier = Modifier, onOpen: (Int) -> Unit) {
    if (images.size == 1) {
        val image = images.first()
        Picture(
            image,
            modifier.fillMaxWidth().aspectRatio((image.width.toFloat() / image.height).coerceIn(0.8f, 2f)).clickable { onOpen(0) },
        )
        return
    }
    Column(
        modifier.clip(Radii.control).border(1.dp, HeyNaija.colors.line, Radii.control),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        images.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                pair.forEachIndexed { column, image ->
                    val index = row * 2 + column
                    // Three pictures: the last one spans the row, as wide as two.
                    val ratio = if (images.size == 3 && index == 2) 2f else 1f
                    Picture(image, Modifier.weight(1f).aspectRatio(ratio).clickable { onOpen(index) }, rounded = false)
                }
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
            // A GIF moves in the list, as on the web.
            model = mediaUrl(image.src, "640"),
            contentDescription = image.alt.ifBlank { null },
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        if (image.animated) Badge("GIF", Modifier.align(Alignment.BottomStart).padding(8.dp))
    }
}

/**
 * A post's video, as on the web (post-video.tsx): the poster and length first,
 * never autoplay; a tap plays it here, in the card. Only one plays at a time:
 * starting another puts this one's poster back. It stops when it scrolls out of
 * the list or its screen is left, and the poster comes back when it ends. Full
 * screen carries on from the same point.
 */
@Composable
private fun VideoPoster(video: PostVideo, postId: String, modifier: Modifier) {
    val app = LocalApp.current
    val ratio = video.width.toFloat() / video.height
    val shown = ratio.coerceIn(0.75f, 16f / 9f)
    val playlist = video.playlists?.all?.let(::mediaFile)
    var started by remember(postId) { mutableStateOf(false) }
    val playing by app.playingVideo.collectAsState()
    val active = started && playing == postId
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(shown)
            .clip(Radii.control)
            .background(Color.Black)
            .border(1.dp, HeyNaija.colors.line, Radii.control),
    ) {
        if (active && playlist != null) {
            VideoPlayer(
                playlist,
                Modifier.matchParentSize(),
                onEnded = { started = false },
                onFullScreen = { at ->
                    started = false
                    app.navigator.push(Route.Video(playlist, at))
                },
            )
        } else {
            Box(
                Modifier.matchParentSize().clickable(enabled = playlist != null) {
                    app.playVideo(postId)
                    started = true
                },
            ) {
                AsyncImage(
                    model = mediaFile(video.posterUrl),
                    contentDescription = video.description.ifBlank { null },
                    // A clip of an unusual shape is letterboxed; any other fills the frame,
                    // so rounding leaves no sliver of black at the edge.
                    contentScale = if (shown == ratio) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.matchParentSize(),
                )
                Box(
                    Modifier.align(Alignment.Center).size(52.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xB3070D0B)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Play, "Play", Modifier.size(24.dp), tint = Color(0xFFEEF4F0))
                }
                Badge(clipLength(video.durationMs), Modifier.align(Alignment.BottomStart).padding(8.dp))
            }
        }
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
fun QuotedPostCard(quote: QuotedPost, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(Radii.control)
            .background(colors.panel)
            .border(1.dp, colors.lineStrong, Radii.control)
            .let { m -> quote.id?.takeUnless { quote.unavailable }?.let { id -> m.clickable { app.navigator.push(Route.Thread(id)) } } ?: m }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (quote.unavailable || quote.authorHandle == null) {
            Text("This post is unavailable.", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = colors.muted)
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
            if (quote.authorFounder) {
                Spacer(Modifier.width(6.dp))
                FounderBadge(plain = true)
            }
            if (quote.authorName != null) {
                Spacer(Modifier.width(6.dp))
                Text("@${quote.authorHandle}", style = MaterialTheme.typography.labelSmall, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val text = quote.text.ifBlank { if (quote.images.isNotEmpty()) "Post with pictures" else "" }
        if (text.isNotEmpty()) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.ink, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        if (quote.images.isNotEmpty() && quote.coverMedia) {
            Text("Sensitive content. Open the post to see it.", style = MaterialTheme.typography.labelSmall, color = colors.muted)
        } else if (quote.images.isNotEmpty()) {
            Row(Modifier.height(120.dp).clip(Radii.control), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (image in quote.images.take(2)) {
                    AsyncImage(
                        model = mediaUrl(image.src, if (image.animated) "still" else "640"),
                        contentDescription = image.alt.ifBlank { null },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).height(120.dp).background(colors.line),
                    )
                }
            }
        }
    }
}

/** The page the post's first address leads to. */
@Composable
private fun LinkCard(preview: LinkPreview, modifier: Modifier) {
    val uri = LocalUriHandler.current
    val colors = HeyNaija.colors
    val host = remember(preview.url) { preview.url.substringAfter("://").substringBefore('/').removePrefix("www.") }
    Column(
        modifier
            .fillMaxWidth()
            .clip(Radii.control)
            .background(colors.panel)
            .border(1.dp, colors.lineStrong, Radii.control)
            .clickable { runCatching { uri.openUri(preview.url) } },
    ) {
        preview.image?.let { image ->
            AsyncImage(
                model = mediaUrl(image.src, "640"),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).aspectRatio(1.91f).background(colors.line),
            )
            HorizontalDivider(color = colors.line)
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(host, style = MaterialTheme.typography.labelSmall, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(preview.title, style = MaterialTheme.typography.labelLarge, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            preview.description?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Reply, repost, like on the left; bookmark and share on the right, over a hairline. A Gist's reply: reply and like. */
@Composable
private fun Actions(post: Post, compact: Boolean, gist: GistReplyContext?) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val share = rememberShare()
    if (!compact) HorizontalDivider(Modifier.padding(top = 12.dp, end = 8.dp), color = colors.line)
    Row(
        Modifier.fillMaxWidth().padding(vertical = if (compact) 0.dp else 2.dp).heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Action(Icons.Bubble, post.replyCount, if (post.replyCount == 1) "1 reply" else "${post.replyCount} replies", enabled = post.viewerCanReply || app.me == null) {
            app.reply(post, gist?.id)
        }
        if (gist == null) RepostAction(post)
        Action(
            if (post.viewerLiked) Icons.HeartFilled else Icons.Heart,
            post.likeCount,
            if (post.viewerLiked) "Unlike" else "Like",
            // Pressed reads in the deep green, as on the web, not a red heart.
            tint = if (post.viewerLiked) colors.accentStrong else colors.muted,
        ) { app.toggleLike(post, gist?.id) }
        Spacer(Modifier.weight(1f))
        if (gist != null) return@Row
        if (!compact) {
            Action(
                if (post.viewerBookmarked) Icons.BookmarkFilled else Icons.Bookmark,
                0,
                if (post.viewerBookmarked) "Remove bookmark" else "Bookmark",
                tint = if (post.viewerBookmarked) colors.accentStrong else colors.muted,
            ) { app.toggleBookmark(post) }
        }
        Action(Icons.Share, 0, "Share") { share(app.postLink(post)) }
    }
}

@Composable
private fun Action(icon: ImageVector, count: Int, label: String, tint: Color = HeyNaija.colors.muted, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(Radii.control)
            .clickable(enabled = enabled, onClick = onClick, onClickLabel = label)
            .heightIn(min = 40.dp)
            .widthIn(min = 40.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, label, Modifier.size(18.dp), tint = if (enabled) tint else tint.copy(alpha = 0.45f))
        if (count > 0) {
            Spacer(Modifier.width(5.dp))
            Text(compactCount(count), style = MaterialTheme.typography.labelSmall, color = tint)
        }
        if (count > 0) Spacer(Modifier.width(6.dp))
    }
}

/** 1.2K, 3.4M: counts stay short in the action row. */
fun compactCount(count: Int): String = when {
    count < 1000 -> "$count"
    count < 10_000 -> "${count / 1000}.${(count % 1000) / 100}K".replace(".0K", "K")
    count < 1_000_000 -> "${count / 1000}K"
    else -> "${count / 1_000_000}.${(count % 1_000_000) / 100_000}M".replace(".0M", "M")
}

/** Repost opens a choice: repost (or undo it), or quote. */
@Composable
private fun RepostAction(post: Post) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Action(Icons.Repost, post.repostCount, "Repost", tint = if (post.viewerReposted) colors.accentStrong else colors.muted) {
            app.requireMember { open = true }
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(if (post.viewerReposted) "Undo repost" else "Repost") },
                leadingIcon = { Icon(Icons.Repost, null, Modifier.size(18.dp)) },
                onClick = { open = false; app.toggleRepost(post) },
            )
            if (app.me?.has("quotePosts") != false) {
                DropdownMenuItem(
                    text = { Text("Quote") },
                    leadingIcon = { Icon(Icons.Quote, null, Modifier.size(18.dp)) },
                    onClick = { open = false; app.navigator.push(Route.Compose(quote = post)) },
                )
            }
        }
    }
}

/** The "..." menu: the author's own choices, or report, mute and block; a Gist's host may hide a reply. */
@Composable
private fun PostMenu(post: Post, gist: GistReplyContext?) {
    val app = LocalApp.current
    val me = app.me ?: return
    val colors = HeyNaija.colors
    var open by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val own = post.authorId == me.id
    Box {
        Box(
            Modifier.size(40.dp).clip(Radii.control).clickable { open = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Dots, "Post options", Modifier.size(20.dp), tint = colors.muted) }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            if (own) {
                if (post.replyParentId == null && gist == null) {
                    // Any of your top-level posts becomes a story with its first update (web ADR 0024).
                    DropdownMenuItem(
                        text = { Text("Add update") },
                        leadingIcon = { Icon(Icons.Plus, null, Modifier.size(18.dp)) },
                        onClick = { open = false; app.addUpdate(post) },
                    )
                    DropdownMenuItem(
                        text = { Text(if (post.pinnedOnProfile) "Unpin from your profile" else "Pin to your profile") },
                        leadingIcon = { Icon(Icons.Pin, null, Modifier.size(18.dp)) },
                        onClick = {
                            open = false
                            val on = !post.pinnedOnProfile
                            app.act(post, post.copy(pinnedOnProfile = on), "That did not save.") { app.api.setProfilePin(post.id, on) }
                            app.say(if (on) "Pinned to your profile" else "Unpinned")
                        },
                    )
                }
                // The author's own mark (ADR 0020): taking it off always works; a moderator's is not theirs to change.
                if (gist == null && post.sensitive != "moderator" && (post.sensitive == "author" || me.canMarkSensitive)) {
                    val marked = post.sensitive == "author"
                    DropdownMenuItem(
                        text = { Text(if (marked) "Remove sensitive mark" else "Mark as sensitive") },
                        leadingIcon = { Icon(Icons.Sensitive, null, Modifier.size(18.dp)) },
                        onClick = {
                            open = false
                            app.act(post, post.copy(sensitive = if (marked) null else "author"), "That did not save.") { app.api.setSensitive(post.id, !marked) }
                            app.say(if (marked) "Sensitive mark removed" else "Marked as sensitive")
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete post", color = colors.danger) },
                    onClick = { open = false; confirm = "delete" },
                )
            } else {
                if (gist?.viewerIsHost == true) {
                    DropdownMenuItem(
                        text = { Text("Hide reply") },
                        onClick = { open = false; app.hideGistReply(gist.id, post, hidden = true) },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Report post") },
                    leadingIcon = { Icon(Icons.Flag, null, Modifier.size(18.dp)) },
                    onClick = { open = false; app.navigator.push(Route.Report("postId" to post.id, "this post")) },
                )
                DropdownMenuItem(
                    text = { Text("Mute @${post.authorHandle}") },
                    leadingIcon = { Icon(Icons.Muted, null, Modifier.size(18.dp)) },
                    onClick = { open = false; muteOrBlock(app, post.authorHandle, block = false) },
                )
                DropdownMenuItem(
                    text = { Text("Block @${post.authorHandle}", color = colors.danger) },
                    onClick = { open = false; confirm = "block" },
                )
            }
        }
    }
    when (confirm) {
        "delete" -> Confirm(
            title = "Delete this post?",
            text = "This cannot be undone.",
            action = "Delete",
            onConfirm = { confirm = null; app.delete(post) },
            onDismiss = { confirm = null },
        )
        "block" -> Confirm(
            title = "Block @${post.authorHandle}?",
            text = "You will stop seeing each other's posts.",
            action = "Block",
            onConfirm = { confirm = null; muteOrBlock(app, post.authorHandle, block = true) },
            onDismiss = { confirm = null },
        )
    }
}

fun muteOrBlock(app: AppState, handle: String, block: Boolean) {
    app.scope.launchCatching(app, if (block) "@$handle could not be blocked." else "@$handle could not be muted.") {
        if (block) app.api.setBlock(handle, true) else app.api.setMute(handle, true)
        app.say(if (block) "Blocked @$handle" else "Muted @$handle. Their posts are hidden from you.")
    }
}

/** A yes-or-no question before something that cannot be taken back. */
@Composable
fun Confirm(title: String, text: String, action: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = HeyNaija.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action, color = colors.danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.ink) } },
        containerColor = colors.surface,
        shape = Radii.card,
    )
}

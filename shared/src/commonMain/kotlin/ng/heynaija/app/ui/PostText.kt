package ng.heynaija.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import ng.heynaija.app.LocalApp
import ng.heynaija.app.api.Post
import ng.heynaija.app.nav.Route
import ng.heynaija.app.theme.HeyNaija

/** A stretch of a post's text that leads somewhere. */
private data class Span(val start: Int, val end: Int, val target: Target)

private sealed interface Target {
    data class Member(val handle: String) : Target
    data class Topic(val tag: String) : Target
    data class Web(val url: String) : Target
}

/**
 * A post's text with its @mentions, #hashtags and web addresses as links, where
 * the API found them (`mentions`, `hashtags`, `links`, in UTF-16 offsets, which
 * is how Kotlin counts too). Anything that overlaps or runs past the end is left
 * as plain text.
 */
@Composable
fun PostText(
    post: Post,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    val linkStyle = TextLinkStyles(SpanStyle(color = colors.accentStrong, fontWeight = FontWeight.Medium))
    val urlStyle = TextLinkStyles(SpanStyle(color = colors.accentStrong), pressedStyle = SpanStyle(color = colors.accentStrong, textDecoration = TextDecoration.Underline))
    val text = remember(post.id, post.text, post.mentions, post.hashtags, post.links, colors) {
        val spans = (
            post.mentions.map { Span(it.start, it.start + it.length, Target.Member(it.handle)) } +
                post.hashtags.map { Span(it.start, it.start + it.length, Target.Topic(it.tag)) } +
                post.links.map { Span(it.start, it.start + it.length, Target.Web(it.url)) }
            ).sortedBy { it.start }
        buildAnnotatedString {
            var at = 0
            for (span in spans) {
                if (span.start < at || span.end > post.text.length || span.start >= span.end) continue
                append(post.text.substring(at, span.start))
                val shown = post.text.substring(span.start, span.end)
                when (val target = span.target) {
                    is Target.Member -> withLink(LinkAnnotation.Clickable("member", linkStyle) { app.navigator.push(Route.Profile(target.handle)) }) { append(shown) }
                    is Target.Topic -> withLink(LinkAnnotation.Clickable("topic", linkStyle) { app.navigator.push(Route.Hashtag(target.tag)) }) { append(shown) }
                    is Target.Web -> withLink(LinkAnnotation.Url(target.url, urlStyle)) { append(shown) }
                }
                at = span.end
            }
            append(post.text.substring(at))
        }
    }
    Text(
        text,
        modifier,
        style = style,
        color = colors.ink,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = onTextLayout,
    )
}

/** Plain text that is not a post's (a bio, an excerpt), unlinked. */
fun plain(text: String) = AnnotatedString(text)

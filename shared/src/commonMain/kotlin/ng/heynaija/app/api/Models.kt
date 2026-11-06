package ng.heynaija.app.api

import kotlinx.serialization.Serializable

// The parts of the API's JSON the app shows so far, by the API's own names
// (`Post` and friends in apps/web/lib/api.ts). Fields the app does not read yet
// are ignored when decoding, so the API can add to them freely.

/** A page of posts; `cursor` asks for the next one, and is null at the end. */
@Serializable
data class FeedPage(
    val posts: List<Post>,
    val cursor: String? = null,
)

@Serializable
data class Post(
    val id: String,
    val authorHandle: String,
    /** The author's chosen name; show the handle alone when null. */
    val authorName: String? = null,
    /** Avatar path without the size suffix; see [mediaUrl]. */
    val authorAvatar: String? = null,
    val authorFounder: Boolean = false,
    val text: String = "",
    /** ISO 8601, in UTC. */
    val createdAt: String,
    val replyParentId: String? = null,
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val repostCount: Int = 0,
    val deleted: Boolean = false,
    val images: List<PostImage> = emptyList(),
    /** Only sent once the video is ready to play. */
    val video: PostVideo? = null,
    val community: CommunityRef? = null,
    /** A quote post's quoted post. */
    val quote: QuotedPost? = null,
    /** Why the post is in the feed: 'repost' when [repostedBy] reposted it. Null in Latest. */
    val reason: String? = null,
    val repostedBy: Reposter? = null,
)

@Serializable
data class PostImage(
    /** Path without the size suffix; see [mediaUrl]. */
    val src: String,
    val alt: String = "",
    val width: Int,
    val height: Int,
    /** A GIF: its "640" and "1600" sizes move, and "still" is its first frame. */
    val animated: Boolean = false,
)

@Serializable
data class PostVideo(
    val id: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    /** Absolute, on the media domain. */
    val posterUrl: String,
    val description: String = "",
)

@Serializable
data class CommunityRef(
    val slug: String,
    val name: String,
)

/** The post a quote post quotes, or only `unavailable` when the reader cannot see it. */
@Serializable
data class QuotedPost(
    val unavailable: Boolean = false,
    val id: String? = null,
    val authorHandle: String? = null,
    val authorName: String? = null,
    val authorAvatar: String? = null,
    val text: String = "",
    val images: List<PostImage> = emptyList(),
)

@Serializable
data class Reposter(
    val id: String,
    val handle: String,
    val displayName: String? = null,
)

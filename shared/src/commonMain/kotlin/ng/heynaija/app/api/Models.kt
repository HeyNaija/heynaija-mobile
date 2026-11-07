package ng.heynaija.app.api

import kotlinx.serialization.Serializable

// The API's JSON, by the API's own names (`Post` and friends in
// apps/web/lib/api.ts). Fields the app does not read are ignored when decoding,
// so the API can add to them freely.

/** The signed-in member, from `/v1/me`. */
@Serializable
data class Me(
    val id: String,
    val handle: String,
    val displayName: String? = null,
    val bio: String = "",
    val avatar: String? = null,
    val unreadNotifications: Int = 0,
    val moderates: Boolean = false,
    /** False until they finish or skip the welcome steps. */
    val onboarded: Boolean = true,
    /** True until a member who joined before signup asked for a date of birth gives theirs (ADR 0020). */
    val birthDateNeeded: Boolean = false,
    /** What is switched on for this member; a missing key is off. */
    val features: Map<String, Boolean> = emptyMap(),
) {
    fun has(feature: String) = features[feature] == true
}

/** A page of posts; `cursor` asks for the next one, and is null at the end. */
@Serializable
data class FeedPage(
    val posts: List<Post>,
    val cursor: String? = null,
    /** The first page of a profile or a community: the post pinned above the list. */
    val pinned: Post? = null,
)

@Serializable
data class Post(
    val id: String,
    /** A reply in a thread that is gone but has replies under it: only the IDs and time are sent. */
    val unavailable: Boolean = false,
    val authorId: String = "",
    val authorHandle: String = "",
    /** The author's chosen name; show the handle alone when null. */
    val authorName: String? = null,
    /** Avatar path without the size suffix; see [mediaUrl]. */
    val authorAvatar: String? = null,
    val authorFounder: Boolean = false,
    val text: String = "",
    /** ISO 8601, in UTC. */
    val createdAt: String,
    val replyParentId: String? = null,
    /** 'everyone', 'following' or 'mentioned'. */
    val replyAudience: String = "everyone",
    val viewerCanReply: Boolean = true,
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val repostCount: Int = 0,
    val viewerLiked: Boolean = false,
    val viewerReposted: Boolean = false,
    val viewerBookmarked: Boolean = false,
    val pinnedOnProfile: Boolean = false,
    val pinnedInCommunity: Boolean = false,
    val deleted: Boolean = false,
    val images: List<PostImage> = emptyList(),
    /** Only sent once the video is ready to play. */
    val video: PostVideo? = null,
    val mentions: List<PostMention> = emptyList(),
    val hashtags: List<PostHashtag> = emptyList(),
    val links: List<PostLink> = emptyList(),
    val linkPreview: LinkPreview? = null,
    val poll: Poll? = null,
    val community: CommunityRef? = null,
    /** A quote post's quoted post. */
    val quote: QuotedPost? = null,
    /** What a reply answers, in lists. */
    val replyTo: ReplyContext? = null,
    /** Thread pages: whether anything answers this one. */
    val hasReplies: Boolean = false,
    /**
     * Why the post is in a feed: 'own', 'following', 'community', 'repost',
     * 'network', 'active' or 'recent'. Null in Latest.
     */
    val reason: String? = null,
    val repostedBy: Reposter? = null,
    /** In Discover, when [reason] is 'network': who the reader follows that took part. */
    val via: Via? = null,
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
    val playlists: Playlists? = null,
    val description: String = "",
)

/** HLS master playlists: every quality, and 240p to 480p. */
@Serializable
data class Playlists(val all: String, val upTo480: String)

/** Where a mention, hashtag or link sits in the text, in UTF-16 code units, like Kotlin's own indices. */
@Serializable
data class PostMention(val start: Int, val length: Int, val handle: String)

@Serializable
data class PostHashtag(val start: Int, val length: Int, val tag: String)

@Serializable
data class PostLink(val start: Int, val length: Int, val url: String)

@Serializable
data class LinkPreview(
    val url: String,
    val title: String,
    val description: String? = null,
    val siteName: String? = null,
    val image: LinkImage? = null,
)

@Serializable
data class LinkImage(val src: String, val width: Int = 0, val height: Int = 0)

@Serializable
data class Poll(
    val multiple: Boolean = false,
    val expiresAt: String? = null,
    val closed: Boolean = false,
    val totalVotes: Int = 0,
    val viewerVoted: Boolean = false,
    val options: List<PollOption> = emptyList(),
)

@Serializable
data class PollOption(
    val id: String,
    val text: String,
    val position: Int = 0,
    val voteCount: Int = 0,
    val viewerVoted: Boolean = false,
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
    val authorFounder: Boolean = false,
    val text: String = "",
    val images: List<PostImage> = emptyList(),
    val createdAt: String? = null,
)

/** What a reply answers, as lists show it; only `unavailable` when the reader cannot see it. */
@Serializable
data class ReplyContext(
    val unavailable: Boolean = false,
    val id: String? = null,
    val authorHandle: String? = null,
    val authorName: String? = null,
    val authorAvatar: String? = null,
    val excerpt: String = "",
)

@Serializable
data class Reposter(
    val id: String,
    val handle: String,
    val displayName: String? = null,
)

@Serializable
data class Via(
    val id: String,
    val handle: String,
    val displayName: String? = null,
    /** 'replied', 'quoted' or 'reposted'. */
    val action: String,
    val others: Int = 0,
)

/** A post, what it answers (oldest first) and the first page of its replies. */
@Serializable
data class ThreadPage(
    val ancestors: List<Post> = emptyList(),
    val post: Post,
    val replies: List<Post> = emptyList(),
    val replyCursor: String? = null,
    val ancestorsTruncated: Boolean = false,
)

@Serializable
data class ReplyPage(
    val replies: List<Post> = emptyList(),
    val cursor: String? = null,
)

@Serializable
data class Profile(
    val id: String,
    val handle: String,
    val displayName: String? = null,
    val bio: String = "",
    val avatar: String? = null,
    val joinedAt: String,
    val postCount: Int = 0,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val isSelf: Boolean = false,
    val viewerFollows: Boolean = false,
    val viewerBlocks: Boolean = false,
    val viewerMutes: Boolean = false,
    val identityBadge: Boolean = false,
    val founderBadge: Boolean = false,
)

@Serializable
data class Community(
    val id: String,
    val slug: String,
    val name: String,
    val description: String = "",
    val memberCount: Int = 0,
    /** Photo path without the size suffix; null for the subject icon. */
    val photo: String? = null,
    val viewerIsMember: Boolean = false,
    // Only on a community's own page.
    val rules: String = "",
    val moderators: List<String> = emptyList(),
)

@Serializable
data class SearchResults(
    val members: List<MemberSummary> = emptyList(),
    val communities: List<Community> = emptyList(),
    val posts: List<Post> = emptyList(),
)

@Serializable
data class MemberSummary(
    val handle: String,
    val displayName: String? = null,
    val avatar: String? = null,
    val founder: Boolean = false,
)

@Serializable
data class TrendingTopics(val topics: List<TrendingTopic> = emptyList())

@Serializable
data class TrendingTopic(
    val tag: String,
    /** How most of its uses spell it, without the #. */
    val label: String,
    val members: Int = 0,
    val posts: Int = 0,
)

@Serializable
data class NotificationPage(
    val notifications: List<Notification> = emptyList(),
    val cursor: String? = null,
)

@Serializable
data class Notification(
    val id: String,
    /**
     * 'reply', 'like', 'repost', 'quote', 'mention', 'follow', or a moderation
     * outcome ('post_removed', 'post_restored', 'report_resolved', ...).
     */
    val kind: String,
    val createdAt: String,
    val read: Boolean = false,
    val actor: MemberSummary? = null,
    val post: NotificationPost? = null,
    val subjectHandle: String? = null,
    val community: CommunityRef? = null,
    val detail: String? = null,
)

@Serializable
data class NotificationPost(val id: String, val excerpt: String = "", val gistId: String? = null)

/** Someone the welcome steps suggest following, and why. */
@Serializable
data class Suggestion(
    val handle: String,
    val displayName: String? = null,
    val avatar: String? = null,
    val bio: String = "",
    val reason: SuggestionReason? = null,
)

@Serializable
data class SuggestionReason(val kind: String, val community: CommunityRef? = null)

/** `state` is 'none' (with [graceDays]) or 'scheduled' (with [scheduledFor]). */
@Serializable
data class Deletion(val state: String, val graceDays: Int = 14, val scheduledFor: String? = null)

/** A short-lived URL the file is PUT to, and the upload's ID for the next steps. */
@Serializable
data class Upload(val mediaId: String, val uploadUrl: String)

@Serializable
data class UploadStatus(val status: String)

@Serializable
data class Created(val id: String)

/** Kinds of activity a member can turn off, with the words Settings shows for them. */
val NOTIFICATION_KINDS = listOf(
    "reply" to "Replies",
    "mention" to "Mentions",
    "quote" to "Quotes",
    "like" to "Likes",
    "repost" to "Reposts",
    "follow" to "New followers",
)

/** The reasons a report can give, as the web lists them (apps/web/lib/report-reasons.ts). */
val REPORT_REASONS = listOf(
    "spam" to "Spam",
    "harassment" to "Harassment or bullying",
    "hate" to "Hate speech",
    "scam" to "Scam or fraud",
    "illegal" to "Illegal content",
    "other" to "Something else",
)

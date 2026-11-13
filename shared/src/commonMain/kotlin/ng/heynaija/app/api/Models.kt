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
    /**
     * Whether they may start a Gist and speak in one, or only listen: under 18, and
     * no age on record, listen (ADR 0020 in the web repository). A server from before
     * that rule does not say, and let everyone with Gists on stage, so missing is true;
     * the API checks every one of these again whatever the app shows.
     */
    val gistStage: Boolean = true,
    /** The limits a video is checked against before sending; null while video is closed to this member. */
    val videoLimits: VideoLimits? = null,
    /** Posts marked sensitive (ADR 0020): whether they count as an adult for them, and their choices. */
    val sensitive: SensitiveSettings = SensitiveSettings(),
) {
    fun has(feature: String) = features[feature] == true

    /** May host a Gist, ask to speak and use the microphone; a member who may not only listens. */
    val mayTakeStage get() = has("gists") && gistStage

    /** May mark posts sensitive: the adultLabels flag is on for them, and they count as an adult. */
    val canMarkSensitive get() = has("adultLabels") && sensitive.adult

    /** The composer offers the mark, unless Settings already mark everything they post. */
    val composerOffersMark get() = canMarkSensitive && sensitive.markPosts == null
}

/**
 * A member's standing and choices for posts marked sensitive (ADR 0020). Only an
 * adult is ever sent such a post, or may mark one.
 */
@Serializable
data class SensitiveSettings(
    val adult: Boolean = false,
    /** Everything they post is marked: 'author' (their choice) or 'moderator' (which they cannot undo); null when not. */
    val markPosts: String? = null,
    /** Pictures and video in marked posts show without the cover. */
    val showMedia: Boolean = false,
)

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
    /** Marked sensitive (ADR 0020): 'author' or 'moderator', null when not. Only adults and its author are sent it. */
    val sensitive: String? = null,
    /** Its pictures and video wait behind a cover until the reader taps Show. The text never does. */
    val coverMedia: Boolean = false,
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
    /** A Gist reply its host hid: sent as [unavailable] when replies hang under it. */
    val hiddenByHost: Boolean = false,
    /** The story it is part of (web ADR 0024): its first post with updates, or one of them. */
    val story: PostStory? = null,
)

/** What the composer needs to add an update: the story's first post, the update's number, and the first post to show under the box. */
data class StoryDraft(val id: String, val next: Int, val start: QuotedPost?)

/** A story as each of its posts carries it: one author's updates to a post, in order. */
@Serializable
data class PostStory(
    /** The story's first post. */
    val id: String,
    /** 0 on the first post; an update's place among the live updates, from 1. */
    val number: Int = 0,
    val updates: Int = 0,
    val followers: Int = 0,
    val following: Boolean = false,
    /** On an update, the first post (only `unavailable` when gone or hidden). Null on the first post. */
    val start: QuotedPost? = null,
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
    /** Absolute on the media domain, or a `/media/…` path: see [mediaFile]. */
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
    /** Its pictures wait behind a cover (see [Post.coverMedia]). */
    val coverMedia: Boolean = false,
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
    /** On a story's first post, its newest updates, oldest first; the earlier ones are paged with storyUpdates. */
    val updates: List<Post> = emptyList(),
    val replies: List<Post> = emptyList(),
    val replyCursor: String? = null,
    val ancestorsTruncated: Boolean = false,
)

@Serializable
data class ReplyPage(
    val replies: List<Post> = emptyList(),
    val cursor: String? = null,
    /** The replies under a reply in a Gist: whether the reader hosts it. */
    val viewerIsHost: Boolean = false,
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

/** Members offered while typing an @mention, people the member follows first. */
@Serializable
data class MentionPage(val members: List<MemberSummary> = emptyList(), val cursor: String? = null)

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

/** This server's limits for a post's video (ADR 0021). */
@Serializable
data class VideoLimits(val maxBytes: Long, val maxSeconds: Int, val minSeconds: Int, val maxEdge: Int)

/** A video's upload in parts: a signed URL for each part still to send. */
@Serializable
data class VideoPlan(
    val mediaId: String,
    val partSize: Long,
    val totalParts: Int,
    val done: List<VideoPartDone> = emptyList(),
    val parts: List<VideoPart> = emptyList(),
)

@Serializable
data class VideoPart(val partNumber: Int, val url: String, val bytes: Long)

@Serializable
data class VideoPartDone(val partNumber: Int, val size: Long)

/**
 * 'uploading', 'processing', 'quarantined' (held for a safety check), 'ready',
 * 'rejected' (with a [reason]) or 'failed'.
 */
@Serializable
data class VideoStatus(val status: String, val reason: String? = null, val video: PostVideo? = null)

/** A short-lived URL the file is PUT to, and the upload's ID for the next steps. */
@Serializable
data class Upload(val mediaId: String, val uploadUrl: String)

@Serializable
data class UploadStatus(val status: String)

@Serializable
data class Created(val id: String)

// Gists (live audio rooms, ADR 0017), after apps/web/lib/gists.ts.

/** Someone in a Gist. The API sends no avatar here, so their initial stands in. */
@Serializable
data class Person(val id: String, val handle: String, val displayName: String? = null) {
    val name get() = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: "@$handle"
}

/** A co-host or speaker; [role] is 'cohost' or 'speaker'. */
@Serializable
data class GistSpeaker(val id: String, val handle: String, val displayName: String? = null, val role: String = "speaker") {
    val person get() = Person(id, handle, displayName)
}

@Serializable
data class GistCommunity(val id: String, val slug: String, val name: String)

/** [role] is 'host', 'cohost', 'speaker', 'listener', or null before joining. */
@Serializable
data class GistViewer(val role: String? = null, val requestedToSpeak: Boolean = false)

/** What `GET /v1/gists/:id` answers. [status] is 'scheduled', 'live' or 'ended'. */
@Serializable
data class GistView(
    val id: String,
    val title: String,
    val status: String,
    val community: GistCommunity? = null,
    val host: Person,
    /** Co-hosts and speakers; the host is not repeated. */
    val speakers: List<GistSpeaker> = emptyList(),
    val locked: Boolean = false,
    val requestsOpen: Boolean = true,
    val listenerCap: Int = 0,
    val listenerCount: Int = 0,
    val peakListeners: Int = 0,
    val startedAt: String? = null,
    val endedAt: String? = null,
    val viewer: GistViewer = GistViewer(),
)

/** A Gist in the Live now list (`GET /v1/gists/live`). */
@Serializable
data class LiveGist(
    val id: String,
    val title: String,
    val host: Person,
    val community: GistCommunity? = null,
    val listenerCount: Int = 0,
    val startedAt: String? = null,
)

@Serializable
data class LiveGists(val items: List<LiveGist> = emptyList())

/** A LiveKit token and server for the room, and the member's role in it. */
@Serializable
data class GistJoin(val url: String, val token: String, val role: String, val gist: GistView)

@Serializable
data class SpeakRequest(val id: String, val handle: String, val displayName: String? = null, val requestedAt: String? = null) {
    val person get() = Person(id, handle, displayName)
}

@Serializable
data class SpeakRequests(val items: List<SpeakRequest> = emptyList())

@Serializable
data class GistPeople(val items: List<Person> = emptyList())

/**
 * The top level of a Gist's replies. [replyAudience] is 'everyone', 'following',
 * 'mentioned' or 'off'.
 */
@Serializable
data class GistReplies(
    val replies: List<Post> = emptyList(),
    val cursor: String? = null,
    val count: Int = 0,
    val viewerCanReply: Boolean = false,
    val viewerIsHost: Boolean = false,
    val replyAudience: String = "everyone",
)

/** Kinds of activity a member can turn off, with the words Settings shows for them. */
val NOTIFICATION_KINDS = listOf(
    "reply" to "Replies",
    "mention" to "Mentions",
    "quote" to "Quotes",
    "like" to "Likes",
    "repost" to "Reposts",
    "follow" to "New followers",
    "story_update" to "Stories you follow",
)

/**
 * Weekly highlights (ADR 0027 in the web repository): a few conversations a member
 * missed, at most once a week, as a push notification or an email. Its key in the
 * notification preferences. The API sends it only while highlights are offered to
 * the member, so a server without them shows no switch; and it is not a kind of
 * notification ([NOTIFICATION_KINDS]), so it is not listed with those.
 */
const val HIGHLIGHTS_PREFERENCE = "highlights"

/** Whether these preferences offer weekly highlights, to be switched on or off. */
fun offersHighlights(preferences: Map<String, Boolean>) = HIGHLIGHTS_PREFERENCE in preferences

/** The reasons a report can give, as the web lists them (apps/web/lib/report-reasons.ts). */
val REPORT_REASONS = listOf(
    "spam" to "Spam",
    "harassment" to "Harassment or bullying",
    "hate" to "Hate speech",
    "scam" to "Scam or fraud",
    "illegal" to "Illegal content",
    "other" to "Something else",
)

/** Only for a post, while marking posts sensitive is switched on (the adultLabels flag); listed before "Something else". */
val NEEDS_LABEL_REASON = "needs_label" to "Sensitive, but not marked sensitive"

/**
 * `GET /v1/push/key`: the browsers' key, which the app does not use, and the push
 * services the apps are pushed through. [app] is whether Android is, from a server
 * before [appServices].
 */
@Serializable
data class PushKey(val publicKey: String? = null, val app: Boolean = false, val appServices: List<String>? = null)

/**
 * `GET /v1/signup/age`. A server from before 8 November 2026 says nothing of
 * [asksBirthDate], and asked for a date then.
 */
@Serializable
data class SignupAge(val joinAge: Int = 18, val asksBirthDate: Boolean = true)

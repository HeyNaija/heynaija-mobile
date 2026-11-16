package ng.heynaija.app.communities

import ng.heynaija.app.api.Community
import ng.heynaija.app.api.CommunityRef

/**
 * Communities where only one of HeyNaija's automated accounts starts posts, and what
 * their page says instead of a way to post. The API refuses anyone else; the web
 * keeps the same list (`apps/web/lib/chess-arena.ts`). Members reply in them as
 * anywhere else. Keep the slugs here and nowhere else in the apps.
 */
private val botOnlyCommunities = mapOf(
    // The Arbiter, HeyNaija Games.
    "chess-arena" to "Threads here are started by The Arbiter, one for each game played on HeyNaija. Reply in one to join the talk.",
    "game-night" to "Posts here are started by The Arbiter, one for each competition when it ends. Reply in one to join the talk.",
    // Keke's daily news: one post a day, with a reply from Keke for each story.
    "naija-today" to "Posts here are started by Keke, one news roundup a day. Reply under a story to join the talk.",
)

/** Whether only an automated account starts posts in the community [slug]. */
fun isBotOnly(slug: String): Boolean = slug in botOnlyCommunities

/** What the page of the community [slug] says instead of a way to post; null where members start posts. */
fun botOnlyNotice(slug: String): String? = botOnlyCommunities[slug]

/** The communities the composer offers: the ones the member joined, less those only an automated account posts in. */
fun postableCommunities(all: List<Community>): List<CommunityRef> =
    all.filter { it.viewerIsMember && !isBotOnly(it.slug) }.map { CommunityRef(it.slug, it.name) }

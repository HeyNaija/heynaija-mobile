package ng.heynaija.app.communities

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import ng.heynaija.app.api.Community
import ng.heynaija.app.api.CommunityRef

class BotOnlyCommunitiesTest {
    private fun community(slug: String, name: String, joined: Boolean = true) =
        Community(id = "c-$slug", slug = slug, name = name, viewerIsMember = joined)

    @Test
    fun theComposerOffersJoinedCommunitiesButNotTheOnesOnlyAnAutomatedAccountPostsIn() {
        val all = listOf(
            community("lagos", "Lagos"),
            community("chess-arena", "Chess Arena"),
            community("game-night", "Game Night"),
            community("naija-today", "Naija Today"),
            community("abuja", "Abuja", joined = false),
            community("tech", "Tech"),
        )

        assertEquals(listOf(CommunityRef("lagos", "Lagos"), CommunityRef("tech", "Tech")), postableCommunities(all))
    }

    @Test
    fun theArbitersAndKekesCommunitiesSayWhoStartsTheirPostsAndThatMembersCanReply() {
        for (slug in listOf("chess-arena", "game-night", "naija-today")) {
            assertTrue(isBotOnly(slug), slug)
            val notice = assertNotNull(botOnlyNotice(slug), slug)
            assertTrue(notice.startsWith("Threads here are started by") || notice.startsWith("Posts here are started by"), notice)
            assertTrue("Reply" in notice, notice)
        }
        assertTrue("Keke" in botOnlyNotice("naija-today")!!)
        assertTrue("The Arbiter" in botOnlyNotice("chess-arena")!!)
    }

    @Test
    fun anyOtherCommunityTakesPostsFromItsMembers() {
        for (slug in listOf("lagos", "chess", "naija", "naija-today-2", "")) {
            assertFalse(isBotOnly(slug), slug)
            assertNull(botOnlyNotice(slug), slug)
        }
    }
}

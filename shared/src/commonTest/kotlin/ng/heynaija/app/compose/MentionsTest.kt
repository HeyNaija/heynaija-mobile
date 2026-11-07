package ng.heynaija.app.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MentionsTest {
    @Test
    fun aMentionIsTheHandleTypedSoFar() {
        assertEquals("ad", mentionAt("Hi @ad", 6))
        assertEquals("ad", mentionAt("@ad", 3))
        assertEquals("a_1", mentionAt("Hi\n@a_1", 7))
    }

    @Test
    fun anEmailOrABareAtIsNotAMention() {
        assertNull(mentionAt("me@ada", 6))
        assertNull(mentionAt("Hi @", 4))
        assertNull(mentionAt("Hi @ada now", 11))
    }

    @Test
    fun choosingAHandleCompletesItAndMovesPastTheSpace() {
        assertEquals("Hi @adaeze " to 11, insertMention("Hi @ad", 6, "adaeze"))
        assertEquals("@adaeze and you" to 8, insertMention("@ad and you", 3, "adaeze"))
        assertEquals("plain" to 2, insertMention("plain", 2, "adaeze"))
    }
}

package ng.heynaija.app.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class SealedPushTest {
    // Made by the API's own seal() (apps/api/src/push/fcm-sender.ts) with a key of 32 sevens.
    private val key = "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwc"
    private val sealed =
        "12RSghH5jY-7rcmE1QYAX1udTGel4N44NhbwyHCDPNcV10EK3PaPvsTOD2YzlCGJfjMSZG1gkVFMb78RYsh0YoRBgRH7d6-n_IuFXs1DCpp16dWjF2mbYswmrQ-pXZaQ8HHvus3u3Ntr0vKQk0gJLz4ebAuAPcQmXQc5hfcjUrcQCz0Mj0OD7BpzinUcAbGbViwuRTorcIvPELziLIcq6IH5pulaGZu5OVAtjMix7ieCsC_qkhkTVa1a0OOHosqdan7CzkBMcMT8h-e72ETLTm7XrGsoxB00Hp2hjwOcC2i-fqpH8MnAyZBbA8NrcY75-a1b3wvi"

    @Test
    fun opensWhatTheServerSealed() {
        assertEquals(
            PushNote(
                title = "@bayo replied to your post",
                body = "“Jollof, obviously”",
                url = "/p/0b5f0c1e-6d0f-4a43-9b8e-1b9a2b3c4d5e",
                tag = "reply:0b5f0c1e-6d0f-4a43-9b8e-1b9a2b3c4d5e",
                member = "3f1d2c4b-5a69-4788-9900-aabbccddeeff",
            ),
            SealedPush.open(key, sealed),
        )
    }

    @Test
    fun refusesAnotherKeyOrAChangedMessage() {
        assertNull(SealedPush.open(SealedPush.newKey(), sealed))
        val changed = sealed.substring(0, 20) + (if (sealed[20] == 'A') 'B' else 'A') + sealed.substring(21)
        assertNull(SealedPush.open(key, changed))
        assertNull(SealedPush.open(key, "not sealed"))
    }

    @Test
    fun newKeysAre32RandomBytes() {
        val a = SealedPush.newKey()
        assertEquals(43, a.length)
        assertNotEquals(a, SealedPush.newKey())
    }
}

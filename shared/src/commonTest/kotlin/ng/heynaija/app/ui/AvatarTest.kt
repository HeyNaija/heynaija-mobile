package ng.heynaija.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class AvatarTest {
    @Test
    fun aHandleGetsTheSameTintAsOnTheWeb() {
        // Worked out with apps/web/app/ui/avatar.tsx's own loop.
        val web = mapOf("ada" to 6, "psami" to 2, "chinedu_okafor" to 7, "zz9" to 1, "a" to 1)
        for ((handle, tone) in web) assertEquals(tone, toneFor(handle, 8), handle)
    }
}

package ng.heynaija.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfficialBadgeTest {
    @Test
    fun aboutThisAccountLeadsToKekesPageOnTheServerInUse() {
        assertEquals("https://heynaija.ng/about/keke", officialAboutPage("keke", "https://heynaija.ng"))
        assertEquals("https://staging.heynaija.ng/about/keke", officialAboutPage("keke_hn", "https://staging.heynaija.ng"))
    }

    @Test
    fun aboutThisAccountLeadsToAnyOtherAccountsProfile() {
        for (handle in listOf("arbiter", "keke2", "kekee", "Keke_hn_bot")) assertNull(officialAboutPage(handle, "https://heynaija.ng"), handle)
    }
}

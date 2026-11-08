package ng.heynaija.app.platform

import com.google.android.play.agesignals.model.AgeSignalsStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreAgeTest {
    @Test
    fun playsBandsSayAdultOrMinor() {
        assertEquals(StoreAge.Adult, storeAgeOf(AgeSignalsStatus.SHARED, 18, null))
        assertEquals(StoreAge.Minor, storeAgeOf(AgeSignalsStatus.SHARED, 13, 15))
        assertEquals(StoreAge.Minor, storeAgeOf(AgeSignalsStatus.SHARED, 16, 17))
        // Shared, but no band: nothing to go on, so the app's own question stands.
        assertEquals(StoreAge.Unchecked, storeAgeOf(AgeSignalsStatus.SHARED, null, null))
    }

    @Test
    fun aLawThatPlayCannotAnswerYetStopsSigningIn() {
        assertEquals(StoreAge.Unconfirmed, storeAgeOf(AgeSignalsStatus.VERIFICATION_REQUIRED, null, null))
    }

    @Test
    fun notSharedWhereNothingAsksIsThePersonsChoice() {
        assertEquals(StoreAge.Unchecked, storeAgeOf(AgeSignalsStatus.NOT_SHARED, null, null))
        assertEquals(StoreAge.Unchecked, storeAgeOf(AgeSignalsStatus.UNSPECIFIED, null, null))
        assertEquals(StoreAge.Unchecked, storeAgeOf(null, null, null))
    }

    @Test
    fun onlyAnAdultOrNoAnswerLetsThemIn() {
        assertNull(storeAgeRefusal(StoreAge.Adult, "Google Play"))
        assertNull(storeAgeRefusal(StoreAge.Unchecked, "Apple"))
        assertTrue(storeAgeRefusal(StoreAge.Minor, "Apple")!!.contains("Apple says you are under 18"))
        assertTrue(storeAgeRefusal(StoreAge.Unconfirmed, "Google Play")!!.contains("confirm with Google Play"))
    }
}

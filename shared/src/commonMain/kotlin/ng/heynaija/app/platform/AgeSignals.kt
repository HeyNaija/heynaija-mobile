package ng.heynaija.app.platform

import androidx.compose.runtime.Composable

/**
 * What the phone's app store says about the person's age. Some places' laws
 * (Texas's, so far, with Utah's and Louisiana's to follow) make an 18+ app check
 * age with the store's own signal rather than a box the person ticks: Apple's
 * Declared Age Range on iOS 26.2 and later, Google Play's Age Signals on Android.
 */
enum class StoreAge {
    /** The store says 18 or older. */
    Adult,

    /** The store says under 18. */
    Minor,

    /** The law asks for the store's answer here, and there is none: the person did not share it, or has not confirmed their age with the store. */
    Unconfirmed,

    /**
     * Nothing asks for it here (no such law, an older system, no app store), or the
     * store could not answer: the app's own "I am 18 or older" stands, as everywhere else.
     */
    Unchecked,
}

/** Asks the app store about the person's age, before they sign in or join. */
interface AgeSignals {
    /** The store's name, as the person knows it: "Apple", "Google Play". */
    val store: String

    suspend fun check(): StoreAge
}

/**
 * Why someone may not sign in or join, from what [store] said: null when they may.
 * HeyNaija is for adults while it is in beta, as the web's /join says.
 */
fun storeAgeRefusal(age: StoreAge, store: String): String? = when (age) {
    StoreAge.Adult, StoreAge.Unchecked -> null
    StoreAge.Minor -> "HeyNaija is for adults while it is in beta, and $store says you are under 18."
    StoreAge.Unconfirmed ->
        "Where you live, the law asks HeyNaija to confirm with $store that you are 18 or older. " +
            "Share your age range with HeyNaija when $store asks, or confirm your age with $store, then try again."
}

/** The store's age check where this phone has one; null where it has none (tests). */
@Composable
expect fun rememberAgeSignals(): AgeSignals?

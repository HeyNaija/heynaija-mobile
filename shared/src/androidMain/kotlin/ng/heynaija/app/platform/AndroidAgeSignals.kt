package ng.heynaija.app.platform

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.google.android.gms.tasks.Task
import com.google.android.play.agesignals.AgeSignalsAccessRequest
import com.google.android.play.agesignals.AgeSignalsException
import com.google.android.play.agesignals.AgeSignalsManagerFactory
import com.google.android.play.agesignals.AgeSignalsRequest
import com.google.android.play.agesignals.model.AgeSignalsStatus
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

@Composable
actual fun rememberAgeSignals(): AgeSignals? {
    val activity = LocalActivity.current ?: return null
    return remember(activity) { PlayAgeSignals(activity) }
}

/**
 * Google Play's Age Signals. Play first asks whether the person shares their age
 * with the app: where a law asks for it, Play shares it, or says the person has to
 * confirm their age with Play first; elsewhere the person's own setting decides,
 * and Play may ask them. Shared, it gives an age band.
 */
private class PlayAgeSignals(private val activity: Activity) : AgeSignals {
    override val store = "Google Play"

    override suspend fun check(): StoreAge = try {
        val manager = AgeSignalsManagerFactory.create(activity)
        val access = manager.requestAgeSignalsAccess(AgeSignalsAccessRequest.builder().setActivity(activity).build()).await()
        val result = if (access.ageSignalsStatus() == AgeSignalsStatus.SHARED) manager.checkAgeSignals(AgeSignalsRequest.builder().build()).await() else null
        storeAgeOf(access.ageSignalsStatus(), result?.ageLower(), result?.ageUpper())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Not installed from Play, an old Play Store, offline: Play cannot answer, so the app's own question stands.
        println("HeyNaija: Play's age signals failed: ${(e as? AgeSignalsException)?.errorCode ?: e.message}")
        StoreAge.Unchecked
    }
}

/**
 * What Play's answer means for an 18+ app: whether the person shares their age at
 * all ([access], an AgeSignalsStatus), then the band shared, from [lower] to
 * [upper] (no upper bound past 18).
 */
internal fun storeAgeOf(access: Int?, lower: Int?, upper: Int?): StoreAge = when (access) {
    // A law asks for it here, and Play does not know the age: the person confirms it with Play.
    AgeSignalsStatus.VERIFICATION_REQUIRED -> StoreAge.Unconfirmed
    AgeSignalsStatus.SHARED -> when {
        lower != null && lower >= 18 -> StoreAge.Adult
        upper != null && upper < 18 -> StoreAge.Minor
        else -> StoreAge.Unchecked
    }
    // Not shared where nothing requires it: the person's choice, and the app's own question stands.
    else -> StoreAge.Unchecked
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        val error = task.exception
        if (error == null && task.isSuccessful) continuation.resume(task.result)
        else continuation.resumeWithException(error ?: IllegalStateException("Play did not answer"))
    }
}

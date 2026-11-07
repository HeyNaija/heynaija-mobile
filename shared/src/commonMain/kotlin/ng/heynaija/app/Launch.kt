package ng.heynaija.app

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ng.heynaija.app.api.ApiException

/**
 * Runs [block], and says [failure] in the snackbar if it throws: or the API's own
 * words, when it gave a reason by name.
 */
fun CoroutineScope.launchCatching(app: AppState, failure: String, block: suspend () -> Unit): Job = launch {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        app.say(reasonOf(e) ?: failure)
    }
}

/** The API's words for a refusal it named, fit to show; null for anything else. */
fun reasonOf(e: Exception): String? = (e as? ApiException)?.takeIf { it.code != null && it.status in 400..499 }?.message

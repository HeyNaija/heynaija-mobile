package ng.heynaija.app.gists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ng.heynaija.app.AppState
import ng.heynaija.app.api.ApiException
import ng.heynaija.app.api.HeyNaijaApi
import ng.heynaija.app.api.LiveGist
import ng.heynaija.app.reasonOf

/** The Live now list on Home, asked for again every 30 seconds while Home is on screen. */
class LiveNowViewModel(private val api: HeyNaijaApi) : ViewModel() {
    private val _items = MutableStateFlow<List<LiveGist>>(emptyList())
    val items: StateFlow<List<LiveGist>> = _items.asStateFlow()
    private var job: Job? = null

    fun start() {
        job?.cancel()
        job = viewModelScope.launch {
            while (true) {
                // A failed ask keeps what is shown; the next one tries again.
                runCatching { api.liveGists() }.onSuccess { _items.value = it }
                delay(LIVE_POLL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }
}

data class StartGistState(
    val title: String = "",
    val error: String? = null,
    val busy: Boolean = false,
    /** The member's own live Gist, when they tried to start a second. */
    val hosting: String? = null,
)

class StartGistViewModel(private val app: AppState) : ViewModel() {
    private val _state = MutableStateFlow(StartGistState())
    val state: StateFlow<StartGistState> = _state.asStateFlow()

    fun setTitle(title: String) = _state.update { it.copy(title = title.take(TITLE_MAX), error = null) }

    fun start(onStarted: (id: String) -> Unit) {
        val s = _state.value
        if (s.busy) return
        titleProblem(s.title)?.let { problem -> return _state.update { it.copy(error = problem) } }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val gist = app.api.startGist(s.title.trim())
                _state.value = StartGistState()
                onStarted(gist.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The API names the Gist already live in its answer; the list of live ones finds it here.
                val hosting = if ((e as? ApiException)?.code == "already_hosting") {
                    runCatching { app.api.liveGists().firstOrNull { it.host.id == app.me?.id }?.id }.getOrNull()
                } else null
                _state.update { it.copy(busy = false, error = reasonOf(e) ?: "Could not start the Gist. Try again.", hosting = hosting) }
            }
        }
    }
}

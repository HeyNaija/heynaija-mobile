package ng.heynaija.app.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ng.heynaija.app.api.HeyNaijaApi
import ng.heynaija.app.api.Post

data class FeedState(
    val posts: List<Post> = emptyList(),
    /** Where the next page starts; null once the last page is in. */
    val cursor: String? = null,
    /** The first page has arrived. */
    val loaded: Boolean = false,
    /** A page is on its way: the first, or the next. */
    val loading: Boolean = false,
    /** Pulled down to start again from the newest. */
    val refreshing: Boolean = false,
    /** The last request failed; [FeedViewModel.retry] tries it again. */
    val failed: Boolean = false,
) {
    val atEnd get() = loaded && cursor == null
}

/** The Latest feed, a page at a time. */
class FeedViewModel(private val api: HeyNaijaApi) : ViewModel() {
    private val _state = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = _state.asStateFlow()

    private var job: Job? = null
    private var lastWasFresh = true

    init {
        load(fresh = true)
    }

    fun refresh() = load(fresh = true, pulled = true)

    /** The next page, unless one is on its way, the last one failed, or there is none. */
    fun loadMore() {
        val state = _state.value
        if (job?.isActive == true || !state.loaded || state.failed || state.cursor == null) return
        load(fresh = false)
    }

    fun retry() = load(fresh = lastWasFresh)

    private fun load(fresh: Boolean, pulled: Boolean = false) {
        // Starting again from the newest replaces whatever was on its way.
        if (fresh) job?.cancel() else if (job?.isActive == true) return
        lastWasFresh = fresh
        val cursor = if (fresh) null else _state.value.cursor
        _state.update { it.copy(loading = !pulled, refreshing = pulled, failed = false) }
        job = viewModelScope.launch {
            try {
                val page = api.latest(cursor)
                _state.update {
                    // A post can move onto the next page while we read; show it once.
                    val posts = if (fresh) page.posts else (it.posts + page.posts).distinctBy(Post::id)
                    FeedState(posts = posts, cursor = page.cursor, loaded = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Logcat on Android, the Xcode console on iOS.
                println("HeyNaija: Latest could not be loaded: $e")
                _state.update { it.copy(loading = false, refreshing = false, failed = true) }
            }
        }
    }
}

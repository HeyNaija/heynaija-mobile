package ng.heynaija.app.posts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ng.heynaija.app.PostChange
import ng.heynaija.app.api.FeedPage
import ng.heynaija.app.api.Post

data class PostListState(
    val posts: List<Post> = emptyList(),
    /** Pinned above a profile's or a community's list. */
    val pinned: Post? = null,
    /** Where the next page starts; null once the last page is in. */
    val cursor: String? = null,
    /** The first page has arrived. */
    val loaded: Boolean = false,
    /** A page is on its way: the first, or the next. */
    val loading: Boolean = false,
    /** Pulled down to start again from the newest. */
    val refreshing: Boolean = false,
    /** The last request failed; [PostListViewModel.retry] tries it again. */
    val failed: Boolean = false,
) {
    val atEnd get() = loaded && cursor == null
}

/**
 * Any list of posts, a page at a time: a feed, a profile, a community, a
 * hashtag, bookmarks. It takes up [changes] (a like, a delete) made anywhere in
 * the app, and starts again when the member posts if [refreshOnPost].
 */
class PostListViewModel(
    private val load: suspend (cursor: String?) -> FeedPage,
    changes: Flow<PostChange> = emptyFlow(),
    private val refreshOnPost: Boolean = false,
    /** Bookmarks drop a post that is no longer bookmarked. */
    private val keep: (Post) -> Boolean = { true },
) : ViewModel() {
    private val _state = MutableStateFlow(PostListState())
    val state: StateFlow<PostListState> = _state.asStateFlow()

    private var job: Job? = null
    private var lastWasFresh = true

    init {
        load(fresh = true)
        viewModelScope.launch {
            changes.collect { change ->
                when (change) {
                    is PostChange.Updated -> _state.update { state ->
                        fun Post.updated() = if (id == change.post.id) change.post else this
                        state.copy(posts = state.posts.map { it.updated() }.filter(keep), pinned = state.pinned?.updated())
                    }
                    is PostChange.Deleted -> _state.update { state ->
                        state.copy(posts = state.posts.filterNot { it.id == change.id }, pinned = state.pinned?.takeUnless { it.id == change.id })
                    }
                    is PostChange.Created -> if (refreshOnPost) load(fresh = true)
                }
            }
        }
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
                val page = load(cursor)
                _state.update {
                    // A post can move onto the next page while we read; show it once.
                    val posts = if (fresh) page.posts else (it.posts + page.posts).distinctBy(Post::id)
                    PostListState(
                        posts = posts,
                        pinned = if (fresh) page.pinned else it.pinned,
                        cursor = page.cursor,
                        loaded = true,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Logcat on Android, the Xcode console on iOS.
                println("HeyNaija: posts could not be loaded: $e")
                _state.update { it.copy(loading = false, refreshing = false, failed = true) }
            }
        }
    }
}

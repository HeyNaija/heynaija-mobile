package ng.heynaija.app.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import ng.heynaija.app.api.CommunityRef
import ng.heynaija.app.api.Post
import ng.heynaija.app.api.PostImage

/** A screen, with what it needs to open. */
sealed interface Route {
    data object Home : Route
    data object Search : Route
    data object Communities : Route
    data object Notifications : Route
    /** The member's own profile, or a prompt to sign in. */
    data object Me : Route

    /**
     * A post and its conversation; [post] shows at once while the rest loads. With
     * [gist], a reply in that Gist and the replies under it.
     */
    data class Thread(val id: String, val post: Post? = null, val gist: String? = null) : Route
    data class Profile(val handle: String) : Route
    data class Community(val slug: String) : Route
    data class Hashtag(val tag: String) : Route
    data object Bookmarks : Route
    data object Settings : Route
    data object EditProfile : Route
    data object NotificationSettings : Route
    data object DeleteAccount : Route
    /** A live audio room: who is on stage, joining and speaking, and its replies. */
    data class Gist(val id: String) : Route

    // Full screen, without the tab bar.
    /** A new post, reply or quote; with [gist], a reply in that Gist (to [replyTo], or to the Gist itself). */
    data class Compose(val replyTo: Post? = null, val quote: Post? = null, val community: CommunityRef? = null, val gist: String? = null) : Route
    data class Images(val images: List<PostImage>, val start: Int) : Route
    data class Video(val url: String) : Route
    /** [target] is `"postId" to id` or `"handle" to handle`. */
    data class Report(val target: Pair<String, String>, val what: String) : Route
    data object SignIn : Route
    data object SignUp : Route

    val fullScreen: Boolean
        get() = this is Compose || this is Images || this is Video || this is Report || this is SignIn || this is SignUp
}

enum class Motion { Push, Pop, Tab }

enum class Tab(val root: Route) {
    Home(Route.Home),
    Search(Route.Search),
    Communities(Route.Communities),
    Notifications(Route.Notifications),
    Me(Route.Me),
}

/** A route on a stack, with the view models its screen made; they go when it is popped. */
class Entry(val route: Route) : ViewModelStoreOwner {
    val id = next++
    override val viewModelStore = ViewModelStore()

    private companion object {
        var next = 0L
    }
}

/**
 * A back stack per tab, like the apps people know: switching tabs keeps each
 * one where it was, and pressing the current tab again goes back to its top.
 */
class Navigator {
    var tab by mutableStateOf(Tab.Home)
        private set

    private val stacks: Map<Tab, SnapshotStateList<Entry>> = Tab.entries.associateWith { mutableStateListOf(Entry(it.root)) }

    val stack: List<Entry> get() = stacks.getValue(tab)
    val top: Entry get() = stack.last()

    /** How the last change moved, so the screen change can slide or fade to match. */
    var motion by mutableStateOf(Motion.Tab)
        private set

    /** Bumped when the current tab is pressed at its root: its list scrolls to the top. */
    var reselected by mutableIntStateOf(0)
        private set

    val canPop: Boolean get() = stack.size > 1 || tab != Tab.Home

    fun push(route: Route) {
        motion = Motion.Push
        stacks.getValue(tab).add(Entry(route))
    }

    /** Replaces the screen on top, as signing in replaces its form with the app. */
    fun replace(route: Route) {
        val stack = stacks.getValue(tab)
        stack.removeAt(stack.lastIndex).viewModelStore.clear()
        motion = Motion.Push
        stack.add(Entry(route))
    }

    fun pop(): Boolean {
        val stack = stacks.getValue(tab)
        if (stack.size > 1) {
            motion = Motion.Pop
            stack.removeAt(stack.lastIndex).viewModelStore.clear()
            return true
        }
        if (tab != Tab.Home) {
            motion = Motion.Tab
            tab = Tab.Home
            return true
        }
        return false
    }

    /**
     * Opens [route] from outside the app, from a tapped notification: a tab's own
     * screen as that tab, anything else over the Notifications tab, so that Back
     * leads to the list it came from.
     */
    fun openFromNotification(route: Route) {
        val root = Tab.entries.firstOrNull { it.root == route }
        val target = root ?: Tab.Notifications
        if (target != tab) {
            motion = Motion.Tab
            tab = target
        }
        if (root == null) push(route)
    }

    fun select(target: Tab) {
        if (target == tab) {
            val stack = stacks.getValue(tab)
            if (stack.size > 1) {
                motion = Motion.Pop
                while (stack.size > 1) stack.removeAt(stack.lastIndex).viewModelStore.clear()
            } else {
                reselected++
            }
            return
        }
        motion = Motion.Tab
        tab = target
    }

    /** Every tab back at its root: after signing in or out. */
    fun reset() {
        for ((t, stack) in stacks) {
            stack.forEach { it.viewModelStore.clear() }
            stack.clear()
            stack.add(Entry(t.root))
        }
        tab = Tab.Home
        motion = Motion.Tab
    }
}

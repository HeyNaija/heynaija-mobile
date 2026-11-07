package ng.heynaija.app.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ng.heynaija.app.AppState
import ng.heynaija.app.LocalApp
import ng.heynaija.app.api.ApiException
import ng.heynaija.app.api.Profile
import ng.heynaija.app.launchCatching
import ng.heynaija.app.nav.Route
import ng.heynaija.app.posts.PostList
import ng.heynaija.app.posts.PostListViewModel
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii
import ng.heynaija.app.ui.Avatar
import ng.heynaija.app.ui.Card
import ng.heynaija.app.ui.Confirm
import ng.heynaija.app.ui.FounderBadge
import ng.heynaija.app.ui.Icons
import ng.heynaija.app.ui.Loading
import ng.heynaija.app.ui.PrimaryButton
import ng.heynaija.app.ui.Problem
import ng.heynaija.app.ui.SecondaryButton
import ng.heynaija.app.ui.TopBar
import ng.heynaija.app.ui.joined
import ng.heynaija.app.ui.plural

data class ProfileState(val profile: Profile? = null, val failed: Boolean = false, val missing: Boolean = false)

class ProfileViewModel(private val app: AppState, private val handle: String) : ViewModel() {
    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(failed = false) }
        viewModelScope.launch {
            try {
                _state.value = ProfileState(app.api.profile(handle))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _state.value = ProfileState(failed = e.status != 404, missing = e.status == 404)
            } catch (e: Exception) {
                _state.value = ProfileState(failed = true)
            }
        }
    }

    private fun change(update: (Profile) -> Profile, failure: String, call: suspend () -> Unit) {
        val before = _state.value.profile ?: return
        _state.update { it.copy(profile = update(before)) }
        viewModelScope.launchCatching(app, failure) {
            try {
                call()
            } catch (e: Exception) {
                _state.update { it.copy(profile = before) }
                throw e
            }
        }
    }

    fun toggleFollow() {
        val p = _state.value.profile ?: return
        val on = !p.viewerFollows
        change({ it.copy(viewerFollows = on, followerCount = it.followerCount + if (on) 1 else -1) }, "That did not go through.") {
            app.api.setFollow(handle, on)
        }
    }

    fun setMute(on: Boolean) = change({ it.copy(viewerMutes = on) }, "That did not go through.") {
        app.api.setMute(handle, on)
        app.say(if (on) "Muted @$handle" else "Unmuted @$handle")
    }

    fun setBlock(on: Boolean) = change({ it.copy(viewerBlocks = on, viewerFollows = false) }, "That did not go through.") {
        app.api.setBlock(handle, on)
        app.say(if (on) "Blocked @$handle" else "Unblocked @$handle")
    }
}

private enum class Filter(val key: String, val label: String) {
    Posts("posts", "Posts"),
    Replies("replies", "Replies"),
    Media("media", "Media"),
}

/** The member's own profile under the Profile tab, or a way in for a visitor. */
@Composable
fun MeScreen() {
    val app = LocalApp.current
    val me = app.me
    if (me != null) {
        ProfileScreen(me.handle, root = true)
        return
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("Profile")
        Column(Modifier.padding(16.dp)) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your profile lives here", style = MaterialTheme.typography.titleMedium, color = HeyNaija.colors.ink)
                    Text(
                        "Sign in to post, reply, follow people and join communities.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HeyNaija.colors.muted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton("Sign in", { app.navigator.push(Route.SignIn) }, Modifier.weight(1f))
                        SecondaryButton("Join with an invite", { app.navigator.push(Route.SignUp) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(handle: String, root: Boolean = false) {
    val app = LocalApp.current
    val viewModel = viewModel(key = "profile-$handle") { ProfileViewModel(app, handle) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(Filter.Posts) }
    val lists = remember { mutableMapOf<Filter, LazyListState>() }
    val profile = state.profile

    Column(Modifier.fillMaxSize()) {
        TopBar(
            profile?.displayName ?: "@$handle",
            onBack = if (root) null else ({ app.navigator.pop() }),
            actions = {
                if (profile?.isSelf == true) {
                    IconButton(onClick = { app.navigator.push(Route.Settings) }) { Icon(Icons.Gear, "Settings", Modifier.size(22.dp), tint = HeyNaija.colors.muted) }
                } else if (profile != null && app.me != null) {
                    ProfileMenu(profile, viewModel)
                }
            },
        )
        when {
            state.missing -> Problem("There is no member called @$handle.", onRetry = null)
            profile == null && state.failed -> Problem("This profile could not be loaded. Check your connection and try again.", viewModel::load)
            profile == null -> Loading()
            profile.viewerBlocks -> Column(Modifier.padding(16.dp)) {
                Header(profile, viewModel)
                Spacer(Modifier.height(16.dp))
                Text("You blocked @$handle. You do not see each other's posts.", style = MaterialTheme.typography.bodyMedium, color = HeyNaija.colors.muted)
                Spacer(Modifier.height(12.dp))
                SecondaryButton("Unblock", { viewModel.setBlock(false) })
            }
            else -> key(filter) {
                val posts = viewModel(key = "profile-$handle-${filter.key}") {
                    PostListViewModel(
                        load = { cursor -> app.api.memberPosts(handle, filter.key, cursor) },
                        changes = app.changes,
                        refreshOnPost = profile.isSelf,
                    )
                }
                PostList(
                    posts,
                    list = lists.getOrPut(filter) { LazyListState() },
                    empty = when (filter) {
                        Filter.Posts -> if (profile.isSelf) "You have not posted yet." else "@$handle has not posted yet."
                        Filter.Replies -> "No replies yet."
                        Filter.Media -> "No pictures or videos yet."
                    },
                ) {
                    item(key = "header") { Header(profile, viewModel) }
                    item(key = "filters") { Filters(filter) { filter = it } }
                }
            }
        }
    }
}

@Composable
private fun Header(profile: Profile, viewModel: ProfileViewModel) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Avatar(profile.handle, profile.avatar, 72.dp)
                Spacer(Modifier.weight(1f))
                when {
                    profile.isSelf -> SecondaryButton("Edit profile", { app.navigator.push(Route.EditProfile) })
                    profile.viewerBlocks -> {}
                    profile.viewerFollows -> SecondaryButton("Following", { viewModel.toggleFollow() })
                    else -> PrimaryButton("Follow", { app.requireMember { viewModel.toggleFollow() } })
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile.displayName ?: "@${profile.handle}",
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.88f),
                    color = colors.ink,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (profile.identityBadge) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Verified, "Identity verified", Modifier.size(20.dp), tint = colors.accentStrong)
                }
                if (profile.founderBadge) {
                    Spacer(Modifier.width(6.dp))
                    FounderBadge(size = 18.dp)
                }
            }
            if (profile.displayName != null) Text("@${profile.handle}", style = MaterialTheme.typography.labelSmall, color = colors.muted)
            if (profile.bio.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(profile.bio, style = MaterialTheme.typography.bodyLarge, color = colors.ink)
            }
            Spacer(Modifier.height(10.dp))
            Text(joined(Instant.parse(profile.joinedAt)), style = MaterialTheme.typography.labelSmall, color = colors.muted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Count(profile.postCount, "post")
                Count(profile.followerCount, "follower")
                Count(profile.followingCount, "following", plural = false)
            }
            if (profile.viewerMutes) {
                Spacer(Modifier.height(10.dp))
                Text("You muted @${profile.handle}.", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
        }
    }
}

@Composable
private fun Count(count: Int, word: String, plural: Boolean = true) {
    val colors = HeyNaija.colors
    Row {
        Text("$count", style = MaterialTheme.typography.labelLarge, color = colors.ink)
        Spacer(Modifier.width(4.dp))
        Text(if (plural) plural(count, word).substringAfter(' ') else word, style = MaterialTheme.typography.labelSmall, color = colors.muted)
    }
}

@Composable
private fun Filters(current: Filter, onSelect: (Filter) -> Unit) {
    val colors = HeyNaija.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (filter in Filter.entries) {
            val selected = filter == current
            Text(
                filter.label,
                Modifier
                    .background(if (selected) colors.accentSoft else colors.surface, Radii.control)
                    .clickable { onSelect(filter) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) colors.accentStrong else colors.muted,
            )
        }
    }
}

@Composable
private fun ProfileMenu(profile: Profile, viewModel: ProfileViewModel) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    var open by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Dots, "More", Modifier.size(22.dp), tint = colors.muted) }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(if (profile.viewerMutes) "Unmute @${profile.handle}" else "Mute @${profile.handle}") },
                onClick = { open = false; viewModel.setMute(!profile.viewerMutes) },
            )
            DropdownMenuItem(
                text = { Text(if (profile.viewerBlocks) "Unblock @${profile.handle}" else "Block @${profile.handle}", color = colors.danger) },
                onClick = { open = false; if (profile.viewerBlocks) viewModel.setBlock(false) else confirmBlock = true },
            )
            HorizontalDivider(color = colors.line)
            DropdownMenuItem(
                text = { Text("Report @${profile.handle}") },
                onClick = { open = false; app.navigator.push(Route.Report("handle" to profile.handle, "@${profile.handle}")) },
            )
        }
    }
    if (confirmBlock) {
        Confirm(
            title = "Block @${profile.handle}?",
            text = "You will stop seeing each other's posts, and they cannot follow you.",
            action = "Block",
            onConfirm = { confirmBlock = false; viewModel.setBlock(true) },
            onDismiss = { confirmBlock = false },
        )
    }
}

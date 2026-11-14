package ng.heynaija.app.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ng.heynaija.app.AppState
import ng.heynaija.app.LocalApp
import ng.heynaija.app.api.Community
import ng.heynaija.app.api.Suggestion
import ng.heynaija.app.communities.CommunityMark
import ng.heynaija.app.reasonOf
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii
import ng.heynaija.app.ui.Avatar
import ng.heynaija.app.ui.CommunitiesSkeletonList
import ng.heynaija.app.ui.Icons
import ng.heynaija.app.ui.Loading
import ng.heynaija.app.ui.NotificationsSkeletonList
import ng.heynaija.app.ui.PrimaryButton
import ng.heynaija.app.ui.SecondaryButton
import ng.heynaija.app.ui.plural

data class WelcomeState(
    val step: Int = 1,
    val communities: List<Community> = emptyList(),
    val chosen: Set<String> = emptySet(),
    val suggestions: List<Suggestion>? = null,
    val follow: Set<String> = emptySet(),
    val busy: Boolean = false,
    val error: String? = null,
)

/** The welcome steps a newcomer goes through once (OnboardingService in the API). */
class WelcomeViewModel(private val app: AppState) : ViewModel() {
    private val _state = MutableStateFlow(WelcomeState())
    val state: StateFlow<WelcomeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { app.api.communities() }.onSuccess { all -> _state.update { it.copy(communities = all) } }
        }
    }

    fun toggleCommunity(slug: String) = _state.update { s -> s.copy(chosen = if (slug in s.chosen) s.chosen - slug else s.chosen + slug) }

    fun toggleFollow(handle: String) = _state.update { s -> s.copy(follow = if (handle in s.follow) s.follow - handle else s.follow + handle) }

    fun toPeople() {
        _state.update { it.copy(step = 2, suggestions = null) }
        viewModelScope.launch {
            val suggestions = runCatching { app.api.suggestions(_state.value.chosen.toList()) }.getOrDefault(emptyList())
            _state.update { it.copy(suggestions = suggestions) }
        }
    }

    fun back() = _state.update { it.copy(step = 1) }

    fun finish(skip: Boolean) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val s = _state.value
                if (skip) app.api.skipOnboarding() else app.api.completeOnboarding(s.chosen.toList(), s.follow.toList())
                app.refreshMe()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = reasonOf(e) ?: "That did not save. Check your connection and try again.") }
            }
        }
    }
}

@Composable
fun WelcomeScreen() {
    val app = LocalApp.current
    val viewModel = viewModel(key = "welcome") { WelcomeViewModel(app) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = HeyNaija.colors

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Step ${state.step} of 2", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = colors.accentStrong)
            Text("Skip", Modifier.clickable(enabled = !state.busy) { viewModel.finish(skip = true) }.padding(8.dp), style = MaterialTheme.typography.labelLarge, color = colors.muted)
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                if (state.step == 1) "Welcome to HeyNaija" else "People to follow",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.ink,
            )
            Text(
                if (state.step == 1) "Choose the communities you care about. Their posts come to your Following feed, and you can leave any time."
                else "Some people in the communities you chose. Follow anyone you like; you can unfollow later.",
                Modifier.padding(top = 6.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        }
        Box(Modifier.weight(1f)) {
            if (state.step == 1) {
                if (state.communities.isEmpty()) CommunitiesSkeletonList(count = 4)
                else LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.communities, key = { it.id }) { community ->
                        Choice(community.slug in state.chosen, { viewModel.toggleCommunity(community.slug) }) {
                            CommunityMark(community, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(community.name, style = MaterialTheme.typography.titleSmall, color = colors.ink)
                                Text(community.description.ifBlank { plural(community.memberCount, "member") }, style = MaterialTheme.typography.bodyMedium, color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            } else {
                val suggestions = state.suggestions
                when {
                    suggestions == null -> NotificationsSkeletonList(count = 5)
                    suggestions.isEmpty() -> Text(
                        "No suggestions yet. You will find people in the communities and on Discover.",
                        Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                    else -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(suggestions, key = { it.handle }) { person ->
                            Choice(person.handle in state.follow, { viewModel.toggleFollow(person.handle) }) {
                                Avatar(person.handle, person.avatar, 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(person.displayName ?: "@${person.handle}", style = MaterialTheme.typography.titleSmall, color = colors.ink)
                                    val why = when (person.reason?.kind) {
                                        "moderator" -> "Moderates ${person.reason.community?.name}"
                                        "community" -> "In ${person.reason.community?.name}"
                                        else -> "New on HeyNaija"
                                    }
                                    Text(person.bio.ifBlank { why }, style = MaterialTheme.typography.bodyMedium, color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = colors.line)
        Column(Modifier.navigationBarsPadding().padding(16.dp)) {
            state.error?.let { Text(it, Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodyMedium, color = colors.danger) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.step == 2) SecondaryButton("Back", viewModel::back)
                PrimaryButton(
                    if (state.step == 1) "Continue" else "Finish",
                    { if (state.step == 1) viewModel.toPeople() else viewModel.finish(skip = false) },
                    Modifier.weight(1f),
                    busy = state.busy,
                )
            }
        }
    }
}

@Composable
private fun Choice(selected: Boolean, onToggle: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val colors = HeyNaija.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radii.card)
            .background(if (selected) colors.accentSoft else colors.surface)
            .border(1.dp, if (selected) colors.accent else colors.line, Radii.card)
            .clickable(onClick = onToggle)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(24.dp).clip(Radii.chip).background(if (selected) colors.accent else colors.surface).border(1.dp, if (selected) colors.accent else colors.lineStrong, Radii.chip),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Check, null, Modifier.size(16.dp), tint = colors.accentInk)
        }
    }
}

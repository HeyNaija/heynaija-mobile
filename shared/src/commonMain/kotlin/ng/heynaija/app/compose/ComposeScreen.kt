package ng.heynaija.app.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ng.heynaija.app.AppState
import ng.heynaija.app.LocalApp
import ng.heynaija.app.PostChange
import ng.heynaija.app.api.CommunityRef
import ng.heynaija.app.api.MemberSummary
import ng.heynaija.app.api.NewPoll
import ng.heynaija.app.api.NewPost
import ng.heynaija.app.api.Post
import ng.heynaija.app.api.QuotedPost
import ng.heynaija.app.media.awaitVideo
import ng.heynaija.app.media.rejectionText
import ng.heynaija.app.media.sendVideo
import ng.heynaija.app.media.uploadPostImage
import ng.heynaija.app.media.videoProblem
import ng.heynaija.app.nav.Route
import ng.heynaija.app.platform.PickedImage
import ng.heynaija.app.platform.PickedVideo
import ng.heynaija.app.platform.rememberImagePicker
import ng.heynaija.app.platform.rememberVideoPicker
import ng.heynaija.app.reasonOf
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii
import ng.heynaija.app.ui.Avatar
import ng.heynaija.app.ui.FounderBadge
import ng.heynaija.app.ui.Icons
import ng.heynaija.app.ui.PrimaryButton
import ng.heynaija.app.ui.QuotedPostCard
import ng.heynaija.app.ui.TopBar

/** The API counts characters as people see them (graphemes); a little under counts code points. */
private const val TEXT_MAX = 1000
private const val IMAGES_MAX = 4
private const val POLL_OPTIONS_MAX = 4
private const val POLL_OPTION_MAX = 80

/** A picture on its way into the post. */
data class DraftImage(
    val key: Int,
    val picked: PickedImage,
    val mediaId: String? = null,
    val failed: String? = null,
) {
    val ready get() = mediaId != null
}

/** A video on its way into the post: [stage] is 'uploading', 'processing', 'ready', 'held', 'rejected' or 'failed'. */
data class DraftVideo(
    val picked: PickedVideo,
    val mediaId: String? = null,
    val progress: Float = 0f,
    val stage: String = "uploading",
    val error: String? = null,
    val canRetry: Boolean = false,
) {
    val ready get() = stage == "ready"
}

data class PollDraft(val options: List<String> = listOf("", ""), val multiple: Boolean = false, val expiresIn: String = "7_days")

data class ComposeState(
    val text: String = "",
    val images: List<DraftImage> = emptyList(),
    val video: DraftVideo? = null,
    val poll: PollDraft? = null,
    val community: CommunityRef? = null,
    /** The communities the member may post in. */
    val joined: List<CommunityRef> = emptyList(),
    val audience: String = "everyone",
    val posting: Boolean = false,
    val error: String? = null,
)

private val POLL_LENGTHS = listOf("1_day" to "1 day", "3_days" to "3 days", "7_days" to "1 week", "30_days" to "30 days", "never" to "No end")
private val AUDIENCES = listOf(
    "everyone" to "Everyone can reply",
    "following" to "People you follow can reply",
    "mentioned" to "Only people you mention can reply",
)

/** Code points, as near as the app gets to the API's grapheme count without a segmenter. */
fun visibleLength(text: String): Int = text.codePointCountCompat()

private fun String.codePointCountCompat(): Int {
    var count = 0
    var i = 0
    while (i < length) {
        if (this[i].isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate()) i += 2 else i++
        count++
    }
    return count
}

class ComposeViewModel(private val app: AppState, private val route: Route.Compose) : ViewModel() {
    private val _state = MutableStateFlow(ComposeState(community = route.community))
    val state: StateFlow<ComposeState> = _state.asStateFlow()
    private val uploads = mutableMapOf<Int, Job>()
    private var nextKey = 0

    private val _mentions = MutableStateFlow<List<MemberSummary>>(emptyList())
    /** Members to offer for the @mention being typed; empty when there is none. */
    val mentions: StateFlow<List<MemberSummary>> = _mentions.asStateFlow()
    private var lookUp: Job? = null

    /** Asks for members a moment after typing stops; [partial] null clears the list. */
    fun suggest(partial: String?) {
        lookUp?.cancel()
        if (partial == null) {
            _mentions.value = emptyList()
            return
        }
        lookUp = viewModelScope.launch {
            delay(200)
            _mentions.value = runCatching { app.api.mentionSuggestions(partial).members.take(8) }.getOrDefault(emptyList())
        }
    }

    init {
        if (route.replyTo == null && route.gist == null) viewModelScope.launch {
            runCatching { app.api.communities() }.onSuccess { all ->
                _state.update { s -> s.copy(joined = all.filter { it.viewerIsMember }.map { CommunityRef(it.slug, it.name) }) }
            }
        }
    }

    fun setText(text: String) = _state.update { it.copy(text = text, error = null) }

    fun addImages(picked: List<PickedImage>) {
        val room = IMAGES_MAX - _state.value.images.size
        for (image in picked.take(room)) {
            val draft = DraftImage(nextKey++, image)
            _state.update { it.copy(images = it.images + draft, poll = null) }
            upload(draft)
        }
    }

    private fun upload(draft: DraftImage) {
        uploads[draft.key] = viewModelScope.launch {
            try {
                val id = uploadPostImage(app.api, draft.picked)
                setImage(draft.key) { it.copy(mediaId = id, failed = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setImage(draft.key) { it.copy(failed = reasonOf(e) ?: "Could not upload. Tap to try again.") }
            }
        }
    }

    private fun setImage(key: Int, change: (DraftImage) -> DraftImage) =
        _state.update { s -> s.copy(images = s.images.map { if (it.key == key) change(it) else it }) }

    fun retry(image: DraftImage) {
        setImage(image.key) { it.copy(failed = null) }
        upload(image)
    }

    fun removeImage(image: DraftImage) {
        uploads.remove(image.key)?.cancel()
        _state.update { s -> s.copy(images = s.images.filterNot { it.key == image.key }) }
    }

    private var videoJob: Job? = null

    fun addVideo(picked: PickedVideo) {
        val limits = app.me?.videoLimits ?: return
        videoProblem(picked, limits)?.let { app.say(it); return }
        removeVideo()
        _state.update { it.copy(video = DraftVideo(picked), images = emptyList(), poll = null) }
        videoJob = viewModelScope.launch { runVideo { app.api.startVideoUpload(picked.contentType, picked.bytes, picked.durationMs) } }
    }

    /** Picks up a broken upload where storage left it. */
    fun retryVideo() {
        val mediaId = _state.value.video?.mediaId ?: return
        setVideo { it.copy(stage = "uploading", error = null, canRetry = false) }
        videoJob = viewModelScope.launch { runVideo { app.api.resumeVideoUpload(mediaId) } }
    }

    private suspend fun runVideo(plan: suspend () -> ng.heynaija.app.api.VideoPlan) {
        val picked = _state.value.video?.picked ?: return
        val status = try {
            val p = plan()
            setVideo { it.copy(mediaId = p.mediaId) }
            sendVideo(app.api, picked, p) { share -> setVideo { it.copy(progress = share) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val started = _state.value.video?.mediaId != null
            setVideo {
                it.copy(
                    stage = "failed",
                    error = reasonOf(e) ?: if (started) "The upload was interrupted. Check your connection, then continue." else "The video could not be sent. Try again.",
                    canRetry = started,
                )
            }
            return
        }
        setVideo { it.copy(stage = "processing", progress = 1f) }
        val mediaId = _state.value.video?.mediaId ?: return
        val final = if (status.status == "processing" || status.status == "uploading") awaitVideo(app.api, mediaId) else status
        setVideo {
            when (final.status) {
                "ready" -> it.copy(stage = "ready", error = null)
                "quarantined" -> it.copy(stage = "held", error = "This video is being held for a safety check, so it cannot be posted yet.")
                "rejected" -> it.copy(stage = "rejected", error = rejectionText(final.reason, app.me?.videoLimits))
                else -> it.copy(stage = "failed", error = "We could not process that video. Remove it and try again.")
            }
        }
    }

    private fun setVideo(change: (DraftVideo) -> DraftVideo) = _state.update { s -> s.copy(video = s.video?.let(change)) }

    fun removeVideo() {
        videoJob?.cancel()
        val mediaId = _state.value.video?.mediaId
        _state.update { it.copy(video = null) }
        // Not on a post yet, so the API may drop it; a failure only leaves it for the sweep.
        if (mediaId != null) app.scope.launch { runCatching { app.api.removeVideo(mediaId) } }
    }

    fun togglePoll() = _state.update { it.copy(poll = if (it.poll == null) PollDraft() else null) }

    fun setPoll(change: (PollDraft) -> PollDraft) = _state.update { s -> s.copy(poll = s.poll?.let(change)) }

    fun setCommunity(community: CommunityRef?) = _state.update { it.copy(community = community) }

    fun setAudience(audience: String) = _state.update { it.copy(audience = audience) }

    fun canPost(s: ComposeState): Boolean {
        val length = visibleLength(s.text.trim())
        if (s.posting || length > TEXT_MAX) return false
        // A Gist's replies are text only.
        if (route.gist != null) return length > 0
        if (s.images.any { !it.ready }) return false
        if (s.video != null) return s.video.ready
        val poll = s.poll
        if (poll != null) return length > 0 && poll.options.count { it.isNotBlank() } >= 2
        return length > 0 || s.images.isNotEmpty()
    }

    fun post(onDone: () -> Unit) {
        val s = _state.value
        if (!canPost(s)) return
        _state.update { it.copy(posting = true, error = null) }
        viewModelScope.launch {
            try {
                val created = if (route.gist != null) app.api.createGistReply(route.gist, s.text.trim(), route.replyTo?.id) else app.api.createPost(
                    NewPost(
                        text = s.text.trim(),
                        replyTo = route.replyTo?.id,
                        quote = route.quote?.id,
                        community = s.community?.slug,
                        images = s.images.mapNotNull { image -> image.mediaId?.let { it to "" } },
                        poll = s.poll?.let { poll -> NewPoll(poll.options.map { it.trim() }.filter { it.isNotEmpty() }, poll.multiple, poll.expiresIn) },
                        replyAudience = s.audience,
                        video = s.video?.mediaId?.let { it to "" },
                    ),
                )
                app.emit(PostChange.Created(created.id))
                route.replyTo?.let { parent -> app.emit(PostChange.Updated(parent.copy(replyCount = parent.replyCount + 1))) }
                app.say(if (route.replyTo != null || route.gist != null) "Reply posted" else "Posted")
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(posting = false, error = reasonOf(e) ?: "Your post did not go through. Check your connection and try again.")
                }
            }
        }
    }
}

/**
 * Writing a post, reply or quote. Full screen for a post or quote; a reply opens
 * it in a sheet over the page it was asked from ([ReplySheet]), which passes
 * [onClose] and hears through [onDraft] whether anything has been written yet.
 */
@Composable
fun ComposeScreen(route: Route.Compose, onClose: (() -> Unit)? = null, onDraft: (Boolean) -> Unit = {}) {
    val app = LocalApp.current
    val close: () -> Unit = onClose ?: { app.navigator.pop() }
    val me = app.me ?: return
    val viewModel = viewModel(key = "compose") { ComposeViewModel(app, route) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = HeyNaija.colors
    val focus = remember { FocusRequester() }
    val pick = rememberImagePicker { viewModel.addImages(it) }
    val pickVideo = rememberVideoPicker { viewModel.addVideo(it) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val reply = route.replyTo
    val gist = route.gist
    val length = visibleLength(state.text.trim())
    // The field keeps the cursor, which the @mention suggestions need.
    var field by remember { mutableStateOf(TextFieldValue(state.text)) }
    val mentions by viewModel.mentions.collectAsStateWithLifecycle()
    val dirty = state.text.isNotBlank() || state.images.isNotEmpty() || state.video != null || state.poll != null
    LaunchedEffect(dirty) { onDraft(dirty) }
    fun edit(value: TextFieldValue) {
        field = value
        viewModel.setText(value.text)
        viewModel.suggest(if (value.selection.collapsed) mentionAt(value.text, value.selection.start) else null)
    }

    Column(Modifier.fillMaxSize().background(colors.bg).imePadding()) {
        TopBar(
            when {
                reply != null || gist != null -> "Reply"
                route.quote != null -> "Quote"
                else -> "New post"
            },
            onBack = close,
            backIcon = Icons.Close,
            actions = {
                PrimaryButton(
                    if (reply != null || gist != null) "Reply" else "Post",
                    { viewModel.post(close) },
                    Modifier.padding(end = 8.dp),
                    enabled = viewModel.canPost(state),
                    busy = state.posting,
                )
            },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (reply != null) ReplyingTo(reply) else if (gist != null) ReplyingToGist()
            Row {
                Avatar(me.handle, me.avatar, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    if (reply == null && route.quote == null && gist == null) Audience(state, viewModel)
                    Box {
                        if (state.text.isEmpty()) {
                            Text(
                                when {
                                    reply != null || gist != null -> "Write your reply"
                                    state.poll != null -> "Ask a question"
                                    else -> "What is on your mind?"
                                },
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize * 1.07f),
                                color = colors.faint,
                            )
                        }
                        BasicTextField(
                            field,
                            ::edit,
                            Modifier.fillMaxWidth().heightIn(min = 120.dp).focusRequester(focus),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = colors.ink,
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize * 1.07f,
                                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.07f,
                            ),
                            cursorBrush = SolidColor(colors.accent),
                        )
                    }
                    if (state.images.isNotEmpty()) Images(state.images, viewModel)
                    state.video?.let { VideoDraftCard(it, viewModel) }
                    state.poll?.let { PollEditor(it, viewModel) }
                    route.quote?.let { quoted ->
                        Spacer(Modifier.height(12.dp))
                        QuotedPostCard(quoted.asQuote())
                    }
                    state.error?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.danger)
                    }
                }
            }
        }
        if (mentions.isNotEmpty()) {
            MentionRow(mentions) { handle ->
                val (text, cursor) = insertMention(field.text, field.selection.start, handle)
                edit(TextFieldValue(text, TextRange(cursor)))
                viewModel.suggest(null)
            }
        }
        HorizontalDivider(color = colors.line)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A Gist's replies are text only, as on the web.
            if (gist == null) ToolButton(Icons.Image, "Add pictures", enabled = state.images.size < IMAGES_MAX && state.poll == null && state.video == null) {
                pick(IMAGES_MAX - state.images.size)
            }
            // One video, not in replies, and only while video is open to this member (ADR 0019).
            if (reply == null && gist == null && me.videoLimits != null && me.has("videoPosts")) {
                ToolButton(Icons.Video, "Add a video", enabled = state.images.isEmpty() && state.poll == null && state.video == null) { pickVideo() }
            }
            if (reply == null && route.quote == null && gist == null && me.has("polls")) {
                ToolButton(Icons.Vote, if (state.poll == null) "Add a poll" else "Remove the poll", enabled = state.images.isEmpty() && state.video == null, active = state.poll != null) {
                    viewModel.togglePoll()
                }
            }
            Spacer(Modifier.weight(1f))
            val left = TEXT_MAX - length
            if (left < 100) {
                Text(
                    "$left",
                    Modifier.padding(end = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (left < 0) colors.danger else colors.muted,
                )
            }
        }
    }
}

/** People to mention, in a row above the toolbar. */
@Composable
private fun MentionRow(members: List<MemberSummary>, onPick: (String) -> Unit) {
    val colors = HeyNaija.colors
    HorizontalDivider(color = colors.line)
    LazyRow(
        Modifier.fillMaxWidth().background(colors.surface),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(members, key = { it.handle }) { member ->
            Row(
                Modifier.clip(Radii.control).border(1.dp, colors.line, Radii.control).clickable { onPick(member.handle) }.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(member.handle, member.avatar, 24.dp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(member.displayName ?: "@${member.handle}", style = MaterialTheme.typography.labelLarge, color = colors.ink, maxLines = 1)
                        if (member.founder) {
                            Spacer(Modifier.width(6.dp))
                            FounderBadge(size = 14.dp, plain = true)
                        }
                    }
                    if (member.displayName != null) Text("@${member.handle}", style = MaterialTheme.typography.labelSmall, color = colors.muted, maxLines = 1)
                }
            }
        }
    }
}

private fun Post.asQuote() = QuotedPost(
    id = id,
    authorHandle = authorHandle,
    authorName = authorName,
    authorAvatar = authorAvatar,
    authorFounder = authorFounder,
    text = text,
    images = images,
    createdAt = createdAt,
)

@Composable
private fun ReplyingToGist() {
    Row(Modifier.padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Live, null, Modifier.size(18.dp), tint = HeyNaija.colors.accentStrong)
        Spacer(Modifier.width(8.dp))
        Text("Replying to this Gist", style = MaterialTheme.typography.labelLarge, color = HeyNaija.colors.accentStrong)
    }
}

@Composable
private fun ReplyingTo(post: Post) {
    val colors = HeyNaija.colors
    Column(Modifier.padding(bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(post.authorHandle, post.authorAvatar, 24.dp)
            Spacer(Modifier.width(8.dp))
            Text(post.authorName ?: "@${post.authorHandle}", style = MaterialTheme.typography.labelLarge, color = colors.ink)
        }
        if (post.text.isNotBlank()) {
            Text(
                post.text,
                Modifier.padding(start = 32.dp, top = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "Replying to @${post.authorHandle}",
            Modifier.padding(start = 32.dp, top = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colors.accentStrong,
        )
    }
}

/** Where it goes (everyone, or a community) and who may reply. */
@Composable
private fun Audience(state: ComposeState, viewModel: ComposeViewModel) {
    val app = LocalApp.current
    val colors = HeyNaija.colors
    var where by remember { mutableStateOf(false) }
    var who by remember { mutableStateOf(false) }
    Row(Modifier.padding(bottom = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            Pill(state.community?.name ?: "Everyone") { where = true }
            DropdownMenu(where, onDismissRequest = { where = false }) {
                DropdownMenuItem(text = { Text("Everyone") }, onClick = { where = false; viewModel.setCommunity(null) })
                for (community in state.joined) {
                    DropdownMenuItem(text = { Text(community.name) }, onClick = { where = false; viewModel.setCommunity(community) })
                }
                if (state.joined.isEmpty()) {
                    DropdownMenuItem(text = { Text("Join a community to post in it", color = colors.muted) }, onClick = { where = false }, enabled = false)
                }
            }
        }
        if (app.me?.has("replyControls") == true) {
            Box {
                Pill(AUDIENCES.first { it.first == state.audience }.second) { who = true }
                DropdownMenu(who, onDismissRequest = { who = false }) {
                    for ((key, label) in AUDIENCES) {
                        DropdownMenuItem(text = { Text(label) }, onClick = { who = false; viewModel.setAudience(key) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(label: String, onClick: () -> Unit) {
    val colors = HeyNaija.colors
    Row(
        Modifier.clip(Radii.control).border(1.dp, colors.lineStrong, Radii.control).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.accentStrong)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Chevron, null, Modifier.size(14.dp), tint = colors.accentStrong)
    }
}

@Composable
private fun Images(images: List<DraftImage>, viewModel: ComposeViewModel) {
    val colors = HeyNaija.colors
    Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (image in images) {
            Box(Modifier.size(110.dp).clip(Radii.control).background(colors.panel).border(1.dp, colors.line, Radii.control)) {
                AsyncImage(image.picked.bytes, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                when {
                    image.failed != null -> Box(
                        Modifier.matchParentSize().background(Color(0xB3070D0B)).clickable { viewModel.retry(image) }.padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(image.failed, style = MaterialTheme.typography.labelMedium, color = Color(0xFFEEF4F0)) }
                    !image.ready -> Box(Modifier.matchParentSize().background(Color(0x66070D0B)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Color(0xFFEEF4F0))
                    }
                }
                Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp).clip(CircleShape).background(Color(0xD9070D0B)).clickable { viewModel.removeImage(image) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Close, "Remove picture", Modifier.size(14.dp), tint = Color(0xFFEEF4F0)) }
            }
        }
    }
}

@Composable
private fun VideoDraftCard(video: DraftVideo, viewModel: ComposeViewModel) {
    val colors = HeyNaija.colors
    val ratio = if (video.picked.width > 0 && video.picked.height > 0) (video.picked.width.toFloat() / video.picked.height).coerceIn(0.75f, 16f / 9f) else 16f / 9f
    Column(Modifier.padding(top = 12.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(ratio).clip(Radii.control).background(Color.Black).border(1.dp, colors.line, Radii.control)) {
            video.picked.thumbnail?.let { AsyncImage(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).clip(CircleShape).background(Color(0xD9070D0B)).clickable { viewModel.removeVideo() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Close, "Remove video", Modifier.size(16.dp), tint = Color(0xFFEEF4F0)) }
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0xB3070D0B)).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (video.stage == "uploading" || video.stage == "processing") {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFFEEF4F0))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    when (video.stage) {
                        "uploading" -> "Uploading ${(video.progress * 100).toInt()}%"
                        "processing" -> "Getting it ready to play. You can keep writing."
                        "ready" -> "Ready" + (video.picked.durationMs?.let { " · ${ng.heynaija.app.ui.clipLength(it)}" } ?: "")
                        else -> "Not ready"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFEEF4F0),
                )
            }
        }
        video.error?.let {
            Text(it, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = colors.danger)
        }
        if (video.canRetry) {
            Text(
                "Continue the upload",
                Modifier.padding(top = 4.dp).clickable { viewModel.retryVideo() }.padding(vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = colors.accentStrong,
            )
        }
    }
}

@Composable
private fun PollEditor(poll: PollDraft, viewModel: ComposeViewModel) {
    val colors = HeyNaija.colors
    var length by remember { mutableStateOf(false) }
    Column(
        Modifier.padding(top = 12.dp).fillMaxWidth().clip(Radii.card).border(1.dp, colors.lineStrong, Radii.card).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        poll.options.forEachIndexed { index, option ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).clip(Radii.control).border(1.dp, colors.line, Radii.control).background(colors.surface).padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (option.isEmpty()) Text("Choice ${index + 1}", style = MaterialTheme.typography.bodyMedium, color = colors.faint)
                    BasicTextField(
                        option,
                        { text -> if (text.length <= POLL_OPTION_MAX) viewModel.setPoll { p -> p.copy(options = p.options.toMutableList().also { it[index] = text }) } },
                        Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.ink),
                        cursorBrush = SolidColor(colors.accent),
                    )
                }
                if (poll.options.size > 2) {
                    Icon(
                        Icons.Close,
                        "Remove choice",
                        Modifier.padding(start = 8.dp).size(20.dp).clickable { viewModel.setPoll { p -> p.copy(options = p.options.filterIndexed { i, _ -> i != index }) } },
                        tint = colors.muted,
                    )
                }
            }
        }
        if (poll.options.size < POLL_OPTIONS_MAX) {
            Text(
                "Add a choice",
                Modifier.clickable { viewModel.setPoll { it.copy(options = it.options + "") } }.padding(vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = colors.accentStrong,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("More than one answer", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = colors.ink)
            Switch(
                poll.multiple,
                { on -> viewModel.setPoll { it.copy(multiple = on) } },
                colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.accentInk),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Open for", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = colors.ink)
            Box {
                Pill(POLL_LENGTHS.first { it.first == poll.expiresIn }.second) { length = true }
                DropdownMenu(length, onDismissRequest = { length = false }) {
                    for ((key, label) in POLL_LENGTHS) {
                        DropdownMenuItem(text = { Text(label) }, onClick = { length = false; viewModel.setPoll { it.copy(expiresIn = key) } })
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    val colors = HeyNaija.colors
    Box(
        Modifier.size(44.dp).clip(Radii.control).clickable(enabled = enabled, onClick = onClick, onClickLabel = label),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(22.dp), tint = when {
            !enabled -> colors.faint
            active -> colors.accentStrong
            else -> colors.accent
        })
    }
}

package ng.heynaija.app.media

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import ng.heynaija.app.platform.VideoSurface
import ng.heynaija.app.platform.rememberVideoHandle
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii
import ng.heynaija.app.ui.Icons

// The web's scrim tokens (apps/web/app/globals.css): --scrim, --on-scrim, --scrim-control-hover.
private val Scrim = Color(0xFA070D0B)
private val OnScrim = Color(0xFFEEF4F0)
private val ScrimControl = Color(0x38FFFFFF)

/** The seek bar's touch target, as on the web; its track is 4dp. */
private val SeekHeight = 24.dp

/** How long the controls stay after the last touch while it plays, as on the web. */
private const val AWAKE_MS = 2500L

/** m:ss, rounded down, as a clock counts (h:mm:ss past an hour). */
private fun clock(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    val mm = (seconds / 60 % 60).toString()
    val ss = (seconds % 60).toString().padStart(2, '0')
    return if (seconds >= 3600) "${seconds / 3600}:${mm.padStart(2, '0')}:$ss" else "${seconds / 60}:$ss"
}

/**
 * HeyNaija's own controls over the platform's player, as the web draws them
 * (apps/web/app/ui/video-player.tsx): a big play and pause in the middle, and a
 * bar at the foot with play and pause, the time, a seek bar that shows what has
 * loaded (played in the accent), mute and full screen. On a phone-sized player
 * the seek bar has a row of its own above the buttons. While it plays they fade
 * after a moment; a tap brings them back, or hides them again.
 *
 * It plays from [startMs] as soon as it is shown, pauses when the app goes to
 * the background, and lets go of the player when it leaves the screen. [onEnded]
 * is told when the clip has played to its end. [onFullScreen] adds the full
 * screen button and is told where the clip had got to; [fullScreen] is a player
 * that already is, whose button comes back out.
 */
@Composable
fun VideoPlayer(
    url: String,
    modifier: Modifier,
    startMs: Long = 0,
    fullScreen: Boolean = false,
    onEnded: () -> Unit = {},
    onFullScreen: ((positionMs: Long) -> Unit)? = null,
) {
    val handle = rememberVideoHandle(url, startMs)
    val media by handle.state
    val ended by rememberUpdatedState(onEnded)
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { handle.pause() }
    LaunchedEffect(media.ended) { if (media.ended) ended() }

    var awake by remember { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    var seeking by remember { mutableStateOf(false) }
    val shown = !media.playing || media.failed || awake || seeking
    fun wake() {
        awake = true
        touches++
    }
    // Each touch, and each time it starts, keeps them a moment more.
    LaunchedEffect(touches, media.playing) {
        if (media.playing) {
            delay(AWAKE_MS)
            awake = false
        }
    }
    val fade by animateFloatAsState(if (shown) 1f else 0f, tween(200))

    BoxWithConstraints(
        modifier
            .background(Color.Black)
            // Resting, the first touch only brings the controls back; awake, any touch keeps them.
            .pointerInput(shown) {
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    if (!shown) down.consume()
                    wake()
                }
            },
    ) {
        val narrow = maxWidth < 420.dp
        val small = maxWidth < 300.dp
        val navigation = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        VideoSurface(handle, Modifier.matchParentSize())
        // The picture: a tap shows the controls, or hides them while it plays.
        Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { if (media.playing) awake = false } })

        // The big button sits in the middle of the picture, and rises only as far as
        // it must to keep clear of the bar, on a short, wide clip.
        val big = if (small) 48.dp else 56.dp
        val bar = (if (narrow) SeekHeight + 44.dp else 44.dp) + 8.dp + (if (fullScreen) navigation else 0.dp)
        val middle = minOf(maxHeight / 2, maxHeight - bar - 8.dp - big / 2).coerceAtLeast(big / 2)
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = middle - big / 2).size(big),
            contentAlignment = Alignment.Center,
        ) {
            if (media.waiting) {
                CircularProgressIndicator(Modifier.size(40.dp), color = OnScrim, trackColor = ScrimControl, strokeWidth = 3.dp)
            } else if (fade > 0f) {
                Box(
                    Modifier
                        .alpha(fade)
                        .size(big)
                        .clip(Radii.card)
                        .background(Scrim.copy(alpha = 0.7f))
                        .clickable(enabled = shown) { if (media.playing) handle.pause() else handle.play() }
                        .semantics { contentDescription = if (media.playing) "Pause" else "Play" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (media.playing) Icons.PauseFilled else Icons.PlayFilled, null, Modifier.size(28.dp), tint = OnScrim)
                }
            }
        }

        if (fade > 0f) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .alpha(fade)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Scrim.copy(alpha = 0.8f))))
                    .let { if (fullScreen) it.navigationBarsPadding() else it }
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = if (narrow) 20.dp else 28.dp),
            ) {
                val seekBar = @Composable { m: Modifier ->
                    SeekBar(media.positionMs, media.durationMs, media.bufferedMs, enabled = shown, onSeeking = { seeking = it }, onSeek = handle::seekTo, modifier = m)
                }
                if (narrow) seekBar(Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BarButton(if (media.playing) Icons.PauseFilled else Icons.PlayFilled, if (media.playing) "Pause" else "Play", shown) {
                        if (media.playing) handle.pause() else handle.play()
                    }
                    Text(
                        "${if (media.ended) clock(media.durationMs) else clock(media.positionMs)} / ${clock(media.durationMs)}",
                        Modifier.padding(start = 4.dp, end = 8.dp),
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnScrim),
                        maxLines = 1,
                    )
                    if (narrow) Spacer(Modifier.weight(1f)) else seekBar(Modifier.weight(1f))
                    BarButton(if (media.muted) Icons.Muted else Icons.Sound, if (media.muted) "Unmute" else "Mute", shown) {
                        handle.setMuted(!media.muted)
                    }
                    if (onFullScreen != null) {
                        BarButton(if (fullScreen) Icons.ExitFullScreen else Icons.FullScreen, if (fullScreen) "Exit full screen" else "Full screen", shown) {
                            onFullScreen(media.positionMs)
                        }
                    }
                }
            }
        }

        if (media.failed) {
            Box(Modifier.matchParentSize().background(Scrim).padding(16.dp), contentAlignment = Alignment.Center) {
                Text("This video could not be played.", style = TextStyle(fontSize = 13.sp, color = OnScrim))
            }
        }
    }
}

@Composable
private fun RowScope.BarButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(Radii.control)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = OnScrim)
    }
}

/**
 * The web's seek bar: a 4dp track in a target tall enough for a thumb, played
 * in the accent, loaded lighter, the rest faint, and a light thumb that runs
 * from 7dp in to 7dp from the end. A tap seeks there; a drag shows where it
 * will go and seeks when let go.
 */
@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    enabled: Boolean,
    onSeeking: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
) {
    val accent = HeyNaija.colors.accent
    var dragged by remember { mutableStateOf<Float?>(null) }
    val length by rememberUpdatedState(durationMs)
    val usable = enabled && durationMs > 0
    val played = dragged ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val loaded = if (durationMs > 0) (maxOf(bufferedMs, positionMs).toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Canvas(
        modifier
            .height(SeekHeight)
            .alpha(if (durationMs > 0) 1f else 0.6f)
            .semantics { contentDescription = "Seek, ${clock(positionMs)} of ${clock(durationMs)}" }
            .pointerInput(usable) {
                if (!usable) return@pointerInput
                val inset = 7.dp.toPx()
                fun at(x: Float) = ((x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)
                detectTapGestures { onSeek((at(it.x) * length).toLong()) }
            }
            .pointerInput(usable) {
                if (!usable) return@pointerInput
                val inset = 7.dp.toPx()
                fun at(x: Float) = ((x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragged = at(it.x)
                        onSeeking(true)
                    },
                    onDragEnd = {
                        dragged?.let { onSeek((it * length).toLong()) }
                        dragged = null
                        onSeeking(false)
                    },
                    onDragCancel = {
                        dragged = null
                        onSeeking(false)
                    },
                ) { change, _ ->
                    change.consume()
                    dragged = at(change.position.x)
                }
            },
    ) {
        val inset = 7.dp.toPx()
        val track = 4.dp.toPx()
        val width = size.width - 2 * inset
        val y = size.height / 2
        val radius = CornerRadius(track / 2)
        fun bar(from: Float, to: Float, colour: Color) {
            if (to <= from) return
            drawRoundRect(colour, Offset(inset + width * from, y - track / 2), Size(width * (to - from), track), radius)
        }
        bar(0f, 1f, ScrimControl)
        bar(played, loaded, OnScrim.copy(alpha = 0.55f))
        bar(0f, played, accent)
        drawCircle(OnScrim, radius = 7.dp.toPx(), center = Offset(inset + width * played, y))
    }
}

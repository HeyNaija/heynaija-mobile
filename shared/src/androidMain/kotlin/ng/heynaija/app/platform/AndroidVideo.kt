package ng.heynaija.app.platform

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private class ExoHandle(val player: ExoPlayer) : VideoHandle {
    private val current = mutableStateOf(VideoState())
    override val state: State<VideoState> = current

    fun refresh() {
        val ended = player.playbackState == Player.STATE_ENDED
        current.value = VideoState(
            playing = player.playWhenReady && !ended,
            ended = ended,
            positionMs = player.currentPosition,
            durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0,
            bufferedMs = player.bufferedPosition,
            muted = player.volume == 0f,
            waiting = player.playWhenReady && player.playbackState == Player.STATE_BUFFERING,
            failed = player.playerError != null,
        )
    }

    override fun play() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.play()
    }

    override fun pause() = player.pause()

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        refresh()
    }

    override fun setMuted(muted: Boolean) {
        player.volume = if (muted) 0f else 1f
        refresh()
    }
}

@Composable
actual fun rememberVideoHandle(url: String, startMs: Long): VideoHandle {
    val context = LocalContext.current
    val handle = remember(url) {
        ExoHandle(
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build(), startMs)
                prepare()
                playWhenReady = true
            },
        )
    }
    DisposableEffect(handle) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = handle.refresh()
        }
        handle.player.addListener(listener)
        onDispose {
            handle.player.removeListener(listener)
            handle.player.release()
        }
    }
    // The playhead moves without events.
    LaunchedEffect(handle) {
        while (isActive) {
            if (handle.player.isPlaying) handle.refresh()
            delay(250)
        }
    }
    return handle
}

@Composable
actual fun VideoSurface(handle: VideoHandle, modifier: Modifier) {
    val player = (handle as ExoHandle).player
    AndroidView(
        factory = {
            PlayerView(it).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(Color.BLACK)
                this.player = player
            }
        },
        modifier = modifier,
        update = { it.player = player },
    )
}

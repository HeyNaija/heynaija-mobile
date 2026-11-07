package ng.heynaija.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
actual fun VideoPlayer(url: String, modifier: Modifier, startMs: Long, onEnded: () -> Unit, onFullScreen: ((positionMs: Long) -> Unit)?) {
    val context = LocalContext.current
    val ended = rememberUpdatedState(onEnded)
    val fullScreen = rememberUpdatedState(onFullScreen)
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build(), startMs)
            prepare()
            playWhenReady = true
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) ended.value()
            }
        }
        // Not on in the background: the app is out of sight.
        val pause = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) player.pause() }
        player.addListener(listener)
        lifecycle.addObserver(pause)
        onDispose {
            lifecycle.removeObserver(pause)
            player.removeListener(listener)
            player.release()
        }
    }
    AndroidView(
        factory = {
            PlayerView(it).apply {
                this.player = player
                // One clip, so no previous or next.
                setShowPreviousButton(false)
                setShowNextButton(false)
                if (fullScreen.value != null) {
                    setFullscreenButtonClickListener { fullScreen.value?.invoke(player.currentPosition) }
                }
            }
        },
        modifier = modifier,
        update = { it.player = player },
    )
}

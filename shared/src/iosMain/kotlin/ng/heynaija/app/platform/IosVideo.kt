@file:OptIn(ExperimentalForeignApi::class)

package ng.heynaija.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.CMTimeRangeValue
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerTimeControlStatusPaused
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.loadedTimeRanges
import platform.AVFoundation.muted
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.seekToTime
import platform.AVFoundation.setMuted
import platform.AVFoundation.timeControlStatus
import platform.AVKit.AVPlayerViewController
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSValue

private class AvHandle(val player: AVPlayer) : VideoHandle {
    private val current = mutableStateOf(VideoState())
    override val state: State<VideoState> = current
    var ended = false

    fun refresh() {
        val item = player.currentItem
        val seconds = CMTimeGetSeconds(player.currentTime())
        val length = item?.let { CMTimeGetSeconds(it.duration) }?.takeIf { it.isFinite() } ?: 0.0
        // The end of the stretch that has loaded around the playhead.
        var loaded = seconds
        item?.loadedTimeRanges?.forEach { value ->
            (value as? NSValue)?.CMTimeRangeValue?.useContents {
                val from = CMTimeGetSeconds(start.readValue())
                val to = from + CMTimeGetSeconds(duration.readValue())
                if (from <= seconds + 0.1 && to >= seconds) loaded = to
            }
        }
        val status = player.timeControlStatus
        current.value = VideoState(
            playing = status != AVPlayerTimeControlStatusPaused && !ended,
            ended = ended,
            positionMs = (seconds.takeIf { it.isFinite() } ?: 0.0).times(1000).toLong(),
            durationMs = (length * 1000).toLong(),
            bufferedMs = (loaded.takeIf { it.isFinite() } ?: 0.0).times(1000).toLong(),
            muted = player.muted,
            waiting = status == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate,
            failed = item?.status == AVPlayerItemStatusFailed,
        )
    }

    override fun play() {
        if (ended) player.seekToTime(CMTimeMake(value = 0, timescale = 1000))
        ended = false
        player.play()
        refresh()
    }

    override fun pause() {
        player.pause()
        refresh()
    }

    override fun seekTo(positionMs: Long) {
        ended = false
        player.seekToTime(CMTimeMake(value = positionMs, timescale = 1000))
        refresh()
    }

    override fun setMuted(muted: Boolean) {
        player.setMuted(muted)
        refresh()
    }
}

@Composable
actual fun rememberVideoHandle(url: String, startMs: Long): VideoHandle {
    val handle = remember(url) {
        AvHandle(AVPlayer(uRL = NSURL.URLWithString(url)!!)).apply {
            if (startMs > 0) player.seekToTime(CMTimeMake(value = startMs, timescale = 1000))
            player.play()
        }
    }
    DisposableEffect(handle) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            AVPlayerItemDidPlayToEndTimeNotification,
            `object` = handle.player.currentItem,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            handle.ended = true
            handle.refresh()
        }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
            handle.player.pause()
        }
    }
    LaunchedEffect(handle) {
        while (isActive) {
            handle.refresh()
            delay(250)
        }
    }
    return handle
}

@Composable
actual fun VideoSurface(handle: VideoHandle, modifier: Modifier) {
    val player = (handle as AvHandle).player
    UIKitViewController(
        factory = {
            AVPlayerViewController().apply {
                this.player = player
                showsPlaybackControls = false
                videoGravity = AVLayerVideoGravityResizeAspect
                // Touches belong to HeyNaija's controls drawn over it.
                view.userInteractionEnabled = false
            }
        },
        modifier = modifier,
        properties = UIKitInteropProperties(interactionMode = null),
    )
}

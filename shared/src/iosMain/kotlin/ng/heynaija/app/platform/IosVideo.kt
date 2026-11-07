@file:OptIn(ExperimentalForeignApi::class)

package ng.heynaija.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.currentItem
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.seekToTime
import platform.AVKit.AVPlayerViewController
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL

/** AVKit's controls have their own full screen, so [onFullScreen] is not needed here. */
@Composable
actual fun VideoPlayer(url: String, modifier: Modifier, startMs: Long, onEnded: () -> Unit, onFullScreen: ((positionMs: Long) -> Unit)?) {
    val ended = rememberUpdatedState(onEnded)
    val player = remember(url) {
        NSURL.URLWithString(url)?.let { AVPlayer(uRL = it) }?.apply {
            if (startMs > 0) seekToTime(CMTimeMake(value = startMs, timescale = 1000))
        }
    }
    DisposableEffect(player) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            AVPlayerItemDidPlayToEndTimeNotification,
            `object` = player?.currentItem,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> ended.value() }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
            player?.pause()
        }
    }
    UIKitViewController(
        factory = {
            AVPlayerViewController().apply {
                this.player = player
                player?.play()
            }
        },
        modifier = modifier,
    )
}

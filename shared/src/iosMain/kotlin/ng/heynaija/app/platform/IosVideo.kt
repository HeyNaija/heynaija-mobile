@file:OptIn(ExperimentalForeignApi::class)

package ng.heynaija.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSURL

@Composable
actual fun VideoPlayer(url: String, modifier: Modifier) {
    UIKitViewController(
        factory = {
            AVPlayerViewController().apply {
                player = NSURL.URLWithString(url)?.let { AVPlayer(uRL = it) }
                player?.play()
            }
        },
        modifier = modifier,
        onRelease = { it.player?.pause() },
    )
}

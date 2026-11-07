package ng.heynaija.app.media

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import ng.heynaija.app.LocalApp
import ng.heynaija.app.api.PostImage
import ng.heynaija.app.api.mediaUrl
import ng.heynaija.app.ui.Icons

private val Scrim = Color(0xFF070D0B)
private val OnScrim = Color(0xFFEEF4F0)

@Composable
private fun CloseButton(modifier: Modifier) {
    val app = LocalApp.current
    Box(
        modifier
            .statusBarsPadding()
            .padding(12.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0x99000000))
            .pointerInput(Unit) { detectTapGestures { app.navigator.pop() } },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Close, "Close", Modifier.size(22.dp), tint = OnScrim) }
}

/** A post's pictures, full screen: swipe between them, pinch or double-tap to zoom. */
@Composable
fun ImageViewer(images: List<PostImage>, start: Int) {
    val pager = rememberPagerState(initialPage = start) { images.size }
    Box(Modifier.fillMaxSize().background(Scrim)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page -> Zoomable(images[page]) }
        CloseButton(Modifier.align(Alignment.TopStart))
        if (images.size > 1) {
            Text(
                "${pager.currentPage + 1} of ${images.size}",
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 22.dp),
                style = MaterialTheme.typography.labelLarge,
                color = OnScrim,
            )
        }
        val alt = images[pager.currentPage].alt
        if (alt.isNotBlank()) {
            Text(
                alt,
                Modifier.align(Alignment.BottomStart).background(Color(0xB3000000)).padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = OnScrim,
            )
        }
    }
}

@Composable
private fun Zoomable(image: PostImage) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    AsyncImage(
        model = mediaUrl(image.src, "1600"),
        contentDescription = image.alt.ifBlank { null },
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                })
            }
            // Only take the pan once zoomed, so a swipe at normal size turns the page.
            .transformable(transform, canPan = { scale > 1f })
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}

/** A post's clip, full screen, in the platform's own player. */
@Composable
fun VideoScreen(url: String, startMs: Long = 0) {
    val app = LocalApp.current
    Box(Modifier.fillMaxSize().background(Scrim)) {
        VideoPlayer(url, Modifier.fillMaxSize().statusBarsPadding(), startMs, fullScreen = true, onFullScreen = { app.navigator.pop() })
        CloseButton(Modifier.align(Alignment.TopStart))
    }
}

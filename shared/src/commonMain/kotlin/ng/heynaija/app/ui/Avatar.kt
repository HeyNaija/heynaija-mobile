package ng.heynaija.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import ng.heynaija.app.api.mediaUrl
import ng.heynaija.app.theme.HeyNaija

/**
 * A member's profile photo, or their initial on a tint picked from their handle
 * (the same tint as on the web). A photo sits on its tint, so there is colour,
 * not a hole, while it loads. Decorative: the handle is always printed beside it.
 */
@Composable
fun Avatar(handle: String, path: String?, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val colors = HeyNaija.colors
    Box(
        modifier.size(size).clip(CircleShape).background(colors.tones[toneFor(handle, colors.tones.size)]),
        contentAlignment = Alignment.Center,
    ) {
        if (path != null) {
            AsyncImage(
                // Two stored sizes; pick the one that still looks sharp on a dense screen.
                model = mediaUrl(path, if (size <= 32.dp) "96" else "256"),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Text(
                handle.take(1).uppercase(),
                color = colors.brandDeep,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.36f).sp,
            )
        }
    }
}

/** The web's bucket: `hash = hash * 31 + code`, kept to 32 unsigned bits (apps/web/app/ui/avatar.tsx). */
internal fun toneFor(handle: String, tones: Int): Int {
    var hash = 0u
    for (ch in handle) hash = hash * 31u + ch.code.toUInt()
    return (hash % tones.toUInt()).toInt()
}

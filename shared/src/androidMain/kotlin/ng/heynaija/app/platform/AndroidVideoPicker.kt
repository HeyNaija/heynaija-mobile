package ng.heynaija.app.platform

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberVideoPicker(onPicked: (PickedVideo) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val video = withContext(Dispatchers.IO) { runCatching { readVideo(context, uri) }.getOrNull() }
            if (video != null) latest.value(video)
        }
    }
    return remember { { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) } }
}

private fun readVideo(context: Context, uri: Uri): PickedVideo {
    val resolver = context.contentResolver
    val size = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getLong(0) else null
    } ?: resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        var width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        var height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        // A phone held upright records sideways and says so; the picture people see is turned.
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (rotation == 90 || rotation == 270) width = height.also { height = width }
        val frame = retriever.getFrameAtTime(0)?.let { bitmap ->
            val scaled = Bitmap.createScaledBitmap(bitmap, 320, (320f * bitmap.height / bitmap.width).toInt().coerceAtLeast(1), true)
            ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
        }
        return PickedVideo(
            contentType = resolver.getType(uri) ?: "video/mp4",
            bytes = size,
            durationMs = duration,
            width = width,
            height = height,
            thumbnail = frame,
            read = { offset, length ->
                resolver.openFileDescriptor(uri, "r")!!.use { fd ->
                    FileInputStream(fd.fileDescriptor).channel.use { channel ->
                        val buffer = ByteBuffer.allocate(length)
                        var at = offset
                        while (buffer.hasRemaining()) {
                            val n = channel.read(buffer, at)
                            if (n < 0) break
                            at += n
                        }
                        buffer.array().copyOf(buffer.position())
                    }
                }
            },
        )
    } finally {
        retriever.release()
    }
}

package ng.heynaija.app.platform

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val LONG_EDGE = 2048

@Composable
actual fun rememberImagePicker(onPicked: (List<PickedImage>) -> Unit): (max: Int) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(onPicked)
    val read: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty()) scope.launch {
            val images = withContext(Dispatchers.IO) { uris.mapNotNull { runCatching { readImage(context, it) }.getOrNull() } }
            latest.value(images)
        }
    }
    val one = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> read(listOfNotNull(uri)) }
    val many = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris -> read(uris) }
    return remember {
        { max ->
            val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            if (max <= 1) one.launch(request) else many.launch(request)
        }
    }
}

private fun readImage(context: Context, uri: Uri): PickedImage? {
    val resolver = context.contentResolver
    val type = resolver.getType(uri) ?: "image/jpeg"
    val raw = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (type == "image/gif") {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        return PickedImage(raw, type, bounds.outWidth, bounds.outHeight)
    }
    val bitmap = decode(context, uri, raw) ?: return null
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
    return PickedImage(out.toByteArray(), "image/jpeg", bitmap.width, bitmap.height)
}

/** The picture, upright and no larger than [LONG_EDGE]. */
private fun decode(context: Context, uri: Uri, raw: ByteArray): Bitmap? {
    if (Build.VERSION.SDK_INT >= 28) {
        // ImageDecoder applies the photo's rotation itself.
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val long = maxOf(info.size.width, info.size.height)
            if (long > LONG_EDGE) {
                val scale = LONG_EDGE.toFloat() / long
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
        }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= LONG_EDGE) sample *= 2
    return BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

@Composable
actual fun rememberShare(): (text: String) -> Unit {
    val context = LocalContext.current
    return remember {
        { text ->
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

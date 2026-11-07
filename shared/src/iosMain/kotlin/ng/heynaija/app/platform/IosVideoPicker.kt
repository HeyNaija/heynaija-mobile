@file:OptIn(ExperimentalForeignApi::class)

package ng.heynaija.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.duration
import platform.AVFoundation.naturalSize
import platform.AVFoundation.preferredTransform
import platform.AVFoundation.tracksWithMediaType
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.closeFile
import platform.Foundation.fileHandleForReadingAtPath
import platform.Foundation.readDataOfLength
import platform.Foundation.seekToFileOffset
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.math.abs

@Composable
actual fun rememberVideoPicker(onPicked: (PickedVideo) -> Unit): () -> Unit {
    val latest = rememberUpdatedState(onPicked)
    val delegate = remember { VideoPickerDelegate { latest.value(it) } }
    return remember {
        {
            val config = PHPickerConfiguration()
            config.filter = PHPickerFilter.videosFilter
            config.selectionLimit = 1
            val picker = PHPickerViewController(configuration = config)
            picker.delegate = delegate
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class VideoPickerDelegate(private val onPicked: (PickedVideo) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
        // The file handed over is deleted when this returns, so it is copied first.
        result.itemProvider.loadFileRepresentationForTypeIdentifier("public.movie") { url, _ ->
            val source = url ?: return@loadFileRepresentationForTypeIdentifier
            val extension = source.pathExtension?.lowercase() ?: "mov"
            val copy = NSURL.fileURLWithPath(NSTemporaryDirectory() + NSUUID().UUIDString + "." + extension)
            if (!NSFileManager.defaultManager.copyItemAtURL(source, copy, null)) return@loadFileRepresentationForTypeIdentifier
            val video = videoAt(copy, extension) ?: return@loadFileRepresentationForTypeIdentifier
            dispatch_async(dispatch_get_main_queue()) { onPicked(video) }
        }
    }
}

private fun videoAt(url: NSURL, extension: String): PickedVideo? {
    val path = url.path ?: return null
    val size = (NSFileManager.defaultManager.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: return null
    val asset = AVURLAsset(uRL = url, options = null)
    val seconds = CMTimeGetSeconds(asset.duration)
    val track = asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? platform.AVFoundation.AVAssetTrack
    var width = 0
    var height = 0
    track?.let { t ->
        t.naturalSize.useContents { width = abs(this.width).toInt(); height = abs(this.height).toInt() }
        // Turned a quarter: what people see is the other way round.
        val turned = t.preferredTransform.useContents { b != 0.0 || c != 0.0 }
        if (turned) width = height.also { height = width }
    }
    val generator = AVAssetImageGenerator(asset = asset)
    generator.appliesPreferredTrackTransform = true
    val thumbnail = generator.copyCGImageAtTime(CMTimeMake(0, 1), null, null)?.let { image ->
        UIImageJPEGRepresentation(UIImage.imageWithCGImage(image), 0.7)?.toByteArray()
    }
    return PickedVideo(
        contentType = if (extension == "mp4" || extension == "m4v") "video/mp4" else "video/quicktime",
        bytes = size,
        durationMs = if (seconds.isNaN()) null else (seconds * 1000).toLong(),
        width = width,
        height = height,
        thumbnail = thumbnail,
        read = { offset, length ->
            val handle = NSFileHandle.fileHandleForReadingAtPath(path)!!
            try {
                handle.seekToFileOffset(offset.toULong())
                handle.readDataOfLength(length.toULong()).toByteArray()
            } finally {
                handle.closeFile()
            }
        },
    )
}


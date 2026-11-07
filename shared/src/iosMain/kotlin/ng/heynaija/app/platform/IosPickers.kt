@file:OptIn(ExperimentalForeignApi::class)

package ng.heynaija.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val LONG_EDGE = 2048.0

/** The view controller on top, to present a picker or share sheet from. */
internal fun topViewController(): UIViewController? {
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}

@Composable
actual fun rememberImagePicker(onPicked: (List<PickedImage>) -> Unit): (max: Int) -> Unit {
    val latest = rememberUpdatedState(onPicked)
    val delegate = remember { PickerDelegate { latest.value(it) } }
    return remember {
        { max ->
            val config = PHPickerConfiguration()
            config.filter = PHPickerFilter.imagesFilter
            config.selectionLimit = max.toLong()
            val picker = PHPickerViewController(configuration = config)
            picker.delegate = delegate
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class PickerDelegate(private val onPicked: (List<PickedImage>) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val results = didFinishPicking.filterIsInstance<PHPickerResult>()
        if (results.isEmpty()) return
        val picked = arrayOfNulls<PickedImage>(results.size)
        var left = results.size
        // Each picture loads on its own thread; the last one in hands them all over, in order, on the main thread.
        fun done(index: Int, image: PickedImage?) = dispatch_async(dispatch_get_main_queue()) {
            picked[index] = image
            if (--left == 0) onPicked(picked.filterNotNull())
        }
        results.forEachIndexed { index, result ->
            val provider = result.itemProvider
            val gif = provider.hasItemConformingToTypeIdentifier("com.compuserve.gif")
            provider.loadDataRepresentationForTypeIdentifier(if (gif) "com.compuserve.gif" else "public.image") { data, _ ->
                done(index, data?.let { if (gif) gifOf(it) else photoOf(it) })
            }
        }
    }
}

private fun gifOf(data: NSData): PickedImage? {
    val image = UIImage.imageWithData(data) ?: return null
    val (width, height) = image.size.useContents { (width * image.scale).toInt() to (height * image.scale).toInt() }
    return PickedImage(data.toByteArray(), "image/gif", width, height)
}

/** Any photo (HEIC included) as an upright JPEG no larger than [LONG_EDGE]. */
private fun photoOf(data: NSData): PickedImage? {
    val image = UIImage.imageWithData(data) ?: return null
    val (width, height) = image.size.useContents { width * image.scale to height * image.scale }
    val scale = minOf(1.0, LONG_EDGE / maxOf(width, height))
    val size = CGSizeMake(width * scale, height * scale)
    // Drawing applies the photo's orientation, so the JPEG comes out upright.
    val renderer = UIGraphicsImageRenderer(size = size, format = platform.UIKit.UIGraphicsImageRendererFormat().apply { this.scale = 1.0 })
    val drawn = renderer.imageWithActions { _ -> image.drawInRect(CGRectMake(0.0, 0.0, width * scale, height * scale)) }
    val jpeg = UIImageJPEGRepresentation(drawn, 0.85) ?: return null
    return PickedImage(jpeg.toByteArray(), "image/jpeg", (width * scale).toInt(), (height * scale).toInt())
}

@Composable
actual fun rememberShare(): (text: String) -> Unit = remember {
    { text ->
        val top = topViewController()
        if (top != null) {
            val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
            // An iPad shows it as a popover, which needs somewhere to point.
            sheet.popoverPresentationController?.sourceView = top.view
            top.presentViewController(sheet, animated = true, completion = null)
        }
    }
}

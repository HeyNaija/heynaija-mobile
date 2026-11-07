package ng.heynaija.app.media

import kotlinx.coroutines.delay
import ng.heynaija.app.api.ApiException
import ng.heynaija.app.api.HeyNaijaApi
import ng.heynaija.app.platform.PickedImage

/** Under the API's limits: 5 MB for a profile photo, 10 MB for a post's picture. */
const val AVATAR_MAX_BYTES = 5 * 1024 * 1024
const val POST_IMAGE_MAX_BYTES = 10 * 1024 * 1024

/** Uploads a profile photo and makes it the member's; answers with its path. */
suspend fun uploadAvatar(api: HeyNaijaApi, image: PickedImage): String? {
    if (image.bytes.size > AVATAR_MAX_BYTES) throw ApiException(400, "too_large", "That picture is too large. Choose one under 5 MB.")
    val upload = api.startUpload("avatar", image.contentType, image.bytes.size)
    api.putFile(upload, image.contentType, image.bytes)
    return api.completeAvatar(upload.mediaId)
}

/**
 * Uploads a picture for a post and waits until the API has checked and
 * processed it: a post can only be made once every picture on it is ready.
 * Answers with the upload's ID.
 */
suspend fun uploadPostImage(api: HeyNaijaApi, image: PickedImage): String {
    if (image.bytes.size > POST_IMAGE_MAX_BYTES) throw ApiException(400, "too_large", "That picture is too large. Choose one under 10 MB.")
    val upload = api.startUpload("images", image.contentType, image.bytes.size)
    api.putFile(upload, image.contentType, image.bytes)
    var status = api.completeImage(upload.mediaId).status
    var wait = 500L
    repeat(40) {
        when (status) {
            "ready" -> return upload.mediaId
            "failed" -> throw ApiException(400, "image_failed", "That picture could not be used. Try another.")
        }
        delay(wait)
        wait = minOf(wait * 2, 3000L)
        status = api.imageStatus(upload.mediaId).status
    }
    throw ApiException(408, "image_slow", "That picture is taking too long. Try again.")
}

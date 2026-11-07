package ng.heynaija.app.media

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ng.heynaija.app.api.ApiException
import ng.heynaija.app.api.HeyNaijaApi
import ng.heynaija.app.api.VideoLimits
import ng.heynaija.app.api.VideoPlan
import ng.heynaija.app.api.VideoStatus
import ng.heynaija.app.platform.PickedImage
import ng.heynaija.app.platform.PickedVideo

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

private const val VIDEO_TYPES = "video/mp4 video/quicktime video/webm"

private fun mmss(ms: Long) = "${ms / 60_000}:${((ms / 1000) % 60).toString().padStart(2, '0')}"

/** Why a video cannot be sent, in the web's words (apps/web/lib/video.ts); null when it can. */
fun videoProblem(video: PickedVideo, limits: VideoLimits): String? {
    if (video.contentType !in VIDEO_TYPES.split(' ')) return "Choose an MP4, MOV or WebM video."
    if (video.bytes <= 0) return "That file is empty."
    if (video.bytes > limits.maxBytes) return "That video is ${video.bytes / (1024 * 1024)} MB. The limit is ${limits.maxBytes / (1024 * 1024)} MB."
    val ms = video.durationMs ?: return null
    if (ms > limits.maxSeconds * 1000L) return "That video is ${mmss(ms)} long. The limit is ${limits.maxSeconds} seconds."
    if (ms < limits.minSeconds * 1000L) return "That video is too short. The least is ${limits.minSeconds} seconds."
    return null
}

/** What to tell the member when the worker refused their video, by the API's reason. */
fun rejectionText(reason: String?, limits: VideoLimits?): String = when (reason) {
    "too_long" -> "That video is too long. The limit is ${limits?.maxSeconds ?: 180} seconds."
    "too_short" -> "That video is too short. The least is ${limits?.minSeconds ?: 3} seconds."
    "too_large" -> "That video is too large."
    "too_big_picture" -> "That video's picture is too big. Record it at a lower quality."
    "not_a_video" -> "That file could not be read as a video."
    else -> "That video could not be used."
}

/** Parts in flight at once, and tries per part, as on the web. */
private const val PARALLEL = 2
private const val RETRIES = 3

/**
 * Sends a video's parts straight to storage, two at a time and each retried, so
 * a dropped connection costs one part; [progress] hears the share sent (0 to 1).
 * Then asks the API to assemble it, and answers with its status.
 */
suspend fun sendVideo(api: HeyNaijaApi, video: PickedVideo, plan: VideoPlan, progress: (Float) -> Unit): VideoStatus {
    val sent = mutableMapOf<Int, Long>().apply { plan.done.forEach { put(it.partNumber, it.size) } }
    val lock = Mutex()
    suspend fun report() = lock.withLock { progress((sent.values.sum().toFloat() / video.bytes).coerceAtMost(0.99f)) }
    report()
    val queue = ArrayDeque(plan.parts)
    coroutineScope {
        List(PARALLEL) {
            async {
                while (true) {
                    val part = lock.withLock { queue.removeFirstOrNull() } ?: break
                    val start = (part.partNumber - 1) * plan.partSize
                    var attempt = 0
                    while (true) {
                        try {
                            val data = withContext(Dispatchers.IO) { video.read(start, part.bytes.toInt()) }
                            api.putVideoPart(part, data)
                            lock.withLock { sent[part.partNumber] = part.bytes }
                            report()
                            break
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (++attempt >= RETRIES) throw e
                            delay(1000L shl attempt)
                        }
                    }
                }
            }
        }.awaitAll()
    }
    return api.completeVideoUpload(plan.mediaId)
}

/** Waits for the worker: about 25 minutes, its limit for a 3-minute clip after any wait in its queue. */
suspend fun awaitVideo(api: HeyNaijaApi, mediaId: String): VideoStatus {
    repeat(750) { i ->
        val status = runCatching { api.videoStatus(mediaId) }.getOrNull()
        if (status != null && status.status != "processing" && status.status != "uploading") return status
        delay(if (i < 5) 1500 else 2000)
    }
    return VideoStatus("failed")
}

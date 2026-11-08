package ng.heynaija.app.platform

import androidx.compose.runtime.Composable

/** Small strings kept between launches. */
interface KeyValueStore {
    fun get(key: String): String?
    fun set(key: String, value: String?)
}

/**
 * What each platform gives the shared app: [secure] holds the session token
 * (the Android Keystore, the iOS Keychain); [settings] holds everything else.
 */
class Platform(
    val secure: KeyValueStore,
    val settings: KeyValueStore,
    /** A debug build: Settings offers the dev stack as a server. */
    val debug: Boolean,
    /** A Gist's sound; null where there is none (tests), and Gists then say they cannot play. */
    val gistAudio: GistAudioFactory? = null,
    /** Push notifications; null where there are none: an Android build without Firebase, tests. */
    val push: PushDevice? = null,
)

/** A picture chosen to post or to use as a profile photo, ready to upload. */
class PickedImage(
    val bytes: ByteArray,
    /** image/jpeg, image/png, image/webp or image/gif: what the API takes. */
    val contentType: String,
    val width: Int,
    val height: Int,
)

/**
 * Opens the system's photo picker for up to [max] pictures (1 for a profile
 * photo). Photos are turned into JPEG no larger than 2048 on their long edge, as
 * the web does before upload; a GIF is sent as it is.
 */
@Composable
expect fun rememberImagePicker(onPicked: (List<PickedImage>) -> Unit): (max: Int) -> Unit

/** The system's share sheet, for a post's link. */
@Composable
expect fun rememberShare(): (text: String) -> Unit

/** What a clip is doing, as the platform's player says, in milliseconds. */
data class VideoState(
    /** Not paused: playing, or waiting for more to arrive to play. */
    val playing: Boolean = false,
    val ended: Boolean = false,
    val positionMs: Long = 0,
    /** 0 until the playlist says. */
    val durationMs: Long = 0,
    /** How far it has loaded from where it is. */
    val bufferedMs: Long = 0,
    val muted: Boolean = false,
    val waiting: Boolean = false,
    val failed: Boolean = false,
)

/**
 * One clip in the platform's own player (Media3's ExoPlayer, AVFoundation's
 * AVPlayer), without the platform's controls: HeyNaija draws its own, as the web
 * does (media/VideoPlayer.kt).
 */
interface VideoHandle {
    val state: androidx.compose.runtime.State<VideoState>
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setMuted(muted: Boolean)
}

/** A player for the HLS playlist [url], from [startMs], playing as soon as it can. It is let go when it leaves the composition. */
@Composable
expect fun rememberVideoHandle(url: String, startMs: Long): VideoHandle

/** The picture alone, letterboxed in black inside [modifier]. */
@Composable
expect fun VideoSurface(handle: VideoHandle, modifier: androidx.compose.ui.Modifier)

/**
 * A video chosen to post. It stays on the device and is read a part at a time
 * ([read]), so a clip of up to 1 GB never has to fit in memory.
 */
class PickedVideo(
    /** video/mp4, video/quicktime or video/webm. */
    val contentType: String,
    val bytes: Long,
    val durationMs: Long?,
    val width: Int,
    val height: Int,
    /** Its first frame as a JPEG, to show while it uploads. */
    val thumbnail: ByteArray?,
    /** [length] bytes from [offset]; blocking, so call it off the main thread. */
    val read: (offset: Long, length: Int) -> ByteArray,
)

/** Opens the system's picker for one video. */
@Composable
expect fun rememberVideoPicker(onPicked: (PickedVideo) -> Unit): () -> Unit

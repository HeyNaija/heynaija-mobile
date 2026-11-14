package ng.heynaija.app.platform

import androidx.compose.runtime.Composable

/**
 * The sound of one visit to a Gist's room, through LiveKit: its Android SDK in
 * androidMain, and on iOS the Swift wrapper in `iosApp` (LiveKit's Swift SDK has no
 * Objective-C interface, so Kotlin cannot call it itself). Callbacks rather than
 * suspend functions, so Swift implements it plainly. Calls and callbacks are on the
 * main thread.
 */
interface GistAudio {
    /** Joins the room and plays everyone on stage. [done] gets null, or the SDK's words for what failed. */
    fun connect(url: String, token: String, done: (String?) -> Unit)

    /**
     * Publishes the microphone, or mutes it. Ask for the system's permission first
     * ([rememberMicrophonePermission]), and turn it on only once the room allows it
     * ([GistAudioEvents.onMayPublish]): before that LiveKit refuses. [done] gets
     * null, or what failed.
     */
    fun setMicrophone(on: Boolean, done: (String?) -> Unit)

    /**
     * Plays the room's sound, or silences it on this phone: the system's pause, from
     * the lock screen or headphones. The member stays in the room. A connection
     * starts with the sound on, and speakers who start later follow this.
     */
    fun setHearing(on: Boolean)

    /** Leaves the room. No events follow. */
    fun disconnect()
}

/** What the room says while connected. Member ids are the LiveKit identities the API gives out. */
interface GistAudioEvents {
    fun onReconnecting()

    fun onReconnected()

    /** The connection ended by itself: [reason] is 'duplicate', 'removed', 'room_deleted' or 'network'. */
    fun onLost(reason: String)

    /** Who is speaking now. */
    fun onSpeaking(ids: List<String>)

    /** Who has a live, unmuted microphone, the member included. */
    fun onMics(ids: List<String>)

    /** Whose connection the server sees as poor or lost, so a breaking voice is named as theirs. */
    fun onWeak(ids: List<String>)

    /** The server took the member's microphone: a host muted them, or made them a listener. */
    fun onMicOff()

    /**
     * Whether the room lets the member publish their microphone. Every connection
     * starts as a listener's: the server allows a host, co-host or speaker a
     * moment after they connect, and anyone else when a host lets them speak.
     */
    fun onMayPublish(allowed: Boolean)
}

/** Makes a [GistAudio] for each visit to a room. */
interface GistAudioFactory {
    fun create(events: GistAudioEvents): GistAudio
}

/**
 * The Gist the member is in, as the system is told: for the notification Android
 * shows while it plays, and iOS's Now Playing. [hearing] is false while its sound is
 * paused.
 */
data class GistNow(
    val id: String,
    val title: String,
    val micOn: Boolean,
    val reconnecting: Boolean,
    val host: String = "",
    val hearing: Boolean = true,
)

/**
 * Tells the system about the Gist the member is in, outside the app. Android stops
 * an app's sound and microphone soon after it leaves the screen unless a foreground
 * service, with a notification, says they are in use (androidApp's GistService).
 * iOS keeps the sound going with the audio background mode alone; its keeper puts
 * the Gist on the lock screen, in Control Center and the Dynamic Island, as Now
 * Playing (iosApp's GistNowPlaying.swift).
 */
interface GistKeeper {
    /**
     * In a room, or something about it changed. [leave] leaves it: the Android
     * notification's button. [hear] plays or silences its sound: iOS's play and pause.
     */
    fun keep(now: GistNow, leave: () -> Unit, hear: (Boolean) -> Unit)

    /** Out of the room: the system stops showing it. */
    fun stop()
}

/**
 * The system's microphone permission: the returned function asks for it when it has
 * not been given (Android's RECORD_AUDIO, iOS's record permission), and [onResult]
 * hears whether the microphone may be used.
 */
@Composable
expect fun rememberMicrophonePermission(onResult: (Boolean) -> Unit): () -> Unit

/** Keeps the screen awake while it is shown, as the web's wake lock does in a Gist. */
@Composable
expect fun KeepScreenOn()

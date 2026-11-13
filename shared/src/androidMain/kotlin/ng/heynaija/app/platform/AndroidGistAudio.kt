package ng.heynaija.app.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.DisconnectReason
import io.livekit.android.events.ParticipantEvent
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.AudioTrackPublishDefaults
import io.livekit.android.room.participant.AudioTrackPublishOptions
import io.livekit.android.room.participant.ConnectionQuality
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.LocalAudioTrack
import io.livekit.android.room.track.Track
import io.livekit.android.util.flow
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Voice, not music: 32 kbps Opus is full speech and leaves room on patchy mobile data, as on the web. */
private const val VOICE_BITRATE = 32_000

class LiveKitGistAudioFactory(private val context: Context, private val debug: Boolean) : GistAudioFactory {
    override fun create(events: GistAudioEvents): GistAudio = LiveKitGistAudio(context.applicationContext, events, debug)
}

/**
 * A Gist's room through LiveKit's Android SDK, which plays every speaker by itself
 * and routes the sound to earphones when they are in. The microphone is a track of
 * our own, unpublished and stopped when muted, so the system's recording indicator
 * goes off with it.
 */
private class LiveKitGistAudio(context: Context, private val on: GistAudioEvents, private val debug: Boolean) : GistAudio {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val room: Room = LiveKit.create(
        context,
        RoomOptions(
            // RED repeats each moment of sound in the next packets, so a lost one costs nothing; DTX
            // sends almost nothing in silence. LiveKit's defaults, stated so they stay on.
            audioTrackPublishDefaults = AudioTrackPublishDefaults(audioBitrate = VOICE_BITRATE, dtx = true, red = true),
        ),
    )
    private var mic: LocalAudioTrack? = null
    private var closing = false

    private val Participant.id get() = identity?.value
    private val everyone get() = listOf<Participant>(room.localParticipant) + room.remoteParticipants.values

    // Published and not muted, as the server has it. Participant.isMicrophoneEnabled waits for a remote track to be
    // subscribed and updates a beat after the events, so a speaker would show as muted for a while.
    private fun emitMics() = on.onMics(
        everyone.filter { p -> p.getTrackPublication(Track.Source.MICROPHONE)?.let { !it.muted } == true }.mapNotNull { it.id },
    )

    private fun emitWeak() = on.onWeak(
        everyone.filter { it.connectionQuality == ConnectionQuality.POOR || it.connectionQuality == ConnectionQuality.LOST }.mapNotNull { it.id },
    )

    init {
        scope.launch {
            room.events.collect { event ->
                when (event) {
                    is RoomEvent.Reconnecting -> on.onReconnecting()
                    is RoomEvent.Reconnected -> on.onReconnected()
                    is RoomEvent.Disconnected -> if (!closing) on.onLost(lostReason(event.reason))
                    is RoomEvent.ActiveSpeakersChanged -> on.onSpeaking(event.speakers.mapNotNull { it.id })
                    is RoomEvent.TrackMuted -> {
                        // We never mute our own track (it is unpublished instead), so this is the server: a host muted us.
                        if (event.participant == room.localParticipant) takeMicBack()
                        emitMics()
                    }
                    is RoomEvent.TrackUnmuted, is RoomEvent.TrackPublished, is RoomEvent.TrackUnpublished,
                    is RoomEvent.ParticipantConnected, is RoomEvent.ParticipantDisconnected -> {
                        emitMics()
                        emitWeak()
                    }
                    is RoomEvent.ConnectionQualityChanged -> emitWeak()
                    is RoomEvent.TrackSubscribed -> {
                        if (debug) println("HeyNaija: hearing ${event.participant.id}")
                        emitMics()
                    }
                    else -> Unit
                }
            }
        }
        scope.launch {
            // Every token is a listener's; the server lets a speaker publish a moment after they connect, or when a host
            // lets them speak. Read from the participant's own state, which holds the answer whenever it came: the
            // room's event for it is dropped when the answer arrives while the connection is still being made.
            room.localParticipant::permissions.flow.collect { permissions ->
                val allowed = permissions?.canPublish == true
                if (debug) println("HeyNaija: the room ${if (allowed) "lets" else "does not let"} this member publish")
                on.onMayPublish(allowed)
            }
        }
        scope.launch {
            room.localParticipant.events.collect { event ->
                when (event) {
                    is ParticipantEvent.LocalTrackPublished -> emitMics()
                    is ParticipantEvent.LocalTrackUnpublished -> {
                        // Ours are unpublished through setMicrophone, which forgets the track first; any other is the server's
                        // doing (made a listener), so the button must not keep saying "on".
                        if (event.publication.track != null && event.publication.track == mic) takeMicBack()
                        emitMics()
                    }
                    else -> Unit
                }
            }
        }
    }

    /** The server took the microphone: stop it here too, and say so. */
    private fun takeMicBack() {
        val track = mic ?: return
        mic = null
        room.localParticipant.unpublishTrack(track, stopOnUnpublish = true)
        track.dispose()
        on.onMicOff()
    }

    override fun connect(url: String, token: String, done: (String?) -> Unit) {
        scope.launch {
            try {
                room.connect(url, token)
                emitMics()
                emitWeak()
                done(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                done(e.message ?: e.toString())
            }
        }
    }

    override fun setMicrophone(on: Boolean, done: (String?) -> Unit) {
        scope.launch {
            try {
                if (!on) {
                    mic?.let { track ->
                        mic = null
                        room.localParticipant.unpublishTrack(track, stopOnUnpublish = true)
                        track.dispose()
                    }
                    emitMics()
                    done(null)
                    return@launch
                }
                if (mic != null) return@launch done(null)
                val track = room.localParticipant.createAudioTrack("microphone")
                val published = try {
                    // Throws when the server has not let this member publish (GistAudio.setMicrophone).
                    room.localParticipant.publishAudioTrack(
                        track,
                        AudioTrackPublishOptions(name = "microphone", audioBitrate = VOICE_BITRATE, source = Track.Source.MICROPHONE),
                    )
                } catch (e: Throwable) {
                    // Not published, so the room will not let it go: do it here, or the track is left behind.
                    track.dispose()
                    throw e
                }
                if (!published) {
                    track.dispose()
                    return@launch done("The microphone could not be published.")
                }
                mic = track
                emitMics()
                done(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                done("denied")
            } catch (e: Exception) {
                done(e.message ?: e.toString())
            }
        }
    }

    override fun disconnect() {
        closing = true
        // The room unpublishes the microphone and lets it go as it closes. Disposing it here first crashed the app
        // when leaving with the microphone on: the room then asked the disposed track for its id, on the main thread
        // ("MediaStreamTrack has been disposed").
        mic = null
        try {
            room.disconnect()
            room.release()
        } catch (e: Exception) {
            // Leaving must never take the app down with it.
            if (debug) println("HeyNaija: the Gist's room did not close cleanly: $e")
        }
        scope.cancel()
    }
}

/** Why LiveKit ended the connection, in the terms the room acts on: only a network drop is worth retrying. */
private fun lostReason(reason: DisconnectReason): String = when (reason) {
    DisconnectReason.DUPLICATE_IDENTITY -> "duplicate"
    DisconnectReason.PARTICIPANT_REMOVED -> "removed"
    DisconnectReason.ROOM_DELETED, DisconnectReason.ROOM_CLOSED -> "room_deleted"
    else -> "network"
}

@Composable
actual fun rememberMicrophonePermission(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val current = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> current.value(granted) }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) current.value(true)
        else launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
}

@Composable
actual fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

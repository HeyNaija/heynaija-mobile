import Foundation
import LiveKit
import Shared

// A Gist's sound on iOS. LiveKit's Swift SDK has no Objective-C interface, so the
// shared Kotlin code cannot call it; it defines GistAudio (shared/src/commonMain/
// kotlin/ng/heynaija/app/platform/GistAudio.kt) and this file implements it.
// Kotlin calls in on the main thread and hears back on the main thread.

/** Voice, not music: 32 kbps Opus is full speech and leaves room on patchy mobile data, as on the web. */
private let voiceBitrate = 32_000

final class LiveKitGistAudioFactory: NSObject, GistAudioFactory {
    func create(events: any GistAudioEvents) -> any GistAudio {
        LiveKitGistAudio(events: events)
    }
}

/**
 * One visit to a room. LiveKit plays every speaker by itself and sets up the audio
 * session (playback while listening, voice chat with echo cancellation while the
 * microphone is on). Muting unpublishes the microphone, so iOS's recording
 * indicator goes off with it.
 */
final class LiveKitGistAudio: NSObject, GistAudio, RoomDelegate, @unchecked Sendable {
    private let events: any GistAudioEvents
    private let room: Room
    /** Set while we change the microphone ourselves, so our own unpublish is not taken for the server's. */
    private var changingMic = false
    private var closing = false
    /**
     * Held from joining to leaving, so the audio session stays active between speakers. Muting unpublishes
     * the microphone here, so while nobody on stage has theirs on the room has no sound at all, and LiveKit
     * would switch the session off. With the app in the background iOS does not let a session be switched
     * back on, so the next speaker would not be heard until the app was opened again.
     */
    private var session: SessionRequirementHandle?
    /** False while the member has paused the sound: the lock screen's pause, or AirPods taken out. Main thread. */
    private var hearing = true

    init(events: any GistAudioEvents) {
        self.events = events
        // RED repeats each moment of sound in the next packets, so a lost one costs nothing; DTX sends
        // almost nothing in silence. LiveKit's defaults, stated so they stay on.
        let publish = AudioPublishOptions(encoding: AudioEncoding(maxBitrate: voiceBitrate), dtx: true, red: true)
        room = Room(roomOptions: RoomOptions(defaultAudioPublishOptions: publish))
        super.init()
        room.add(delegate: self)
    }

    // MARK: GistAudio

    func connect(url: String, token: String, done: @escaping (String?) -> Void) {
        // Before LiveKit's audio starts: a listener's session must not mix, or iOS will not show the Gist as Now Playing.
        UnmixedGistSession.install()
        // Joining is a tap in the app, so the session may be switched on now.
        session = try? AudioManager.shared.acquireSessionRequirement(.playbackOnly)
        UnmixedGistSession.unmix()
        Task {
            do {
                try await room.connect(url: url, token: token)
                onMain { [self] in
                    emitMics()
                    emitWeak()
                    events.onMayPublish(allowed: room.localParticipant.permissions.canPublish)
                    done(nil)
                }
            } catch {
                onMain { done(error.localizedDescription) }
            }
        }
    }

    func setMicrophone(on: Bool, done: @escaping (String?) -> Void) {
        Task {
            changingMic = true
            defer { changingMic = false }
            do {
                if on {
                    try await room.localParticipant.setMicrophone(enabled: true)
                } else if let publication = room.localParticipant.audioTracks.first(where: { $0.source == .microphone }) as? LocalTrackPublication {
                    try await room.localParticipant.unpublish(publication: publication)
                }
                onMain { [self] in
                    emitMics()
                    done(nil)
                }
            } catch {
                let denied = (error as? LiveKitError)?.type == .deviceAccessDenied
                onMain { done(denied ? "denied" : error.localizedDescription) }
            }
        }
    }

    func setHearing(on: Bool) {
        hearing = on
        for participant in room.remoteParticipants.values {
            for case let publication as RemoteTrackPublication in participant.audioTracks {
                quiet(publication, on: on)
            }
        }
    }

    /** Silences one speaker here at once, and asks the server to stop sending their sound, which saves data while paused. */
    private func quiet(_ publication: RemoteTrackPublication, on: Bool) {
        (publication.track as? RemoteAudioTrack)?.volume = on ? 1 : 0
        Task { try? await publication.set(enabled: on) }
    }

    func disconnect() {
        closing = true
        let held = session
        session = nil
        Task {
            await room.disconnect()
            try? held?.release()
        }
    }

    // MARK: RoomDelegate (any thread)

    func room(_: Room, didStartReconnectWithMode _: ReconnectMode) {
        onMain { [self] in events.onReconnecting() }
    }

    func room(_: Room, didCompleteReconnectWithMode _: ReconnectMode) {
        onMain { [self] in events.onReconnected() }
    }

    func room(_: Room, didDisconnectWithError error: LiveKitError?) {
        guard !closing else { return }
        // Why decides what comes next: only a network drop is worth trying again.
        let reason = switch error?.type {
        case .duplicateIdentity: "duplicate"
        case .participantRemoved: "removed"
        case .roomDeleted: "room_deleted"
        default: "network"
        }
        onMain { [self] in events.onLost(reason: reason) }
    }

    func room(_: Room, didUpdateSpeakingParticipants participants: [Participant]) {
        let ids = participants.compactMap { $0.identity?.stringValue }
        onMain { [self] in events.onSpeaking(ids: ids) }
    }

    func room(_: Room, participant: Participant, trackPublication _: TrackPublication, didUpdateIsMuted isMuted: Bool) {
        // We never mute our own track (it is unpublished instead), so this is the server: a host muted us.
        if isMuted, participant is LocalParticipant, !changingMic { takeMicBack() }
        onMain { [self] in emitMics() }
    }

    func room(_: Room, participant _: LocalParticipant, didUnpublishTrack _: LocalTrackPublication) {
        // Not ours: the server took the microphone (made a listener), so the button must not keep saying "on".
        if !changingMic { onMain { [self] in events.onMicOff() } }
        onMain { [self] in emitMics() }
    }

    func room(_: Room, participant _: LocalParticipant, didPublishTrack _: LocalTrackPublication) {
        onMain { [self] in emitMics() }
    }

    func room(_: Room, participant: Participant, didUpdatePermissions permissions: ParticipantPermissions) {
        // Every token is a listener's; the server lets a speaker publish a moment after they connect, or when a host lets them speak.
        guard participant is LocalParticipant else { return }
        let allowed = permissions.canPublish
        onMain { [self] in events.onMayPublish(allowed: allowed) }
    }

    func room(_: Room, participant _: RemoteParticipant, didPublishTrack _: RemoteTrackPublication) {
        onMain { [self] in emitMics() }
    }

    func room(_: Room, participant _: RemoteParticipant, didUnpublishTrack _: RemoteTrackPublication) {
        onMain { [self] in emitMics() }
    }

    func room(_: Room, participant: RemoteParticipant, didSubscribeTrack publication: RemoteTrackPublication) {
        #if DEBUG
        print("HeyNaija: hearing \(participant.identity?.stringValue ?? "someone")")
        #endif
        // Someone who starts speaking while the member has paused stays quiet too.
        onMain { [self] in
            if !hearing { quiet(publication, on: false) }
        }
    }

    func room(_: Room, participantDidConnect _: RemoteParticipant) {
        onMain { [self] in
            emitMics()
            emitWeak()
        }
    }

    func room(_: Room, participantDidDisconnect _: RemoteParticipant) {
        onMain { [self] in
            emitMics()
            emitWeak()
        }
    }

    func room(_: Room, participant _: Participant, didUpdateConnectionQuality _: ConnectionQuality) {
        onMain { [self] in emitWeak() }
    }

    // MARK: Helpers

    private var everyone: [Participant] {
        [room.localParticipant] + Array(room.remoteParticipants.values)
    }

    private func emitMics() {
        events.onMics(ids: everyone.filter { $0.isMicrophoneEnabled() }.compactMap { $0.identity?.stringValue })
    }

    private func emitWeak() {
        let weak = everyone.filter { $0.connectionQuality == .poor || $0.connectionQuality == .lost }
        events.onWeak(ids: weak.compactMap { $0.identity?.stringValue })
    }

    /** The server muted the microphone: stop it here too, and say so. */
    private func takeMicBack() {
        Task {
            changingMic = true
            defer { changingMic = false }
            if let publication = room.localParticipant.audioTracks.first(where: { $0.source == .microphone }) as? LocalTrackPublication {
                try? await room.localParticipant.unpublish(publication: publication)
            }
            onMain { [self] in
                events.onMicOff()
                emitMics()
            }
        }
    }

    private func onMain(_ work: @escaping @Sendable () -> Void) {
        DispatchQueue.main.async(execute: work)
    }
}

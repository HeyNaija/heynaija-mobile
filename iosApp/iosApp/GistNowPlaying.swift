import AVFAudio
import LiveKit
import MediaPlayer
import Shared
import UIKit

// The Gist the member is in, outside the app. Android shows a notification while
// a Gist plays (androidApp's GistService); iOS has no lasting notifications, and an
// app playing sound shows as Now Playing instead: on the lock screen, in Control
// Center and in the Dynamic Island, as a podcast does. Kotlin tells this keeper
// about the room (GistKeeper in shared/src/commonMain/kotlin/ng/heynaija/app/
// platform/GistAudio.kt) on the main thread.

/** The app's icon, for the artwork beside the Gist's title. */
private let artwork: MPMediaItemArtwork? = UIImage(named: "GistArtwork").map { image in
    MPMediaItemArtwork(boundsSize: image.size) { _ in image }
}

/**
 * The Gist as Now Playing: its title, who hosts it or what the member's microphone is
 * doing, and the app's icon; a tap opens the app. Pause silences the Gist on this
 * phone and play brings the sound back, while the member stays in the room. iOS
 * pauses it too when AirPods come out, so a Gist does not carry on out loud.
 */
final class NowPlayingGistKeeper: NSObject, GistKeeper {
    private var hear: ((KotlinBoolean) -> Void)?
    private var hearing = true
    /** The command handlers added, to take away on leaving: another app's sound is not ours to answer. */
    private var handlers: [(MPRemoteCommand, Any)] = []

    func keep(now: GistNow, leave _: @escaping () -> Void, hear: @escaping (KotlinBoolean) -> Void) {
        self.hear = hear
        hearing = now.hearing
        if handlers.isEmpty { listen() }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: now.title.isEmpty ? "Gist" : now.title,
            MPMediaItemPropertyArtist: subtitle(now),
            MPMediaItemPropertyAlbumTitle: "HeyNaija",
            // Live: no progress bar and no skipping.
            MPNowPlayingInfoPropertyIsLiveStream: true,
            MPNowPlayingInfoPropertyMediaType: MPNowPlayingInfoMediaType.audio.rawValue,
            MPNowPlayingInfoPropertyPlaybackRate: now.hearing ? 1.0 : 0.0,
        ]
        if let artwork { info[MPMediaItemPropertyArtwork] = artwork }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    func stop() {
        for (command, handler) in handlers { command.removeTarget(handler) }
        handlers = []
        hear = nil
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }

    /** The line under the title: what is wrong first, then the microphone, then who hosts it. The same words as the app's Gist bar. */
    private func subtitle(_ now: GistNow) -> String {
        if now.reconnecting { return "Reconnecting…" }
        if !now.hearing { return "Paused. You are still in the Gist." }
        if now.micOn { return "Your microphone is on" }
        return now.host.isEmpty ? "Live Gist" : "Live · Hosted by \(now.host)"
    }

    /** Play and pause, from the lock screen, Control Center, headphones and AirPods. iOS shows an app as Now Playing only once it answers one. */
    private func listen() {
        let center = MPRemoteCommandCenter.shared()
        add(center.playCommand) { $0.set(hearing: true) }
        add(center.pauseCommand) { $0.set(hearing: false) }
        add(center.togglePlayPauseCommand) { $0.set(hearing: !$0.hearing) }
    }

    private func add(_ command: MPRemoteCommand, _ act: @escaping (NowPlayingGistKeeper) -> Bool) {
        command.isEnabled = true
        let handler = command.addTarget { [weak self] _ in
            guard let self, act(self) else { return .noActionableNowPlayingItem }
            return .success
        }
        handlers.append((command, handler))
    }

    private func set(hearing on: Bool) -> Bool {
        guard let hear else { return false }
        hear(KotlinBoolean(bool: on))
        return true
    }
}

/**
 * Keeps a listener's audio session from mixing with other apps' sound. LiveKit gives
 * a listener a mixable session, and iOS shows only an app whose sound does not mix
 * as Now Playing; a speaker's session already does not mix. Joining a Gist pauses
 * music from another app, as starting a podcast does.
 *
 * It sits before LiveKit's own observer (AudioManager.audioSession), which sets the
 * session up as the engine starts and stops, and tidies up after it each time.
 */
final class UnmixedGistSession: AudioEngineObserver, @unchecked Sendable {
    var next: (any AudioEngineObserver)?

    private static let installed: Void = {
        let audio = AudioManager.shared
        audio.set(engineObservers: [UnmixedGistSession(), audio.audioSession, audio.mixer])
    }()

    /** Puts it in LiveKit's chain, once, before the first room's audio starts. */
    static func install() {
        _ = installed
    }

    func engineWillEnable(_ engine: AVAudioEngine, isPlayoutEnabled: Bool, isRecordingEnabled: Bool) -> Int {
        let result = next?.engineWillEnable(engine, isPlayoutEnabled: isPlayoutEnabled, isRecordingEnabled: isRecordingEnabled) ?? 0
        Self.unmix()
        return result
    }

    func engineDidDisable(_ engine: AVAudioEngine, isPlayoutEnabled: Bool, isRecordingEnabled: Bool) -> Int {
        let result = next?.engineDidDisable(engine, isPlayoutEnabled: isPlayoutEnabled, isRecordingEnabled: isRecordingEnabled) ?? 0
        Self.unmix()
        return result
    }

    /** LiveKit's listening session, without mixing. Anything else (a speaker's, or none) is left as it is. */
    static func unmix() {
        let session = AVAudioSession.sharedInstance()
        guard session.category == .playback, session.categoryOptions.contains(.mixWithOthers) else { return }
        do {
            try session.setCategory(.playback, mode: session.mode, options: session.categoryOptions.subtracting(.mixWithOthers))
        } catch {
            #if DEBUG
            print("HeyNaija: the Gist's audio session would not stop mixing: \(error)")
            #endif
        }
    }
}

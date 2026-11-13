package ng.heynaija.app

import androidx.compose.ui.window.ComposeUIViewController
import kotlin.experimental.ExperimentalNativeApi
import ng.heynaija.app.platform.AppleAgeRange
import ng.heynaija.app.platform.GistAudioFactory
import ng.heynaija.app.platform.GistKeeper
import ng.heynaija.app.platform.iosPlatform

/**
 * The app for iosApp's SwiftUI view. [gistAudio], [gistKeeper] and [ageRange] are
 * Swift: the wrapper around LiveKit (GistAudio.swift), which Kotlin cannot call
 * itself, the Gist as Now Playing (GistNowPlaying.swift), beside LiveKit's audio
 * session, and Apple's Declared Age Range (AgeRange.swift).
 */
@OptIn(ExperimentalNativeApi::class)
fun MainViewController(gistAudio: GistAudioFactory, gistKeeper: GistKeeper, ageRange: AppleAgeRange) = ComposeUIViewController {
    App(iosPlatform(debug = kotlin.native.Platform.isDebugBinary, gistAudio = gistAudio, gistKeeper = gistKeeper, ageRange = ageRange))
}

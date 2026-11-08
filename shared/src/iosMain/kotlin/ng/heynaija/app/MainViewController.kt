package ng.heynaija.app

import androidx.compose.ui.window.ComposeUIViewController
import kotlin.experimental.ExperimentalNativeApi
import ng.heynaija.app.platform.AppleAgeRange
import ng.heynaija.app.platform.GistAudioFactory
import ng.heynaija.app.platform.iosPlatform

/**
 * The app for iosApp's SwiftUI view. [gistAudio] and [ageRange] are the Swift
 * wrappers around LiveKit (GistAudio.swift) and Apple's Declared Age Range
 * (AgeRange.swift), which Kotlin cannot call itself.
 */
@OptIn(ExperimentalNativeApi::class)
fun MainViewController(gistAudio: GistAudioFactory, ageRange: AppleAgeRange) =
    ComposeUIViewController { App(iosPlatform(debug = kotlin.native.Platform.isDebugBinary, gistAudio = gistAudio, ageRange = ageRange)) }

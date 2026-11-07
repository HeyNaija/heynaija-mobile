package ng.heynaija.app

import androidx.compose.ui.window.ComposeUIViewController
import kotlin.experimental.ExperimentalNativeApi
import ng.heynaija.app.platform.GistAudioFactory
import ng.heynaija.app.platform.iosPlatform

/** The app for iosApp's SwiftUI view; [gistAudio] is the Swift wrapper around LiveKit (GistAudio.swift). */
@OptIn(ExperimentalNativeApi::class)
fun MainViewController(gistAudio: GistAudioFactory) =
    ComposeUIViewController { App(iosPlatform(debug = kotlin.native.Platform.isDebugBinary, gistAudio = gistAudio)) }

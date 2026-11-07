package ng.heynaija.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVAudioSessionRecordPermissionUndetermined
import platform.UIKit.UIApplication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

// A Gist's sound on iOS is the Swift wrapper in iosApp (GistAudio.swift), passed in
// through MainViewController: LiveKit's Swift SDK cannot be called from Kotlin.

@Composable
actual fun rememberMicrophonePermission(onResult: (Boolean) -> Unit): () -> Unit {
    val current = rememberUpdatedState(onResult)
    return remember {
        {
            val session = AVAudioSession.sharedInstance()
            when (session.recordPermission) {
                AVAudioSessionRecordPermissionGranted -> current.value(true)
                // Only the first time: after a no, iOS answers no at once, and Settings is the way back.
                AVAudioSessionRecordPermissionUndetermined -> session.requestRecordPermission { granted ->
                    dispatch_async(dispatch_get_main_queue()) { current.value(granted) }
                }
                else -> current.value(false)
            }
        }
    }
}

@Composable
actual fun KeepScreenOn() {
    DisposableEffect(Unit) {
        UIApplication.sharedApplication.idleTimerDisabled = true
        onDispose { UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}

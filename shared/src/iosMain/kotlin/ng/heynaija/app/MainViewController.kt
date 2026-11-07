package ng.heynaija.app

import androidx.compose.ui.window.ComposeUIViewController
import kotlin.experimental.ExperimentalNativeApi
import ng.heynaija.app.platform.iosPlatform

@OptIn(ExperimentalNativeApi::class)
fun MainViewController() = ComposeUIViewController { App(iosPlatform(debug = kotlin.native.Platform.isDebugBinary)) }

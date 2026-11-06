package ng.heynaija.app

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import ng.heynaija.app.api.HeyNaijaApi
import ng.heynaija.app.feed.FeedScreen
import ng.heynaija.app.feed.FeedViewModel
import ng.heynaija.app.theme.HeyNaijaTheme

/** The whole app, the same on Android and iOS. */
@Composable
fun App() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
    }
    HeyNaijaTheme {
        FeedScreen(viewModel { FeedViewModel(HeyNaijaApi()) })
    }
}

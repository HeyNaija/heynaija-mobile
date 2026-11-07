package ng.heynaija.app.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import ng.heynaija.app.AppState
import ng.heynaija.app.nav.Entry
import ng.heynaija.app.nav.Route
import ng.heynaija.app.theme.HeyNaija

/**
 * A reply, or the next update to a story, written in a sheet over the feed or
 * thread it was asked from, so the member stays where they were. The composer is the full one (pictures,
 * mentions). Once something is written, only its own close button or posting
 * shuts the sheet: a stray swipe or tap outside does not throw the draft away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplySheet(app: AppState, route: Route.Compose) {
    var dirty by remember(route) { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || !dirty })
    // Its own view models, gone with the sheet, so the next reply starts empty.
    val owner = remember(route) { Entry(route) }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    val close = { app.replying = null }
    ModalBottomSheet(
        onDismissRequest = close,
        sheetState = sheet,
        containerColor = HeyNaija.colors.bg,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            Box(Modifier.fillMaxHeight(0.94f)) {
                ComposeScreen(route, onClose = close, onDraft = { dirty = it })
            }
        }
    }
}

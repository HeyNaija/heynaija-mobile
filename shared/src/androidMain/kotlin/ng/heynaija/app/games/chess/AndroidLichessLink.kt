package ng.heynaija.app.games.chess

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Lichess's page in the phone's browser. Its answer comes back to the app through
 * the `ng.heynaija.app://lichess` intent filter on MainActivity, which hands it to
 * [LichessReturn].
 */
@Composable
actual fun rememberLichessLink(): LichessLink {
    val context = LocalContext.current
    return remember(context) {
        object : LichessLink {
            override fun open(url: String) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
                try {
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    // No browser at all: nothing to open it in.
                }
            }
        }
    }
}

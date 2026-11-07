package ng.heynaija.app

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ng.heynaija.app.platform.androidPlatform

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val debug = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val platform = androidPlatform(applicationContext, debug)
        setContent { App(platform) }
    }
}

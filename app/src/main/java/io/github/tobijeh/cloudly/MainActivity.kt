package io.github.tobijeh.cloudly

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.tobijeh.cloudly.tv.TvPresenter
import io.github.tobijeh.cloudly.ui.CloudlyRoot
import io.github.tobijeh.cloudly.ui.CloudlyTheme
import io.github.tobijeh.cloudly.ui.TvCloud

class MainActivity : ComponentActivity() {
    private val graph get() = (application as CloudlyApp).graph
    private lateinit var tv: TvPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tv = TvPresenter(this) { CloudlyTheme { TvCloud(graph) } }
        setContent {
            CloudlyTheme {
                val tvName by tv.displayName.collectAsState()
                val state by graph.controller.state.collectAsState()
                var mirrorRequested by rememberSaveable { mutableStateOf(false) }
                // A real presentation display takes over the cloud; the phone goes back to controls.
                val mirror = mirrorRequested && tvName == null

                LaunchedEffect(mirror) {
                    // Lock the cloud view to landscape; controls follow the device.
                    requestedOrientation = if (mirror) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    val insets = WindowCompat.getInsetsController(window, window.decorView)
                    if (mirror) {
                        insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        insets.hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        insets.show(WindowInsetsCompat.Type.systemBars())
                    }
                }
                // Keep the screen awake while a session is active: on air on a TV, mirroring, or listening.
                val keepAwake = tvName != null || mirror || state.listening
                DisposableEffect(keepAwake) {
                    if (keepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    onDispose { }
                }
                BackHandler(enabled = mirror) { mirrorRequested = false }

                CloudlyRoot(graph, tvName, mirror, onToggleMirror = { mirrorRequested = !mirror })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        tv.start()
    }

    override fun onStop() {
        tv.stop()
        super.onStop()
    }
}

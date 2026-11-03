package io.github.tobijeh.cloudly

import android.app.Application
import io.github.tobijeh.cloudly.core.CloudController
import io.github.tobijeh.cloudly.core.CloudState
import io.github.tobijeh.cloudly.data.EpisodeStore
import io.github.tobijeh.cloudly.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** App-wide singletons shared by the phone UI and the TV Presentation. */
class AppGraph(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsStore(app)
    val episodes = EpisodeStore(app.filesDir)
    val controller = CloudController(initial = CloudState(words = episodes.loadCurrent()))

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch { settings.settings.collect { controller.settings = it } }
        // Auto-release ticker.
        scope.launch {
            while (isActive) {
                controller.tick()
                delay(1_000)
            }
        }
        // Debounced autosave of the word list (never the transcript).
        scope.launch(Dispatchers.IO) {
            controller.state.map { it.words }.distinctUntilChanged().drop(1).debounce(500).collect {
                runCatching { episodes.saveCurrent(it) }
            }
        }
    }
}

class CloudlyApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this).also { it.start() }
    }
}

package io.github.tobijeh.cloudly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.tobijeh.cloudly.AppGraph
import io.github.tobijeh.cloudly.core.CloudController
import io.github.tobijeh.cloudly.core.Episode
import io.github.tobijeh.cloudly.core.Word
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** How long the Undo snackbar for a deleted word stays up. */
const val UNDO_WINDOW_MS = 5_000L

private sealed interface DialogState {
    data object AddWord : DialogState
    data class EditVariants(val word: Word) : DialogState
    data object SaveEpisode : DialogState
    data class LoadEpisode(val episodes: List<Episode>) : DialogState
    data object Settings : DialogState
    data object ConfirmClear : DialogState
}

/** The cloud alone, for the TV Presentation. */
@Composable
fun TvCloud(graph: AppGraph) {
    val state by graph.controller.state.collectAsState()
    val settings by graph.settings.settings.collectAsState()
    CloudView(state, settings, Modifier.fillMaxSize())
}

@Composable
fun CloudlyRoot(graph: AppGraph, tvName: String?, mirror: Boolean, onToggleMirror: () -> Unit) {
    val state by graph.controller.state.collectAsState()
    val settings by graph.settings.settings.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<DialogState?>(null) }

    val actions = remember(onToggleMirror) {
        object : ControlsActions {
            override val controller: CloudController = graph.controller
            override fun addWord() { dialog = DialogState.AddWord }
            override fun editVariants(word: Word) { dialog = DialogState.EditVariants(word) }
            override fun message(text: String) { scope.launch { snackbar.showSnackbar(text) } }
            override fun delete(word: Word) {
                val deleted = controller.delete(word.id) ?: return
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val timer = launch { delay(UNDO_WINDOW_MS); snackbar.currentSnackbarData?.dismiss() }
                    val r = snackbar.showSnackbar("Deleted “${word.display}”", actionLabel = "Undo", duration = SnackbarDuration.Indefinite)
                    timer.cancel()
                    if (r == SnackbarResult.ActionPerformed) controller.restore(deleted)
                }
            }
            override fun saveEpisode() { dialog = DialogState.SaveEpisode }
            override fun loadEpisode() { dialog = DialogState.LoadEpisode(graph.episodes.list()) }
            override fun openSettings() { dialog = DialogState.Settings }
            override fun clearAll() { dialog = DialogState.ConfirmClear }
            override fun toggleMirror() = onToggleMirror()
        }
    }

    if (mirror) {
        MirrorScreen(graph, actions, tvName)
    } else {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            ControlsContent(state, settings, actions, mirror = false, tvName = tvName, showPreview = true, contentPadding = padding)
        }
    }
    if (mirror) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.BottomCenter) { SnackbarHost(snackbar) }
    }

    when (val d = dialog) {
        null -> Unit
        DialogState.AddWord -> WordEditorDialog(null, onDismiss = { dialog = null }) { text, variants ->
            graph.controller.addWord(text, variants); dialog = null
        }
        is DialogState.EditVariants -> ListenForDialog(d.word, onDismiss = { dialog = null }) { variants ->
            graph.controller.setVariants(d.word.id, variants); dialog = null
        }
        DialogState.SaveEpisode -> SaveEpisodeDialog(
            defaultName = "Episode " + SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
            onDismiss = { dialog = null },
        ) { name ->
            graph.episodes.save(name, graph.controller.state.value.words)
            dialog = null
            scope.launch { snackbar.showSnackbar("Saved “$name”") }
        }
        is DialogState.LoadEpisode -> LoadEpisodeDialog(
            episodes = d.episodes,
            onDismiss = { dialog = null },
            onLoad = { e, keep -> graph.controller.replaceWords(e.words, keep); dialog = null },
            onDelete = { e -> graph.episodes.delete(e.name); dialog = DialogState.LoadEpisode(graph.episodes.list()) },
        )
        DialogState.Settings -> SettingsDialog(settings, onDismiss = { dialog = null }) { s -> graph.settings.update { s } }
        DialogState.ConfirmClear -> ConfirmDialog(
            title = "Clear all words?",
            message = "This removes every word and its count from the cloud. Saved episodes are not affected.",
            confirm = "Clear all",
            onDismiss = { dialog = null },
        ) { graph.controller.clearAll(); dialog = null }
    }
}

/** Mirroring only: the phone shows the cloud full screen with the controls in a pull-out drawer. */
@Composable
private fun MirrorScreen(graph: AppGraph, actions: ControlsActions, tvName: String?) {
    val state by graph.controller.state.collectAsState()
    val settings by graph.settings.settings.collectAsState()
    val drawer = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(Modifier.width(400.dp)) {
                ControlsContent(state, settings, actions, mirror = true, tvName = tvName, showPreview = true)
            }
        },
    ) {
        Box(Modifier.fillMaxSize().background(CloudBackground)) {
            CloudView(state, settings, Modifier.fillMaxSize())
            IconButton(
                onClick = { scope.launch { drawer.open() } },
                modifier = Modifier.padding(12.dp).background(Color(0x22FFFFFF), CircleShape),
            ) { Icon(Icons.Filled.Menu, "Controls", tint = Color(0x66FFFFFF)) }
        }
    }
}

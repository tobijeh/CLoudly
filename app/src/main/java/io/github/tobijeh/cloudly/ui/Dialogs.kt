package io.github.tobijeh.cloudly.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tobijeh.cloudly.core.CloudSettings
import io.github.tobijeh.cloudly.core.Episode
import io.github.tobijeh.cloudly.core.Word
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Add or edit a word: "Display" text (any script) plus "Listen for" variants. */
@Composable
fun WordEditorDialog(
    initial: Word?,
    onDismiss: () -> Unit,
    onSave: (display: String, variants: List<String>) -> Unit,
) {
    var display by remember { mutableStateOf(initial?.display ?: "") }
    val variants = remember { mutableStateListOf<String>().apply { addAll(initial?.variants.orEmpty()) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add word" else "Edit word") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = display,
                    onValueChange = { display = it },
                    label = { Text("Display") },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = CloudFonts.forText(display), fontSize = 22.sp, textDirection = TextDirection.Content),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text("Listen for", style = MaterialTheme.typography.titleSmall)
                Text(
                    "The display text is always listened for. Add other spellings, transliterations or forms " +
                        "the transcript might use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                variants.forEachIndexed { i, v ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = v,
                            onValueChange = { variants[i] = it },
                            singleLine = true,
                            textStyle = TextStyle(textDirection = TextDirection.Content),
                            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                        )
                        IconButton(onClick = { variants.removeAt(i) }) { Icon(Icons.Filled.Close, "Remove variant") }
                    }
                }
                TextButton(onClick = { variants.add("") }) {
                    Icon(Icons.Filled.Add, null)
                    Text("Add variant")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = display.isNotBlank(), onClick = { onSave(display, variants.toList()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Hold menu → Listen-for spellings: extra spellings, transliterations or forms that count for this word. */
@Composable
fun ListenForDialog(word: Word, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    val variants = remember { mutableStateListOf<String>().apply { addAll(word.variants); if (isEmpty()) add("") } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Listen for “${word.display}”",
                fontFamily = CloudFonts.forText(word.display),
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "“${word.display}” itself always counts. Add other spellings, transliterations or forms " +
                        "the transcript might use. Earlier mentions in this session are counted straight away.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                variants.forEachIndexed { i, v ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = v,
                            onValueChange = { variants[i] = it },
                            singleLine = true,
                            textStyle = TextStyle(textDirection = TextDirection.Content),
                            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                        )
                        IconButton(onClick = { variants.removeAt(i) }) { Icon(Icons.Filled.Close, "Remove spelling") }
                    }
                }
                TextButton(onClick = { variants.add("") }) {
                    Icon(Icons.Filled.Add, null)
                    Text("Add spelling")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(variants.toList()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SaveEpisodeDialog(defaultName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save word list") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Episode name") }, singleLine = true)
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun LoadEpisodeDialog(
    episodes: List<Episode>,
    onDismiss: () -> Unit,
    onLoad: (Episode, keepCounts: Boolean) -> Unit,
    onDelete: (Episode) -> Unit,
) {
    var chosen by remember { mutableStateOf<Episode?>(null) }
    val c = chosen
    if (c != null) {
        AlertDialog(
            onDismissRequest = { chosen = null },
            title = { Text("Load “${c.name}”?") },
            text = { Text("This replaces the current words. Keep the saved mention counts, or start every word at zero?") },
            confirmButton = { TextButton(onClick = { onLoad(c, true) }) { Text("With counts") } },
            dismissButton = { TextButton(onClick = { onLoad(c, false) }) { Text("Words only") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Load word list") },
        text = {
            if (episodes.isEmpty()) {
                Text("No saved episodes yet.")
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(episodes, key = { it.name }) { e ->
                        Row(
                            Modifier.fillMaxWidth().clickable { chosen = e }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(e.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${e.words.size} words · " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(e.savedAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onDelete(e) }) { Icon(Icons.Filled.Delete, "Delete episode") }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun SettingsDialog(settings: CloudSettings, onDismiss: () -> Unit, onChange: (CloudSettings) -> Unit) {
    var visible by remember { mutableFloatStateOf(settings.maxVisible.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Visible words: ${visible.roundToInt()}", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = visible,
                    onValueChange = { visible = it },
                    onValueChangeFinished = { onChange(settings.copy(maxVisible = visible.roundToInt())) },
                    valueRange = CloudSettings.MIN_VISIBLE.toFloat()..CloudSettings.MAX_VISIBLE.toFloat(),
                    steps = CloudSettings.MAX_VISIBLE - CloudSettings.MIN_VISIBLE - 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Count taps as mentions", style = MaterialTheme.typography.titleSmall)
                        Text("Locking a word adds one mention", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = settings.countTapsAsMentions,
                        onCheckedChange = { onChange(settings.copy(countTapsAsMentions = it)) },
                    )
                }
                Text("Auto-release centre lock", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CloudSettings.AUTO_RELEASE_CHOICES_MIN.forEach { min ->
                        val ms = min?.let { it * 60_000L }
                        FilterChip(
                            selected = settings.autoReleaseMs == ms,
                            onClick = { onChange(settings.copy(autoReleaseMs = ms)) },
                            label = { Text(if (min == null) "Off" else "$min min") },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

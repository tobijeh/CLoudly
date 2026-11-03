package io.github.tobijeh.cloudly.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tobijeh.cloudly.core.CloudController
import io.github.tobijeh.cloudly.core.CloudSettings
import io.github.tobijeh.cloudly.core.CloudState
import io.github.tobijeh.cloudly.core.Word

/** Everything the controls need to talk back to the app. */
interface ControlsActions {
    val controller: CloudController
    fun addWord()
    fun edit(word: Word)
    fun delete(word: Word)
    fun saveEpisode()
    fun loadEpisode()
    fun openSettings()
    fun clearAll()
    fun toggleMirror()
}

fun statusLine(state: CloudState): String {
    val listen = if (state.listening) "Listening" else "Paused"
    val mode = state.lock?.let { l -> "Locked on ${state.word(l.wordId)?.display ?: "?"}" } ?: "Auto"
    return "$listen · $mode"
}

@Composable
fun ControlsHeader(state: CloudState, actions: ControlsActions, mirror: Boolean, tvName: String?) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Cloudly", style = MaterialTheme.typography.headlineSmall, fontFamily = CloudFonts.fraunces)
                Text(statusLine(state), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }
            FilledTonalButton(onClick = { actions.controller.setListening(!state.listening) }) {
                Icon(if (state.listening) Icons.Filled.Pause else Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text(if (state.listening) "Pause" else "Resume")
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Save word list…") }, leadingIcon = { Icon(Icons.Filled.Save, null) }, onClick = { menu = false; actions.saveEpisode() })
                    DropdownMenuItem(text = { Text("Load word list…") }, leadingIcon = { Icon(Icons.Filled.FolderOpen, null) }, onClick = { menu = false; actions.loadEpisode() })
                    DropdownMenuItem(
                        text = { Text(if (mirror) "Exit full-screen cloud" else "Full-screen cloud (mirroring)") },
                        leadingIcon = { Icon(if (mirror) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, null) },
                        onClick = { menu = false; actions.toggleMirror() },
                    )
                    DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(Icons.Filled.Settings, null) }, onClick = { menu = false; actions.openSettings() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Clear all words…") }, leadingIcon = { Icon(Icons.Outlined.ClearAll, null) }, onClick = { menu = false; actions.clearAll() })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.lock != null) {
                AssistChip(onClick = { actions.controller.releaseLock() }, label = { Text("Auto") }, leadingIcon = { Icon(Icons.Filled.AutoMode, null) })
            }
            Icon(Icons.Filled.Tv, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                when {
                    tvName != null -> "TV: $tvName"
                    mirror -> "Full-screen cloud for mirroring"
                    else -> "No TV display. Use full-screen cloud to mirror."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The phone's main screen: header, live preview, add button, and the word list. */
@Composable
fun ControlsContent(
    state: CloudState,
    settings: CloudSettings,
    actions: ControlsActions,
    mirror: Boolean,
    tvName: String?,
    showPreview: Boolean,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val c = actions.controller
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        ControlsHeader(state, actions, mirror, tvName)
        if (showPreview) {
            CloudView(
                state = state,
                settings = settings,
                emptyHint = "Add a word to start the cloud",
                onTapWord = c::tap,
                onPinch = c::emphasize,
                onPinchEnd = c::endEmphasis,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Text(
                "Tap a word to lock it in the centre. Pinch out on a word to enlarge it on the TV.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        OutlinedButton(onClick = actions::addWord, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().height(52.dp)) {
            Icon(Icons.Filled.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Add word", fontSize = 18.sp)
        }
        val maxCount = state.maxCount
        val words = state.wordsByRecent
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(words, key = { it.id }) { w ->
                WordRow(
                    word = w,
                    maxCount = maxCount,
                    locked = state.lock?.wordId == w.id,
                    centre = state.centreId == w.id,
                    onTap = { c.tap(w.id) },
                    onEdit = { actions.edit(w) },
                    onToggleHidden = { c.setHidden(w.id, !w.hidden) },
                    onReset = { c.resetCount(w.id) },
                    onDelete = { actions.delete(w) },
                    modifier = Modifier.animateItem(),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WordRow(
    word: Word,
    maxCount: Int,
    locked: Boolean,
    centre: Boolean,
    onTap: () -> Unit,
    onEdit: () -> Unit,
    onToggleHidden: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val bar by animateFloatAsState(if (maxCount <= 0) 0f else word.count / maxCount.toFloat(), tween(600), label = "bar")
    val color = wordColor(word.id)
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp)
                .background(if (centre) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background)
                .combinedClickable(onClick = onTap, onLongClick = { menu = true })
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (locked) Icon(Icons.Filled.Lock, "Locked", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp).padding(end = 6.dp))
                    if (word.hidden) Icon(Icons.Filled.VisibilityOff, "Hidden from TV", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp).padding(end = 6.dp))
                    Text(
                        word.display,
                        fontFamily = CloudFonts.forText(word.display),
                        fontSize = 26.sp,
                        color = if (word.hidden) MaterialTheme.colorScheme.onSurfaceVariant else color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                    )
                }
                if (word.variants.isNotEmpty()) {
                    Text(
                        word.variants.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { bar },
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth(0.85f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
            AnimatedContent(
                targetState = word.count,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
                label = "count",
            ) { n ->
                Text("$n", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 12.dp))
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; onEdit() })
            DropdownMenuItem(
                text = { Text(if (word.hidden) "Show on TV" else "Hide from TV") },
                leadingIcon = { Icon(if (word.hidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, null) },
                onClick = { menu = false; onToggleHidden() },
            )
            DropdownMenuItem(text = { Text("Reset count") }, leadingIcon = { Icon(Icons.Filled.RestartAlt, null) }, onClick = { menu = false; onReset() })
            DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { menu = false; onDelete() })
        }
    }
}

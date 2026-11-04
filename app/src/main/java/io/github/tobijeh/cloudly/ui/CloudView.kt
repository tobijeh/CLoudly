package io.github.tobijeh.cloudly.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tobijeh.cloudly.core.CloudLayout
import io.github.tobijeh.cloudly.core.CloudSettings
import io.github.tobijeh.cloudly.core.CloudState
import io.github.tobijeh.cloudly.core.PlacedWord
import io.github.tobijeh.cloudly.core.TextBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot

/** Slow, calm movement: ~2 s glide with a soft ease in and out. */
const val MOVE_DURATION_MS = 2000

/** Drift-back after a drag: slower still, so the return is barely noticeable on camera. */
const val DRIFT_DURATION_MS = 4000

/** Second tap must land within this window to count as a double-tap. Shorter than Android's 300 ms default. */
const val DOUBLE_TAP_WINDOW_MS = 220L

private val CalmEasing = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)

/** Callbacks for the phone preview's gestures. Positions are in the preview's pixels. */
class CloudGestures(
    val onTap: (Long) -> Unit,
    val onDoubleTap: (Long, Offset) -> Unit,
    val onHold: (Long, Offset) -> Unit,
    /** Normalised position relative to the centre (-0.5..0.5 of width/height). */
    val onDrag: (Long, Float, Float) -> Unit,
    val onDragEnd: (Long, droppedOnBin: Boolean) -> Unit,
    val onPinch: (Long, Float) -> Unit,
    val onPinchEnd: () -> Unit,
)

private enum class Motion { Calm, Follow, Drift }

private data class Target(val x: Float, val y: Float, val size: Float, val alpha: Float, val motion: Motion)

private class WordAnim(t: Target, layout: TextLayoutResult, val color: Color) {
    val x = Animatable(t.x)
    val y = Animatable(t.y)
    val size = Animatable(t.size)
    val alpha = Animatable(t.alpha)
    var layout by mutableStateOf(layout)
    var target by mutableStateOf(t)
    var removing by mutableStateOf(false)
    var naturalX by mutableStateOf(t.x)
    var naturalY by mutableStateOf(t.y)
    var pinned by mutableStateOf(false)
}

/**
 * Renders the word cloud: full screen on the TV / in mirror mode, small as the phone preview.
 * With [gestures] (the preview): tap, double-tap, hold, drag (with bin), pinch. With [showGuides] the preview
 * also draws a faint outline where a dragged word will drift back to.
 */
@Composable
fun CloudView(
    state: CloudState,
    settings: CloudSettings,
    modifier: Modifier = Modifier,
    emptyHint: String? = null,
    gestures: CloudGestures? = null,
    showGuides: Boolean = false,
) {
    BoxWithConstraints(modifier.background(CloudBackground)) {
        val vw = constraints.maxWidth.toFloat()
        val vh = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val refPx = with(density) { REF_SIZE.toPx() }
        val measurer = rememberTextMeasurer(cacheSize = 0)
        val cache = remember(density) { TextCache(measurer) }
        val isPreview = gestures != null

        val visible = CloudLayout.visibleWords(state, settings.maxVisible)
        val centreId = state.centreId
        val boxes = visible.associate { w ->
            val tl = cache[w.display]
            w.id to TextBox(tl.size.width / refPx, tl.size.height / refPx)
        }
        val placements by produceState(emptyList<PlacedWord>(), visible, centreId, vw, vh, boxes) {
            value = withContext(Dispatchers.Default) {
                CloudLayout.layout(visible, centreId, { boxes.getValue(it.id) }, vw, vh)
            }
        }
        val displayById = visible.associate { it.id to it.display }
        val pins = state.pins

        val anims = remember { mutableStateMapOf<Long, WordAnim>() }

        // Update each word's target. Each word animates on its own (below), so one word following a
        // finger never restarts or disturbs the calm glides of the others.
        LaunchedEffect(placements, pins, vw, vh) {
            val ids = placements.mapTo(HashSet()) { it.id }
            for (p in placements) {
                val layout = cache[displayById[p.id] ?: continue]
                val pin = pins[p.id]
                val a = anims[p.id]
                val motion = when {
                    pin?.dragging == true -> Motion.Follow
                    pin == null && a?.pinned == true -> Motion.Drift
                    else -> Motion.Calm
                }
                val tx = pin?.let { it.nx * vw } ?: p.cx
                val ty = pin?.let { it.ny * vh } ?: p.cy
                val t = Target(tx, ty, p.fontSize, 1f, motion)
                if (a == null) {
                    // New words grow in place from small.
                    anims[p.id] = WordAnim(t.copy(size = p.fontSize * 0.2f, alpha = 0f), layout, wordColor(p.id)).also {
                        it.target = t
                        it.naturalX = p.cx; it.naturalY = p.cy; it.pinned = pin != null
                    }
                } else {
                    a.layout = layout
                    a.removing = false
                    a.naturalX = p.cx; a.naturalY = p.cy
                    a.pinned = pin != null
                    a.target = t
                }
            }
            for ((id, a) in anims) {
                if (id !in ids && !a.removing) {
                    a.removing = true
                    a.target = a.target.copy(alpha = 0f, motion = Motion.Calm)
                }
            }
        }

        for (id in anims.keys.toList()) {
            val a = anims[id] ?: continue
            key(id) {
                LaunchedEffect(a) {
                    snapshotFlow { a.target }.collectLatest { t ->
                        val spec: AnimationSpec<Float> = when (t.motion) {
                            // Preview tracks the finger exactly; the TV glides after it smoothly.
                            Motion.Follow -> if (isPreview) snap() else spring(dampingRatio = 1f, stiffness = 45f)
                            Motion.Drift -> tween(DRIFT_DURATION_MS, easing = CalmEasing)
                            Motion.Calm -> tween(MOVE_DURATION_MS, easing = CalmEasing)
                        }
                        coroutineScope {
                            launch { a.x.animateTo(t.x, spec) }
                            launch { a.y.animateTo(t.y, spec) }
                            launch { a.size.animateTo(t.size, tween(MOVE_DURATION_MS, easing = CalmEasing)) }
                            launch {
                                a.alpha.animateTo(t.alpha, tween(if (t.alpha == 0f) MOVE_DURATION_MS / 2 else MOVE_DURATION_MS, easing = CalmEasing))
                            }
                        }
                        if (a.removing && a.alpha.value == 0f) anims.remove(id)
                    }
                }
            }
        }

        // Pinch emphasis: follows fingers while active, springs back over ~1 s on release.
        val emphasis = state.emphasis
        val emphasisScale = remember { Animatable(1f) }
        LaunchedEffect(emphasis) {
            when {
                emphasis == null -> emphasisScale.snapTo(1f)
                emphasis.active -> emphasisScale.snapTo(emphasis.scale)
                else -> emphasisScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 40f))
            }
        }

        var draggingId by remember { mutableStateOf<Long?>(null) }
        var overBin by remember { mutableStateOf(false) }

        Canvas(Modifier.fillMaxSize()) {
            // Phone only: faint outline at the natural spot of any word that has been dragged away.
            if (showGuides) {
                for ((_, a) in anims) {
                    if (!a.pinned || a.removing) continue
                    val tl = a.layout
                    val s = a.size.value / refPx
                    translate(vw / 2 + a.naturalX, vh / 2 + a.naturalY) {
                        scale(s, s, pivot = Offset.Zero) {
                            drawText(
                                tl, color = a.color, topLeft = Offset(-tl.size.width / 2f, -tl.size.height / 2f),
                                alpha = 0.28f, drawStyle = Stroke(width = 1.2f / s.coerceAtLeast(0.01f)),
                            )
                        }
                    }
                }
            }
            val order = anims.entries.sortedWith(
                compareBy<Map.Entry<Long, WordAnim>> { it.key == emphasis?.wordId || it.key == draggingId }
                    .thenBy { it.value.size.value },
            )
            for ((id, a) in order) {
                val tl = a.layout
                val emph = if (id == emphasis?.wordId) emphasisScale.value else 1f
                val s = a.size.value / refPx * emph
                val fade = if (id == draggingId && overBin) 0.45f else 1f
                translate(vw / 2 + a.x.value, vh / 2 + a.y.value) {
                    scale(s, s, pivot = Offset.Zero) {
                        drawText(
                            tl, color = a.color, topLeft = Offset(-tl.size.width / 2f, -tl.size.height / 2f),
                            alpha = (a.alpha.value * fade).coerceIn(0f, 1f),
                            drawStyle = Fill, // explicit: the guide outline's Stroke would otherwise stick to the shared layout
                        )
                    }
                }
            }
        }

        if (visible.isEmpty() && emptyHint != null) {
            Text(emptyHint, color = Color(0xFF6B6F78), modifier = Modifier.align(Alignment.Center))
        }

        if (gestures != null) {
            val g by rememberUpdatedState(gestures)
            val binRadius = with(density) { 26.dp.toPx() }
            val binHit = binRadius * 1.9f
            val binCentre = Offset(vw / 2, vh - with(density) { 10.dp.toPx() } - binRadius)

            fun hit(pos: Offset, nearest: Boolean): Long? {
                var best: Long? = null
                var bestDist = Float.MAX_VALUE
                for ((id, a) in anims) {
                    if (a.removing || a.alpha.value < 0.3f) continue
                    val s = a.size.value / refPx
                    val cx = vw / 2 + a.x.value
                    val cy = vh / 2 + a.y.value
                    val hw = a.layout.size.width * s / 2 + 6f
                    val hh = a.layout.size.height * s / 2 + 6f
                    val inside = pos.x in (cx - hw)..(cx + hw) && pos.y in (cy - hh)..(cy + hh)
                    val d = hypot(pos.x - cx, pos.y - cy)
                    if ((inside || nearest) && d < bestDist) { best = id; bestDist = d }
                }
                return best
            }
            fun screenPos(id: Long): Offset? = anims[id]?.let { Offset(vw / 2 + it.x.value, vh / 2 + it.y.value) }

            Box(
                Modifier.fillMaxSize().pointerInput(vw, vh) {
                    val slop = viewConfiguration.touchSlop
                    val holdMs = viewConfiguration.longPressTimeoutMillis
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val target = hit(down.position, nearest = false)

                        // Phase 1: decide what this gesture is.
                        var kind = "hold"
                        var last: PointerEvent? = null
                        withTimeoutOrNull(holdMs) {
                            while (true) {
                                val ev = awaitPointerEvent()
                                last = ev
                                val pressed = ev.changes.filter { it.pressed }
                                when {
                                    pressed.isEmpty() -> { kind = "up"; return@withTimeoutOrNull }
                                    pressed.size >= 2 -> { kind = "pinch"; return@withTimeoutOrNull }
                                    (pressed[0].position - down.position).getDistance() > slop -> { kind = "drag"; return@withTimeoutOrNull }
                                }
                            }
                        }

                        when (kind) {
                            "up" -> {
                                if (target == null) return@awaitEachGesture
                                // Wait briefly for a second tap; a single tap acts as soon as the window closes.
                                val second = withTimeoutOrNull(DOUBLE_TAP_WINDOW_MS) { awaitFirstDown(requireUnconsumed = false) }
                                if (second != null && hit(second.position, nearest = false) == target) {
                                    consumeUntilUp()
                                    g.onDoubleTap(target, screenPos(target) ?: second.position)
                                } else {
                                    g.onTap(target)
                                    if (second != null) consumeUntilUp()
                                }
                            }
                            "hold" -> {
                                if (target != null) g.onHold(target, screenPos(target) ?: down.position)
                                consumeUntilUp()
                            }
                            "drag" -> {
                                if (target == null) { consumeUntilUp(); return@awaitEachGesture }
                                val a = anims[target]
                                val startX = a?.x?.value ?: 0f
                                val startY = a?.y?.value ?: 0f
                                draggingId = target
                                var pos = last?.changes?.firstOrNull()?.position ?: down.position
                                fun update(p: Offset) {
                                    pos = p
                                    overBin = (p - binCentre).getDistance() < binHit
                                    g.onDrag(target, (startX + p.x - down.position.x) / vw, (startY + p.y - down.position.y) / vh)
                                }
                                update(pos)
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: ev.changes.first()
                                    if (!ch.pressed) break
                                    update(ch.position)
                                    ev.changes.forEach(PointerInputChange::consume)
                                }
                                val binned = overBin
                                draggingId = null
                                overBin = false
                                g.onDragEnd(target, binned)
                            }
                            "pinch" -> {
                                var pinchId: Long? = null
                                var startDist = 0f
                                var ev = last
                                while (ev != null) {
                                    val pressed = ev.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) break
                                    if (pressed.size >= 2) {
                                        val p0 = pressed[0].position
                                        val p1 = pressed[1].position
                                        val dist = (p0 - p1).getDistance()
                                        if (pinchId == null) {
                                            pinchId = hit((p0 + p1) / 2f, nearest = true)
                                            startDist = dist
                                        } else if (startDist > 0f) {
                                            g.onPinch(pinchId, dist / startDist)
                                        }
                                    }
                                    ev.changes.forEach(PointerInputChange::consume)
                                    ev = awaitPointerEvent()
                                }
                                if (pinchId != null) g.onPinchEnd()
                            }
                        }
                    }
                },
            )

            // The bin: only visible while a word is being dragged.
            AnimatedVisibility(
                visible = draggingId != null,
                enter = fadeIn() + scaleIn(initialScale = 0.7f),
                exit = fadeOut() + scaleOut(targetScale = 0.7f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            ) {
                Box(
                    Modifier.size(52.dp).background(if (overBin) Color(0xCCE5484D) else Color(0x66FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Delete, "Drop here to delete", tint = Color.White) }
            }
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.consumeUntilUp() {
    while (true) {
        val ev = awaitPointerEvent()
        ev.changes.forEach(PointerInputChange::consume)
        if (ev.changes.none { it.pressed }) return
    }
}

private val REF_SIZE = 100.sp

/** Measures each word once at a reference size; drawing scales it, so animating size never re-measures text. */
private class TextCache(private val measurer: TextMeasurer) {
    private val map = HashMap<String, TextLayoutResult>()
    operator fun get(text: String): TextLayoutResult = map.getOrPut(text) {
        measurer.measure(
            AnnotatedString(text),
            style = TextStyle(
                fontFamily = CloudFonts.forText(text),
                fontSize = REF_SIZE,
                textDirection = TextDirection.Content, // Hebrew lays out right to left with vowel points intact
            ),
            softWrap = false,
            maxLines = 1,
        )
    }
}

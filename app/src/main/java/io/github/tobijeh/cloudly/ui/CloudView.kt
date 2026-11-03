package io.github.tobijeh.cloudly.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import io.github.tobijeh.cloudly.core.CloudLayout
import io.github.tobijeh.cloudly.core.CloudSettings
import io.github.tobijeh.cloudly.core.CloudState
import io.github.tobijeh.cloudly.core.PlacedWord
import io.github.tobijeh.cloudly.core.TextBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot

/** Slow, calm movement: ~2 s glide with a soft ease in and out. */
const val MOVE_DURATION_MS = 2000
private val CalmEasing = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)

private class WordAnim(
    x: Float, y: Float, size: Float, alpha: Float,
    layout: TextLayoutResult,
    val color: Color,
) {
    val x = Animatable(x)
    val y = Animatable(y)
    val size = Animatable(size)
    val alpha = Animatable(alpha)
    var layout by mutableStateOf(layout)
}

private val Animatable<Float, AnimationVector1D>.v get() = value

/**
 * Renders the word cloud. Used full screen on the TV / in mirror mode, and small as the phone's live preview.
 * When [onTapWord] / [onPinch] are given (preview), taps lock words and pinches emphasise them.
 */
@Composable
fun CloudView(
    state: CloudState,
    settings: CloudSettings,
    modifier: Modifier = Modifier,
    emptyHint: String? = null,
    onTapWord: ((Long) -> Unit)? = null,
    onPinch: ((Long, Float) -> Unit)? = null,
    onPinchEnd: (() -> Unit)? = null,
) {
    BoxWithConstraints(modifier.background(CloudBackground)) {
        val vw = constraints.maxWidth.toFloat()
        val vh = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val refPx = with(density) { REF_SIZE.toPx() }
        val measurer = rememberTextMeasurer(cacheSize = 0)
        val cache = remember(density) { TextCache(measurer) }

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

        val anims = remember { mutableStateMapOf<Long, WordAnim>() }
        LaunchedEffect(placements) {
            val spec = tween<Float>(MOVE_DURATION_MS, easing = CalmEasing)
            val ids = placements.mapTo(HashSet()) { it.id }
            for (p in placements) {
                val layout = cache[displayById[p.id] ?: continue]
                val a = anims[p.id]
                if (a == null) {
                    // New words grow in place from small.
                    val n = WordAnim(p.cx, p.cy, p.fontSize * 0.2f, 0f, layout, wordColor(p.id))
                    anims[p.id] = n
                    launch { n.size.animateTo(p.fontSize, spec) }
                    launch { n.alpha.animateTo(1f, spec) }
                } else {
                    a.layout = layout
                    launch { a.x.animateTo(p.cx, spec) }
                    launch { a.y.animateTo(p.cy, spec) }
                    launch { a.size.animateTo(p.fontSize, spec) }
                    launch { a.alpha.animateTo(1f, spec) }
                }
            }
            for (id in anims.keys.toList()) {
                if (id in ids) continue
                val a = anims[id] ?: continue
                launch {
                    a.alpha.animateTo(0f, tween(MOVE_DURATION_MS / 2, easing = CalmEasing))
                    anims.remove(id)
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

        Canvas(Modifier.fillMaxSize()) {
            val order = anims.entries.sortedWith(
                compareBy<Map.Entry<Long, WordAnim>> { it.key == emphasis?.wordId }.thenBy { it.value.size.v },
            )
            for ((id, a) in order) {
                val tl = a.layout
                val emph = if (id == emphasis?.wordId) emphasisScale.value else 1f
                val s = a.size.v / refPx * emph
                translate(vw / 2 + a.x.v, vh / 2 + a.y.v) {
                    scale(s, s, pivot = Offset.Zero) {
                        drawText(
                            tl,
                            color = a.color,
                            topLeft = Offset(-tl.size.width / 2f, -tl.size.height / 2f),
                            alpha = a.alpha.v.coerceIn(0f, 1f),
                        )
                    }
                }
            }
        }

        if (visible.isEmpty() && emptyHint != null) {
            Text(emptyHint, color = Color(0xFF6B6F78), modifier = Modifier.align(Alignment.Center))
        }

        if (onTapWord != null || onPinch != null) {
            val tap by rememberUpdatedState(onTapWord)
            val pinch by rememberUpdatedState(onPinch)
            val pinchEnd by rememberUpdatedState(onPinchEnd)
            fun hit(pos: Offset, nearest: Boolean): Long? {
                var best: Long? = null
                var bestDist = Float.MAX_VALUE
                for ((id, a) in anims) {
                    if (a.alpha.v < 0.3f) continue
                    val s = a.size.v / refPx
                    val cx = vw / 2 + a.x.v
                    val cy = vh / 2 + a.y.v
                    val hw = a.layout.size.width * s / 2
                    val hh = a.layout.size.height * s / 2
                    val inside = pos.x in (cx - hw)..(cx + hw) && pos.y in (cy - hh)..(cy + hh)
                    val d = hypot(pos.x - cx, pos.y - cy)
                    if ((inside || nearest) && d < bestDist) { best = id; bestDist = d }
                }
                return best
            }
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var pinchId: Long? = null
                        var startDist = 0f
                        var isTap = true
                        while (true) {
                            val ev = awaitPointerEvent()
                            val pressed = ev.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                isTap = false
                                val a = pressed[0].position
                                val b = pressed[1].position
                                val dist = (a - b).getDistance()
                                if (pinchId == null) {
                                    pinchId = hit((a + b) / 2f, nearest = true)
                                    startDist = dist
                                } else if (startDist > 0f) {
                                    pinch?.invoke(pinchId, dist / startDist)
                                }
                                ev.changes.forEach { it.consume() }
                            } else if ((pressed[0].position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                isTap = false
                            }
                        }
                        if (pinchId != null) pinchEnd?.invoke()
                        else if (isTap) hit(down.position, nearest = false)?.let { tap?.invoke(it) }
                    }
                },
            )
        }
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

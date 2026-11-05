package io.github.tobijeh.cloudly.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Size of a word's text box per 1px of font size (i.e. width/fontSize, height/fontSize). */
data class TextBox(val width: Float, val height: Float)

/** A word placed on the cloud. Coordinates are relative to the viewport centre, in px. */
data class PlacedWord(
    val id: Long,
    val cx: Float,
    val cy: Float,
    val fontSize: Float,
    val width: Float,
    val height: Float,
    val isCentre: Boolean,
)

/**
 * Word-cloud layout. Pure and deterministic so the same inputs always give the same picture.
 *
 * - The centre word is drawn at a fixed large size ([CENTRE_HEIGHT_FRACTION] of the viewport height),
 *   regardless of its count (shrunk only if the word is too wide to fit).
 * - Other words are sized relative to each other by mention count (square-root scale), then packed
 *   around the centre along an elliptical spiral so the cloud is rounded and matches the screen shape.
 * - When the cloud no longer fits, all surrounding words scale down together (same proportions)
 *   until it does.
 */
object CloudLayout {
    const val CENTRE_HEIGHT_FRACTION = 0.20f
    const val CENTRE_MAX_WIDTH_FRACTION = 0.80f
    const val MIN_RELATIVE = 0.22f
    const val MAX_RELATIVE = 0.60f
    const val FIT_WIDTH = 0.94f
    const val FIT_HEIGHT = 0.90f
    const val GAP_FRACTION = 0.10f
    private const val MIN_SCALE = 0.08f
    private const val SEARCH_ITERATIONS = 10
    private const val MAX_SPIRAL_STEPS = 6000

    /** The centre word plus the top (maxVisible - 1) other non-hidden words by count, then recency. */
    fun visibleWords(state: CloudState, maxVisible: Int): List<Word> {
        val centre = state.word(state.centreId)
        val others = state.words
            .filter { !it.hidden && it.id != centre?.id }
            .sortedWith(compareByDescending<Word> { it.count }.thenByDescending { it.lastActivityAt }.thenBy { it.id })
            .take((maxVisible - if (centre != null) 1 else 0).coerceAtLeast(0))
        return listOfNotNull(centre) + others
    }

    /** Size of a non-centre word relative to the centre word's fixed size. */
    fun relativeSize(count: Int, maxCount: Int): Float {
        if (maxCount <= 0) return MIN_RELATIVE
        val t = sqrt(count.coerceAtLeast(0).toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
        return MIN_RELATIVE + (MAX_RELATIVE - MIN_RELATIVE) * t
    }

    fun layout(
        words: List<Word>,
        centreId: Long?,
        boxes: (Word) -> TextBox,
        viewportWidth: Float,
        viewportHeight: Float,
        /**
         * Words the host dragged somewhere: id → centre position in px relative to the viewport centre.
         * They sit exactly there; words they bump into move to the nearest free spot, the rest stay put.
         */
        fixed: Map<Long, Pair<Float, Float>> = emptyMap(),
    ): List<PlacedWord> {
        if (words.isEmpty() || viewportWidth <= 0f || viewportHeight <= 0f) return emptyList()
        if (fixed.isNotEmpty()) return pushAround(words, centreId, boxes, viewportWidth, viewportHeight, fixed)
        val centre = words.firstOrNull { it.id == centreId }
        val others = words.filter { it.id != centre?.id }
        val maxCount = words.maxOf { it.count }

        val centreBox = centre?.let(boxes)
        val centreFont = centreBox?.let {
            min(viewportHeight * CENTRE_HEIGHT_FRACTION, viewportWidth * CENTRE_MAX_WIDTH_FRACTION / it.width.coerceAtLeast(0.01f))
        } ?: (viewportHeight * CENTRE_HEIGHT_FRACTION)

        val sorted = others
            .map { Triple(it, boxes(it), relativeSize(it.count, maxCount)) }
            .sortedWith(compareByDescending<Triple<Word, TextBox, Float>> { it.third }.thenBy { it.first.id })

        fun attempt(scale: Float): List<PlacedWord> {
            val placed = ArrayList<PlacedWord>(words.size)
            if (centre != null && centreBox != null) {
                placed += PlacedWord(centre.id, 0f, 0f, centreFont, centreBox.width * centreFont, centreBox.height * centreFont, true)
            }
            for ((w, box, rel) in sorted) {
                val font = centreFont * rel * scale
                placed += place(w.id, font, box, placed, viewportWidth, viewportHeight)
            }
            return placed
        }

        fun fits(p: List<PlacedWord>): Boolean {
            var maxX = 0f
            var maxY = 0f
            for (r in p) {
                maxX = max(maxX, abs(r.cx) + r.width / 2)
                maxY = max(maxY, abs(r.cy) + r.height / 2)
            }
            return 2 * maxX <= viewportWidth * FIT_WIDTH && 2 * maxY <= viewportHeight * FIT_HEIGHT
        }

        val full = attempt(1f)
        if (fits(full)) return full
        var lo = MIN_SCALE
        var hi = 1f
        var best = attempt(lo)
        repeat(SEARCH_ITERATIONS) {
            val mid = (lo + hi) / 2
            val p = attempt(mid)
            if (fits(p)) { lo = mid; best = p } else hi = mid
        }
        return best
    }

    /**
     * Sandbox push: start from the normal layout, put dragged words at their spots, and move only the words
     * they bump into, each to the nearest free spot around where it was. Words out of the way don't move.
     */
    private fun pushAround(
        words: List<Word>,
        centreId: Long?,
        boxes: (Word) -> TextBox,
        vw: Float,
        vh: Float,
        fixed: Map<Long, Pair<Float, Float>>,
    ): List<PlacedWord> {
        val base = layout(words, centreId, boxes, vw, vh)
        val placed = ArrayList<PlacedWord>(base.size)
        for (b in base) fixed[b.id]?.let { (fx, fy) -> placed += b.copy(cx = fx, cy = fy) }
        // Centre first, then larger words, so the big ones keep their spots and small ones squeeze around.
        val rest = base.filter { it.id !in fixed }.sortedWith(compareByDescending<PlacedWord> { it.isCentre }.thenByDescending { it.fontSize })
        for (b in rest) {
            val gap = max(2f, GAP_FRACTION * b.height)
            placed += if (placed.none { overlaps(b.cx, b.cy, b.width, b.height, it, gap) }) b
            else place(b.id, b.fontSize, TextBox(b.width / b.fontSize, b.height / b.fontSize), placed, vw, vh, b.cx, b.cy)
                .copy(isCentre = b.isCentre)
        }
        return placed
    }

    private fun place(
        id: Long,
        font: Float,
        box: TextBox,
        placed: List<PlacedWord>,
        vw: Float,
        vh: Float,
        originX: Float = 0f,
        originY: Float = 0f,
    ): PlacedWord {
        val w = box.width * font
        val h = box.height * font
        val gap = max(2f, GAP_FRACTION * h)
        val aspect = (vw / vh).coerceIn(1f, 2.4f)
        val ringSpacing = vh * 0.008f // spiral radius growth per radian
        val stepLen = vh * 0.012f
        // Each word starts its spiral in its own direction so the cloud spreads evenly and stays stable.
        var theta = ((id * 2654435761L) and 0xFFFF).toDouble() / 0xFFFF * 2 * Math.PI
        var travelled = 0.0
        var cx = 0f
        var cy = 0f
        for (step in 0 until MAX_SPIRAL_STEPS) {
            val r = ringSpacing * travelled
            cx = originX + (r * cos(theta) * aspect).toFloat() + 0f // + 0f turns -0.0 into 0.0
            cy = originY + (r * sin(theta)).toFloat() + 0f
            if (placed.none { overlaps(cx, cy, w, h, it, gap) }) break
            val dTheta = stepLen / max(r, stepLen.toDouble())
            theta += dTheta
            travelled += dTheta
        }
        return PlacedWord(id, cx, cy, font, w, h, false)
    }

    private fun overlaps(cx: Float, cy: Float, w: Float, h: Float, o: PlacedWord, gap: Float): Boolean =
        abs(cx - o.cx) < (w + o.width) / 2 + gap && abs(cy - o.cy) < (h + o.height) / 2 + gap
}

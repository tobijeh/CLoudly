package io.github.tobijeh.cloudly.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CloudLayoutTest {
    private val vw = 1920f
    private val vh = 1080f
    // Fake measurement: ~0.55 em per character, 1.25 em line height.
    private val boxes: (Word) -> TextBox = { TextBox(0.55f * it.display.length.coerceAtLeast(1), 1.25f) }

    private fun words(n: Int) = (1..n).map { i -> Word(i.toLong(), "word$i", count = (i * 37) % 23, lastActivityAt = i.toLong()) }

    private fun overlap(a: PlacedWord, b: PlacedWord) =
        abs(a.cx - b.cx) < (a.width + b.width) / 2 && abs(a.cy - b.cy) < (a.height + b.height) / 2

    @Test
    fun `no two words overlap and everything fits the screen`() {
        for (n in listOf(1, 5, 12, 20, 30)) {
            val ws = words(n)
            val placed = CloudLayout.layout(ws, centreId = 1, boxes = boxes, viewportWidth = vw, viewportHeight = vh)
            assertEquals(n, placed.size)
            for (i in placed.indices) for (j in i + 1 until placed.size) {
                assertFalse("overlap n=$n ${placed[i]} ${placed[j]}", overlap(placed[i], placed[j]))
            }
            for (p in placed) {
                assertTrue("fits x n=$n", abs(p.cx) + p.width / 2 <= vw / 2)
                assertTrue("fits y n=$n", abs(p.cy) + p.height / 2 <= vh / 2)
            }
        }
    }

    @Test
    fun `centre word sits in the centre at a fixed size regardless of count`() {
        val few = listOf(Word(1, "hope", count = 0), Word(2, "faith", count = 50))
        val many = words(30).map { if (it.id == 1L) it.copy(display = "hope", count = 999) else it }
        val a = CloudLayout.layout(few, 1, boxes, vw, vh).single { it.isCentre }
        val b = CloudLayout.layout(many, 1, boxes, vw, vh).single { it.isCentre }
        assertEquals(0f, a.cx); assertEquals(0f, a.cy)
        assertEquals(vh * CloudLayout.CENTRE_HEIGHT_FRACTION, a.fontSize, 0.01f)
        assertEquals(a.fontSize, b.fontSize, 0.01f)
    }

    @Test
    fun `other words are sized by count and scale down together as the cloud fills`() {
        val base = listOf(
            Word(1, "centre", count = 1),
            Word(2, "big", count = 16),
            Word(3, "small", count = 4),
        )
        val sparse = CloudLayout.layout(base, 1, boxes, vw, vh).associateBy { it.id }
        assertTrue(sparse.getValue(2).fontSize > sparse.getValue(3).fontSize)

        val filler = (10L..40L).map { Word(it, "fillerword$it", count = 16) }
        val full = CloudLayout.layout(base + filler, 1, boxes, vw, vh).associateBy { it.id }
        assertTrue("surrounding words shrink when full", full.getValue(2).fontSize < sparse.getValue(2).fontSize)
        val ratioSparse = sparse.getValue(2).fontSize / sparse.getValue(3).fontSize
        val ratioFull = full.getValue(2).fontSize / full.getValue(3).fontSize
        assertEquals("proportions kept", ratioSparse, ratioFull, 0.001f)
    }

    @Test
    fun `relative size grows with count`() {
        assertEquals(CloudLayout.MIN_RELATIVE, CloudLayout.relativeSize(0, 10), 0.0001f)
        assertEquals(CloudLayout.MAX_RELATIVE, CloudLayout.relativeSize(10, 10), 0.0001f)
        assertTrue(CloudLayout.relativeSize(3, 10) < CloudLayout.relativeSize(6, 10))
    }

    @Test
    fun `visible words respect the maximum, skip hidden and always include the centre`() {
        val ws = words(40).map { if (it.id == 5L) it.copy(hidden = true) else it }
        val state = CloudState(words = ws + Word(99, "quiet", count = 0, lastActivityAt = 0), currentId = 99)
        val vis = CloudLayout.visibleWords(state, 20)
        assertEquals(20, vis.size)
        assertEquals(99L, vis.first().id)
        assertFalse(vis.any { it.hidden })
    }

    @Test
    fun `layout is deterministic`() {
        val ws = words(20)
        assertEquals(CloudLayout.layout(ws, 3, boxes, vw, vh), CloudLayout.layout(ws, 3, boxes, vw, vh))
    }

    @Test
    fun `long centre word is shrunk to fit the width`() {
        val ws = listOf(Word(1, "a".repeat(60)))
        val p = CloudLayout.layout(ws, 1, boxes, vw, vh).single()
        assertTrue(p.width <= vw * CloudLayout.CENTRE_MAX_WIDTH_FRACTION + 0.5f)
    }

    @Test
    fun `a dragged word sits where it was dropped and pushes the others out of its way`() {
        val ws = words(20)
        val spot = 300f to -150f
        val before = CloudLayout.layout(ws, 1, boxes, vw, vh).associateBy { it.id }
        val after = CloudLayout.layout(ws, 1, boxes, vw, vh, fixed = mapOf(5L to spot)).associateBy { it.id }
        val dragged = after.getValue(5)
        assertEquals(spot.first, dragged.cx, 0.01f)
        assertEquals(spot.second, dragged.cy, 0.01f)
        val list = after.values.toList()
        for (i in list.indices) for (j in i + 1 until list.size) {
            assertFalse("overlap ${list[i]} ${list[j]}", overlap(list[i], list[j]))
        }
        // Something had to move to make room.
        assertTrue(after.values.any { it.id != 5L && (it.cx != before.getValue(it.id).cx || it.cy != before.getValue(it.id).cy) })
    }

    @Test
    fun `the centre word can be dragged too`() {
        val ws = words(10)
        val p = CloudLayout.layout(ws, 1, boxes, vw, vh, fixed = mapOf(1L to (-400f to 200f))).single { it.isCentre }
        assertEquals(-400f, p.cx, 0.01f)
        assertEquals(200f, p.cy, 0.01f)
        assertEquals(vh * CloudLayout.CENTRE_HEIGHT_FRACTION, p.fontSize, 0.01f)
    }

    @Test
    fun `only the words in the way move`() {
        val ws = words(20)
        val before = CloudLayout.layout(ws, 1, boxes, vw, vh).associateBy { it.id }
        val target = before.getValue(7)
        // Drop word 5 right on top of word 7.
        val after = CloudLayout.layout(ws, 1, boxes, vw, vh, fixed = mapOf(5L to (target.cx to target.cy))).associateBy { it.id }
        assertTrue("the word underneath moved", after.getValue(7).cx != target.cx || after.getValue(7).cy != target.cy)
        val unmoved = after.values.count { it.id != 5L && it.cx == before.getValue(it.id).cx && it.cy == before.getValue(it.id).cy }
        assertTrue("most words stay put ($unmoved of 18)", unmoved >= 12)
    }
}

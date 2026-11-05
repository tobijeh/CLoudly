package io.github.tobijeh.cloudly.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GesturesAndBackfillTest {
    private var now = 1_000_000L
    private lateinit var c: CloudController
    private val s get() = c.state.value

    @Before
    fun setUp() {
        c = CloudController(clock = { now })
    }

    @Test
    fun `adding a word mid-session backfills its count from the transcript`() {
        c.ingest("We talked about grace. Grace again, and then hope.")
        val id = c.addWord("grace")!!
        assertEquals(2, s.word(id)!!.count)
    }

    @Test
    fun `adding a variant mid-session backfills`() {
        val id = c.addWord("שָׁלוֹם")!!
        c.ingest("shalom shalom, sholom")
        assertEquals(0, s.word(id)!!.count)
        c.setVariants(id, listOf("shalom", "sholom"))
        assertEquals(3, s.word(id)!!.count)
    }

    @Test
    fun `rename keeps count and position, keeps old name as a variant, and backfills`() {
        val id = c.addWord("felines")!!
        c.mention(id, 1)
        c.drag(id, 0.2f, 0.1f)
        c.ingest("cats are great, cats everywhere, my cat")
        assertTrue(c.rename(id, "cats"))
        val w = s.word(id)!!
        assertEquals("cats", w.display)
        assertEquals(listOf("felines"), w.variants)
        assertEquals("backfilled: 2 earlier mentions of 'cats'", 2, w.count)
        assertNotNull(s.pins[id])
    }

    @Test
    fun `rename never lowers a count and rejects duplicates`() {
        val a = c.addWord("alpha")!!
        c.addWord("beta")
        c.mention(a, 5)
        c.ingest("gamma")
        assertTrue(c.rename(a, "gamma"))
        assertEquals(5, s.word(a)!!.count)
        assertFalse(c.rename(a, "BETA"))
        assertEquals("gamma", s.word(a)!!.display)
    }

    @Test
    fun `ingest counts mentions and moves the word to the centre unless locked`() {
        val a = c.addWord("alpha")!!
        val b = c.addWord("beta")!!
        c.ingest("alpha alpha")
        assertEquals(2, s.word(a)!!.count)
        assertEquals(a, s.centreId)
        c.tap(a)
        c.ingest("beta")
        assertEquals(1, s.word(b)!!.count)
        assertEquals(a, s.centreId)
    }

    @Test
    fun `clear all and end session clear the transcript`() {
        c.ingest("alpha")
        c.clearAll()
        assertTrue(c.transcript.isEmpty())
        c.ingest("beta")
        c.endSession()
        assertTrue(c.transcript.isEmpty())
    }

    @Test
    fun `dragged word drifts back after the delay once released`() {
        c.settings = CloudSettings(driftBackMs = 60_000)
        val a = c.addWord("alpha")!!
        c.drag(a, 0.3f, -0.2f)
        now += 120_000
        c.tick()
        assertNotNull("never drifts while the finger is down", s.pins[a])
        c.endDrag(a)
        now += 59_000
        c.tick()
        assertNotNull(s.pins[a])
        now += 1_000
        c.tick()
        assertNull(s.pins[a])
    }

    @Test
    fun `default drift-back is 5 seconds`() {
        val a = c.addWord("alpha")!!
        c.drag(a, 0.1f, 0.1f); c.endDrag(a)
        now += 4_900
        c.tick()
        assertNotNull(s.pins[a])
        now += 100
        c.tick()
        assertNull(s.pins[a])
    }

    @Test
    fun `dragging again restarts the drift-back timer`() {
        c.settings = CloudSettings(driftBackMs = 60_000)
        val a = c.addWord("alpha")!!
        c.drag(a, 0.1f, 0.1f); c.endDrag(a)
        now += 50_000
        c.drag(a, 0.2f, 0.1f); c.endDrag(a)
        now += 50_000
        c.tick()
        assertEquals(0.2f, s.pins[a]!!.nx)
    }

    @Test
    fun `drag positions are clamped to the screen`() {
        val a = c.addWord("alpha")!!
        c.drag(a, 3f, -3f)
        assertEquals(0.5f, s.pins[a]!!.nx)
        assertEquals(-0.5f, s.pins[a]!!.ny)
    }

    @Test
    fun `dragging never changes counts`() {
        val a = c.addWord("alpha")!!
        c.mention(a, 4)
        val before = s.words
        c.drag(a, 0.1f, 0.1f); c.endDrag(a)
        assertEquals(before, s.words)
    }

    @Test
    fun `bin delete and undo restore the word with its count`() {
        val a = c.addWord("alpha", listOf("alfa"))!!
        c.mention(a, 7)
        c.drag(a, 0.4f, 0.45f) // dragged onto the bin
        val original = s.word(a)
        val d = c.delete(a)!!
        assertNull(s.word(a))
        assertNull(s.pins[a])
        c.restore(d)
        assertEquals(original, s.word(a))
        assertEquals(7, s.word(a)!!.count)
    }

    @Test
    fun `hiding a word drops its pin`() {
        val a = c.addWord("alpha")!!
        c.drag(a, 0.1f, 0.1f)
        c.setHidden(a, true)
        assertNull(s.pins[a])
    }
}

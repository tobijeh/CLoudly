package io.github.tobijeh.cloudly.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CloudControllerTest {
    private var now = 1_000_000L
    private lateinit var c: CloudController
    private val s get() = c.state.value

    @Before
    fun setUp() {
        c = CloudController(clock = { now })
    }

    private fun count(id: Long) = s.word(id)!!.count

    @Test
    fun `add word trims, rejects blank and dedupes by display`() {
        val a = c.addWord("  Grace  ", listOf("grace", " graces ", ""))!!
        assertEquals("Grace", s.word(a)!!.display)
        assertEquals(listOf("graces"), s.word(a)!!.variants) // display duplicate and blanks dropped
        assertNull(c.addWord("   "))
        assertEquals(a, c.addWord("grace"))
        assertEquals(1, s.words.size)
    }

    @Test
    fun `mention grows the word and moves it to the centre when unlocked`() {
        val a = c.addWord("alpha")!!
        val b = c.addWord("beta")!!
        c.mention(a)
        assertEquals(a, s.centreId)
        c.mention(b); c.mention(b)
        assertEquals(2, count(b))
        assertEquals(b, s.centreId)
    }

    @Test
    fun `tap locks centre and counts one mention by default`() {
        val a = c.addWord("alpha")!!
        val b = c.addWord("beta")!!
        c.tap(a)
        assertEquals(a, s.lock?.wordId)
        assertEquals(1, count(a))
        // While locked, other words still grow but cannot take the centre.
        c.mention(b, 5)
        assertEquals(5, count(b))
        assertEquals(a, s.centreId)
    }

    @Test
    fun `tap does not count when setting is off`() {
        c.settings = CloudSettings(countTapsAsMentions = false)
        val a = c.addWord("alpha")!!
        c.tap(a)
        assertEquals(0, count(a))
        assertEquals(a, s.lock?.wordId)
    }

    @Test
    fun `tap again or Auto releases the lock`() {
        val a = c.addWord("alpha")!!
        val b = c.addWord("beta")!!
        c.tap(a)
        c.tap(a)
        assertNull(s.lock)
        assertEquals("release tap does not count", 1, count(a))
        c.tap(a)
        c.releaseLock()
        assertNull(s.lock)
        // After release, the next mention takes the centre again.
        c.mention(b)
        assertEquals(b, s.centreId)
    }

    @Test
    fun `tapping another word moves the lock`() {
        val a = c.addWord("alpha")!!
        val b = c.addWord("beta")!!
        c.tap(a)
        c.tap(b)
        assertEquals(b, s.lock?.wordId)
        assertEquals(b, s.centreId)
    }

    @Test
    fun `auto release after the configured time`() {
        c.settings = CloudSettings(autoReleaseMs = 2 * 60_000L)
        val a = c.addWord("alpha")!!
        c.tap(a)
        now += 119_000
        c.tick()
        assertNotNull(s.lock)
        now += 1_000
        c.tick()
        assertNull(s.lock)
    }

    @Test
    fun `no auto release when timer is off`() {
        c.settings = CloudSettings(autoReleaseMs = null)
        val a = c.addWord("alpha")!!
        c.tap(a)
        now += 60 * 60_000L
        c.tick()
        assertEquals(a, s.lock?.wordId)
    }

    @Test
    fun `relocking restarts the auto release timer`() {
        c.settings = CloudSettings(autoReleaseMs = 60_000L)
        val a = c.addWord("alpha")!!
        val b = c.addWord("beta")!!
        c.tap(a)
        now += 50_000
        c.tap(b)
        now += 50_000
        c.tick()
        assertEquals(b, s.lock?.wordId)
        now += 10_000
        c.tick()
        assertNull(s.lock)
    }

    @Test
    fun `pinch emphasis never changes counts`() {
        val a = c.addWord("alpha")!!
        val b = c.addWord("beta")!!
        c.mention(a, 3); c.mention(b, 7)
        val before = s.words
        listOf(1.2f, 1.8f, 2.5f, 9f, 0.5f).forEach { c.emphasize(a, it) }
        assertEquals(1f, s.emphasis!!.scale)
        assertTrue(s.emphasis!!.active)
        c.endEmphasis()
        assertEquals(false, s.emphasis!!.active)
        assertEquals(1f, s.emphasis!!.scale)
        assertEquals(before, s.words)
        assertEquals(b, s.centreId)
        assertNull(s.lock)
    }

    @Test
    fun `emphasis is clamped between 1x and max`() {
        val a = c.addWord("alpha")!!
        c.emphasize(a, 10f)
        assertEquals(CloudController.MAX_EMPHASIS, s.emphasis!!.scale)
        c.emphasize(a, 0.2f)
        assertEquals(1f, s.emphasis!!.scale)
    }

    @Test
    fun `delete and undo restore the word with its count`() {
        val a = c.addWord("alpha", listOf("alfa"))!!
        val b = c.addWord("beta")!!
        c.mention(a, 4)
        c.setHidden(b, false)
        val original = s.word(a)
        val d = c.delete(a)!!
        assertNull(s.word(a))
        c.restore(d)
        assertEquals(original, s.word(a))
        assertEquals(4, count(a))
        c.restore(d) // second undo is a no-op
        assertEquals(2, s.words.size)
    }

    @Test
    fun `undo of a locked word restores the lock`() {
        val a = c.addWord("alpha")!!
        c.tap(a)
        val d = c.delete(a)!!
        assertNull(s.lock)
        c.restore(d)
        assertEquals(a, s.lock?.wordId)
    }

    @Test
    fun `edit keeps count and hidden state`() {
        val a = c.addWord("alpha")!!
        c.mention(a, 6)
        c.setHidden(a, true)
        c.editWord(a, "Alpha!", listOf("alpha bet"))
        val w = s.word(a)!!
        assertEquals("Alpha!", w.display)
        assertEquals(listOf("alpha bet"), w.variants)
        assertEquals(6, w.count)
        assertTrue(w.hidden)
    }

    @Test
    fun `hide keeps count and releases lock on that word`() {
        val a = c.addWord("alpha")!!
        val b = c.addWord("beta")!!
        c.tap(a)
        c.setHidden(a, true)
        assertNull(s.lock)
        assertEquals(1, count(a))
        assertEquals(b, s.centreId)
    }

    @Test
    fun `reset count and clear all`() {
        val a = c.addWord("alpha")!!
        c.mention(a, 3)
        c.resetCount(a)
        assertEquals(0, count(a))
        c.tap(a)
        c.clearAll()
        assertTrue(s.words.isEmpty())
        assertNull(s.lock)
        assertNull(s.centreId)
    }

    @Test
    fun `load replaces words with or without counts`() {
        val saved = listOf(Word(1, "one", count = 5), Word(2, "two", listOf("2"), count = 2))
        c.addWord("old")
        c.replaceWords(saved, keepCounts = true)
        assertEquals(listOf("one", "two"), s.words.map { it.display })
        assertEquals(listOf(5, 2), s.words.map { it.count })
        c.replaceWords(saved, keepCounts = false)
        assertEquals(listOf(0, 0), s.words.map { it.count })
        assertEquals(s.words.size, s.words.map { it.id }.toSet().size)
        // Newly added words never collide with loaded ids.
        val n = c.addWord("three")!!
        assertEquals(1, s.words.count { it.id == n })
    }

    @Test
    fun `list is most recent first`() {
        val a = c.addWord("alpha")!!
        now += 1
        val b = c.addWord("beta")!!
        assertEquals(listOf(b, a), s.wordsByRecent.map { it.id })
        now += 1
        c.mention(a)
        assertEquals(listOf(a, b), s.wordsByRecent.map { it.id })
    }
}

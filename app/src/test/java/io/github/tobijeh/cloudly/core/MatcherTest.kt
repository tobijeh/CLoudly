package io.github.tobijeh.cloudly.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MatcherTest {
    // JVM tests have no android.icu, so stand in for "Hans-Hant" with a small character map.
    private val hansToHant = mapOf('恩' to '恩', '义' to '義', '圣' to '聖', '经' to '經', '稣' to '穌')
    private val m = Matcher { s -> s.map { hansToHant[it] ?: it }.joinToString("") }

    private fun w(display: String, vararg variants: String, id: Long = 1) = Word(id, display, variants.toList())

    @Test
    fun `matches display and variants, any case, whole words only`() {
        val grace = w("Grace", "graces")
        assertEquals(3, m.count("GRACE and grace, plus Graces. Disgrace? graceful.", grace))
    }

    @Test
    fun `hebrew transliterations as variants`() {
        val shalom = w("שָׁלוֹם", "shalom", "sholom")
        assertEquals(2, m.count("Shalom everyone, and sholom to you", shalom))
    }

    @Test
    fun `hebrew matches without vowel points and with attached prefixes`() {
        val shalom = w("שָׁלוֹם")
        assertEquals(3, m.count("שלום, והשלום ובשלום", shalom))
        assertEquals(0, m.count("שלומית", shalom)) // a different word
    }

    @Test
    fun `simplified chinese is converted to traditional before matching`() {
        val bible = w("聖經")
        assertEquals(2, m.count("圣经很好。我读聖經", bible))
    }

    @Test
    fun `chinese matches by substring because it has no spaces`() {
        val jesus = w("耶穌")
        assertEquals(2, m.count("我信耶稣，耶穌愛我", jesus))
    }

    @Test
    fun `overlapping words are counted once, longest term wins`() {
        val words = listOf(w("grace", id = 1), w("amazing grace", id = 2))
        val counts = m.countAll("amazing grace, how sweet. grace again", words)
        assertEquals(1, counts[1])
        assertEquals(1, counts[2])
    }
}

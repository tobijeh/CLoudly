package io.github.tobijeh.cloudly.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScriptsAndEpisodesTest {
    @Test
    fun `detects scripts for font choice`() {
        assertEquals(Script.HEBREW, Scripts.detect("שָׁלוֹם"))
        assertEquals(Script.HEBREW, Scripts.detect("בְּרֵאשִׁית"))
        assertEquals(Script.CHINESE, Scripts.detect("恩典"))
        assertEquals(Script.CHINESE, Scripts.detect("耶穌 Jesus"))
        assertEquals(Script.LATIN, Scripts.detect("Grace"))
        assertEquals(Script.LATIN, Scripts.detect("123"))
    }

    @Test
    fun `episode round trips with any script`() {
        val e = Episode(
            "Ep 12 · שָׁלוֹם",
            42L,
            listOf(Word(1, "שָׁלוֹם", listOf("shalom", "sholom"), 3), Word(2, "恩典", count = 1, hidden = true)),
        )
        assertEquals(e, EpisodeCodec.decode(EpisodeCodec.encode(e)))
    }

    @Test
    fun `episode file names are safe`() {
        val n = EpisodeCodec.fileNameFor("../a/b:c?")
        assertFalse(n.contains('/'))
        assertFalse(n.contains(':'))
        assertEquals("episode.json", EpisodeCodec.fileNameFor("   "))
        assertEquals("恩典.json", EpisodeCodec.fileNameFor("恩典"))
    }
}

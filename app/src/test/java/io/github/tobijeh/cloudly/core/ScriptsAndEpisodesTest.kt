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

class EpisodeMigrationTest {
    /** A word list saved by version 0.1 (Episode version 1). Must keep loading in every later version. */
    private val v1 = """
        {
            "name": "Ep 3",
            "savedAt": 1793700000000,
            "words": [
                { "id": 1, "display": "שָׁלוֹם", "variants": ["shalom"], "count": 4, "hidden": false, "lastActivityAt": 5 },
                { "id": 2, "display": "恩典", "count": 2, "hidden": true }
            ],
            "version": 1
        }
    """.trimIndent()

    @org.junit.Test
    fun `version 1 word lists still load with their counts`() {
        val e = EpisodeCodec.decode(v1)
        org.junit.Assert.assertEquals(listOf("שָׁלוֹם", "恩典"), e.words.map { it.display })
        org.junit.Assert.assertEquals(listOf(4, 2), e.words.map { it.count })
        org.junit.Assert.assertEquals(listOf("shalom"), e.words[0].variants)
        org.junit.Assert.assertEquals(true, e.words[1].hidden)
    }
}

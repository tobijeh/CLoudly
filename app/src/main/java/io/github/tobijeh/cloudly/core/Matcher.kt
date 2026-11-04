package io.github.tobijeh.cloudly.core

import java.text.Normalizer
import java.util.Locale

/**
 * Finds mentions of words in transcript text.
 *
 * Normalisation: Simplified → Traditional Chinese (via the injected [toTraditional]; on Android this is ICU
 * "Hans-Hant"), lowercase, Unicode NFC, and Hebrew vowel points / cantillation removed so "שָׁלוֹם" matches "שלום".
 *
 * Matching: Chinese terms match as substrings (no spaces in Chinese). Other terms match whole words only.
 * Hebrew terms also match with up to two attached prefix letters (ו ה ב כ ל מ ש: "and", "the", "in", ...).
 * Overlapping matches are counted once, preferring the longest term ("amazing grace" over "grace").
 */
class Matcher(private val toTraditional: (String) -> String = { it }) {

    fun normalize(text: String): String {
        val nfc = Normalizer.normalize(toTraditional(text), Normalizer.Form.NFC)
        val sb = StringBuilder(nfc.length)
        for (ch in nfc) {
            if (ch.code in 0x0591..0x05C7 && ch != '־' && ch != '׀' && ch != '׃' && ch != '׆') continue
            sb.append(ch)
        }
        return sb.toString().lowercase(Locale.ROOT)
    }

    /** All normalised terms that count for [word]: its display text plus its Listen-for variants. */
    fun terms(word: Word): List<String> =
        (listOf(word.display) + word.variants).map { normalize(it).trim() }.filter { it.isNotEmpty() }.distinct()

    /** Number of mentions of [word] in [text]. */
    fun count(text: String, word: Word): Int = countAll(text, listOf(word))[word.id] ?: 0

    /**
     * Mentions per word id in [text]. Each stretch of text counts for at most one word, the longest
     * matching term winning, so overlapping words or variants are not double counted.
     */
    fun countAll(text: String, words: List<Word>): Map<Long, Int> {
        if (text.isBlank() || words.isEmpty()) return emptyMap()
        val norm = normalize(text)
        data class Hit(val start: Int, val end: Int, val wordId: Long)
        val hits = ArrayList<Hit>()
        for (w in words) for (term in terms(w)) {
            regexFor(term).findAll(norm).forEach { m ->
                val g = m.groups[1]!!.range
                hits += Hit(g.first, g.last + 1, w.id)
            }
        }
        // Longest first, then leftmost; keep hits that don't overlap an accepted one.
        hits.sortWith(compareByDescending<Hit> { it.end - it.start }.thenBy { it.start })
        val taken = BooleanArray(norm.length)
        val out = HashMap<Long, Int>()
        for (h in hits) {
            if ((h.start until h.end).any { taken[it] }) continue
            for (i in h.start until h.end) taken[i] = true
            out[h.wordId] = (out[h.wordId] ?: 0) + 1
        }
        return out
    }

    private val regexCache = HashMap<String, Regex>()

    /** Group 1 of each regex is the term itself (without any Hebrew prefix letters). */
    private fun regexFor(term: String): Regex = synchronized(regexCache) {
        regexCache.getOrPut(term) {
            val q = Regex.escape(term)
            when {
                term.any { Scripts.isHan(it.code) } -> Regex("($q)")
                term.any { Scripts.isHebrew(it.code) } ->
                    Regex("(?<![\\p{L}\\p{N}])(?:[ובכלמשה]{0,2})($q)(?![\\p{L}\\p{N}])")
                else -> Regex("(?<![\\p{L}\\p{N}])($q)(?![\\p{L}\\p{N}])")
            }
        }
    }
}

/** The session transcript. Lives in memory only: never written to disk, logs or saved episodes. */
class SessionTranscript {
    private val chunks = ArrayList<String>()

    @Synchronized fun append(chunk: String) {
        if (chunk.isNotBlank()) chunks += chunk.trim()
    }

    @Synchronized fun text(): String = chunks.joinToString("\n")

    @Synchronized fun clear() = chunks.clear()

    @Synchronized fun isEmpty(): Boolean = chunks.isEmpty()
}

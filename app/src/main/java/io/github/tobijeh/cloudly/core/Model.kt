package io.github.tobijeh.cloudly.core

import kotlinx.serialization.Serializable

/** A word on the cloud. [display] is what the TV shows; [variants] are extra spellings to listen for. */
@Serializable
data class Word(
    val id: Long,
    val display: String,
    val variants: List<String> = emptyList(),
    val count: Int = 0,
    val hidden: Boolean = false,
    /** Time of the last add or mention; drives "most recent first" ordering and centre fallback. */
    val lastActivityAt: Long = 0L,
)

/** The centre word is locked by a host tap. While locked, other words still grow but cannot take the centre. */
data class CentreLock(val wordId: Long, val lockedAt: Long)

/**
 * Display-only pinch emphasis. [scale] multiplies the word's drawn size on the TV; it never touches counts.
 * [active] is true while fingers are down; when false the renderer springs the word back to 1x.
 */
data class Emphasis(val wordId: Long, val scale: Float, val active: Boolean)

/**
 * A word the host dragged to a spot of their choosing. [nx]/[ny] are relative to the viewport centre as a
 * fraction of its width/height (-0.5..0.5), so a spot on the phone preview maps to the same spot on the TV.
 * While [dragging] the word follows the finger; once released it stays put until the drift-back delay has
 * passed since [releasedAt], then it glides back to its natural position.
 */
data class Pin(val nx: Float, val ny: Float, val dragging: Boolean, val releasedAt: Long = 0L)

data class CloudState(
    val words: List<Word> = emptyList(),
    /** Most recently mentioned word (the "auto" centre). */
    val currentId: Long? = null,
    val lock: CentreLock? = null,
    val listening: Boolean = false,
    val emphasis: Emphasis? = null,
    val pins: Map<Long, Pin> = emptyMap(),
) {
    fun word(id: Long?): Word? = id?.let { wid -> words.firstOrNull { it.id == wid } }

    /** The word shown in the centre: the locked word, else the current word, else the most recently active visible word. */
    val centreId: Long?
        get() {
            lock?.let { l -> if (word(l.wordId)?.hidden == false) return l.wordId }
            word(currentId)?.let { if (!it.hidden) return it.id }
            return words.filter { !it.hidden }.maxWithOrNull(compareBy<Word> { it.lastActivityAt }.thenBy { it.id })?.id
        }

    /** Words for the phone list: most recent first. */
    val wordsByRecent: List<Word>
        get() = words.sortedWith(compareByDescending<Word> { it.lastActivityAt }.thenByDescending { it.id })

    val maxCount: Int get() = words.maxOfOrNull { it.count } ?: 0
}

data class CloudSettings(
    val maxVisible: Int = DEFAULT_MAX_VISIBLE,
    val countTapsAsMentions: Boolean = true,
    /** null = never auto-release. */
    val autoReleaseMs: Long? = null,
    /** How long a dragged word stays where it was dropped before drifting back. */
    val driftBackMs: Long = DEFAULT_DRIFT_BACK_MS,
) {
    companion object {
        const val DEFAULT_MAX_VISIBLE = 20
        const val DEFAULT_DRIFT_BACK_MS = 60_000L
        const val MIN_VISIBLE = 10
        const val MAX_VISIBLE = 30
        val AUTO_RELEASE_CHOICES_MIN = listOf<Int?>(null, 1, 2, 5)
    }
}

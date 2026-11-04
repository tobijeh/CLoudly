package io.github.tobijeh.cloudly.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Returned by [CloudController.delete] so the word can be restored (with its count) by Undo. */
data class DeletedWord(val word: Word, val wasLocked: Boolean, val wasCurrent: Boolean)

/**
 * All cloud state changes go through here. Pure Kotlin (no Android) so it is fully unit-tested.
 * Thread-safety: state updates are atomic via [MutableStateFlow.update]; call from any thread.
 */
class CloudController(
    private val clock: () -> Long = System::currentTimeMillis,
    initial: CloudState = CloudState(),
    val matcher: Matcher = Matcher(),
) {
    /** In-memory session transcript (never persisted). Fed by listening; used for backfill. */
    val transcript = SessionTranscript()

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<CloudState> = _state.asStateFlow()

    @Volatile
    var settings: CloudSettings = CloudSettings()

    private var nextId: Long = (initial.words.maxOfOrNull { it.id } ?: 0L) + 1

    private fun newId(): Long = synchronized(this) { nextId++ }

    /** Adds a word, or returns the existing id if a word with the same display text already exists. */
    fun addWord(display: String, variants: List<String> = emptyList()): Long? {
        val text = display.trim()
        if (text.isEmpty()) return null
        _state.value.words.firstOrNull { it.display.equals(text, ignoreCase = true) }?.let { return it.id }
        val id = newId()
        val word = Word(id = id, display = text, variants = cleanVariants(variants, text), lastActivityAt = clock())
        _state.update { it.copy(words = it.words + word) }
        backfill(id)
        return id
    }

    /** Edits display text and variants. Keeps count, hidden state and position in the cloud. */
    fun editWord(id: Long, display: String, variants: List<String>) {
        val text = display.trim()
        if (text.isEmpty()) return
        updateWord(id) { it.copy(display = text, variants = cleanVariants(variants, text)) }
        backfill(id)
    }

    /**
     * Double-tap rename. Keeps the word's id, size, count and position; the old name becomes a Listen-for
     * variant so it keeps counting. Then backfills from the session transcript. Returns false if the new
     * name is blank or already used by another word.
     */
    fun rename(id: Long, newDisplay: String): Boolean {
        val text = newDisplay.trim()
        val old = _state.value.word(id) ?: return false
        if (text.isEmpty()) return false
        if (text == old.display) return true
        if (_state.value.words.any { it.id != id && it.display.equals(text, ignoreCase = true) }) return false
        updateWord(id) { it.copy(display = text, variants = cleanVariants(it.variants + it.display, text)) }
        backfill(id)
        return true
    }

    /** Hold menu → Listen-for spellings. Rescans the session transcript for the new spellings. */
    fun setVariants(id: Long, variants: List<String>) {
        updateWord(id) { it.copy(variants = cleanVariants(variants, it.display)) }
        backfill(id)
    }

    /**
     * Sets the word's count from earlier mentions in the session transcript. Never lowers a count
     * (taps and earlier matches stay counted): count = max(current, mentions in transcript).
     */
    fun backfill(id: Long) {
        if (transcript.isEmpty()) return
        val w = _state.value.word(id) ?: return
        val found = matcher.count(transcript.text(), w)
        if (found > w.count) updateWord(id) { it.copy(count = maxOf(it.count, found)) }
    }

    /** A new transcript chunk from listening: store it (memory only) and count mentions in it. */
    fun ingest(chunk: String) {
        if (chunk.isBlank()) return
        transcript.append(chunk)
        val words = _state.value.words
        val hits = matcher.countAll(chunk, words)
        // Each matched word gets its mentions; with several matches the last one listed takes the centre.
        for (w in words) hits[w.id]?.let { mention(w.id, it) }
    }

    /** Drag on the preview: the word follows to (nx, ny), relative to the viewport centre. */
    fun drag(id: Long, nx: Float, ny: Float) {
        _state.update { s ->
            if (s.word(id) == null) s
            else s.copy(pins = s.pins + (id to Pin(nx.coerceIn(-0.5f, 0.5f), ny.coerceIn(-0.5f, 0.5f), dragging = true)))
        }
    }

    /** Finger lifted: the word stays where it was dropped until the drift-back delay passes. */
    fun endDrag(id: Long) {
        val now = clock()
        _state.update { s ->
            val p = s.pins[id] ?: return@update s
            s.copy(pins = s.pins + (id to p.copy(dragging = false, releasedAt = now)))
        }
    }

    /** One mention: the word grows and moves to the centre unless a word is locked. */
    fun mention(id: Long, times: Int = 1) {
        if (times <= 0) return
        val now = clock()
        _state.update { s ->
            val w = s.word(id) ?: return@update s
            s.copy(
                words = s.words.map { if (it.id == id) it.copy(count = w.count + times, lastActivityAt = now) else it },
                currentId = if (s.lock == null && !w.hidden) id else s.currentId,
            )
        }
    }

    /**
     * Host tap on a word: lock it in the centre, or release if it is already locked.
     * The locking tap counts as one mention when [CloudSettings.countTapsAsMentions] is on.
     */
    fun tap(id: Long) {
        val s = _state.value
        val w = s.word(id) ?: return
        if (s.lock?.wordId == id) {
            releaseLock()
            return
        }
        val now = clock()
        _state.update { st ->
            st.copy(
                lock = CentreLock(id, now),
                currentId = id,
                words = if (settings.countTapsAsMentions && !w.hidden) {
                    st.words.map { if (it.id == id) it.copy(count = it.count + 1, lastActivityAt = now) else it }
                } else st.words,
            )
        }
    }

    /** "Auto": release the centre lock. The released word stays in the centre until the next mention. */
    fun releaseLock() {
        _state.update { it.copy(lock = null) }
    }

    /** Call periodically; releases the lock once the auto-release timer has elapsed. */
    fun tick() {
        val now = clock()
        val limit = settings.autoReleaseMs
        val lock = _state.value.lock
        if (limit != null && lock != null && now - lock.lockedAt >= limit) {
            _state.update { if (it.lock == lock) it.copy(lock = null) else it }
        }
        // Drift-back: words dropped somewhere glide home once left alone long enough.
        val drift = settings.driftBackMs
        if (_state.value.pins.values.any { !it.dragging && now - it.releasedAt >= drift }) {
            _state.update { s -> s.copy(pins = s.pins.filterValues { it.dragging || now - it.releasedAt < drift }) }
        }
    }

    fun setHidden(id: Long, hidden: Boolean) {
        _state.update { s ->
            s.copy(
                words = s.words.map { if (it.id == id) it.copy(hidden = hidden) else it },
                lock = if (hidden && s.lock?.wordId == id) null else s.lock,
                currentId = if (hidden && s.currentId == id) null else s.currentId,
                emphasis = if (hidden && s.emphasis?.wordId == id) null else s.emphasis,
                pins = if (hidden) s.pins - id else s.pins,
            )
        }
    }

    fun resetCount(id: Long) = updateWord(id) { it.copy(count = 0) }

    fun delete(id: Long): DeletedWord? {
        var deleted: DeletedWord? = null
        _state.update { s ->
            val w = s.word(id) ?: return@update s
            deleted = DeletedWord(w, wasLocked = s.lock?.wordId == id, wasCurrent = s.currentId == id)
            s.copy(
                words = s.words.filterNot { it.id == id },
                lock = if (s.lock?.wordId == id) null else s.lock,
                currentId = if (s.currentId == id) null else s.currentId,
                emphasis = if (s.emphasis?.wordId == id) null else s.emphasis,
                pins = s.pins - id,
            )
        }
        return deleted
    }

    /** Undo for [delete]: restores the word with its id and count. */
    fun restore(deleted: DeletedWord) {
        _state.update { s ->
            if (s.word(deleted.word.id) != null) return@update s
            s.copy(
                words = s.words + deleted.word,
                currentId = if (deleted.wasCurrent && s.currentId == null) deleted.word.id else s.currentId,
                lock = if (deleted.wasLocked && s.lock == null) CentreLock(deleted.word.id, clock()) else s.lock,
            )
        }
    }

    /** Clear all: removes every word and clears the session transcript. */
    fun clearAll() {
        transcript.clear()
        _state.update { it.copy(words = emptyList(), currentId = null, lock = null, emphasis = null, pins = emptyMap()) }
    }

    /** Ending a session clears the in-memory transcript. */
    fun endSession() {
        transcript.clear()
        setListening(false)
    }

    /** Replaces the word list (loading an episode). When [keepCounts] is false all counts start at zero. */
    fun replaceWords(words: List<Word>, keepCounts: Boolean) {
        val now = clock()
        val fresh = words.mapIndexed { i, w ->
            // Re-id to keep ids unique; preserve list order as recency (first = most recent).
            w.copy(id = newId(), count = if (keepCounts) w.count else 0, lastActivityAt = now - i)
        }
        _state.update { it.copy(words = fresh, currentId = null, lock = null, emphasis = null, pins = emptyMap()) }
    }

    fun setListening(listening: Boolean) {
        _state.update { it.copy(listening = listening) }
    }

    /** Pinch emphasis: display only. Never changes counts. */
    fun emphasize(id: Long, scale: Float) {
        val clamped = scale.coerceIn(1f, MAX_EMPHASIS)
        _state.update { s -> if (s.word(id) == null) s else s.copy(emphasis = Emphasis(id, clamped, active = true)) }
    }

    /** Pinch released: the renderer springs the word back to its saved size. */
    fun endEmphasis() {
        _state.update { s -> s.emphasis?.let { s.copy(emphasis = it.copy(scale = 1f, active = false)) } ?: s }
    }

    private fun updateWord(id: Long, f: (Word) -> Word) {
        _state.update { s -> s.copy(words = s.words.map { if (it.id == id) f(it) else it }) }
    }

    companion object {
        const val MAX_EMPHASIS = 3f

        fun cleanVariants(variants: List<String>, display: String): List<String> =
            variants.map { it.trim() }
                .filter { it.isNotEmpty() && !it.equals(display, ignoreCase = true) }
                .distinctBy { it.lowercase() }
    }
}

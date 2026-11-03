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
) {
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
        return id
    }

    /** Edits display text and variants. Keeps count, hidden state and position in the cloud. */
    fun editWord(id: Long, display: String, variants: List<String>) {
        val text = display.trim()
        if (text.isEmpty()) return
        updateWord(id) { it.copy(display = text, variants = cleanVariants(variants, text)) }
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
        val limit = settings.autoReleaseMs ?: return
        val lock = _state.value.lock ?: return
        if (clock() - lock.lockedAt >= limit) {
            _state.update { if (it.lock == lock) it.copy(lock = null) else it }
        }
    }

    fun setHidden(id: Long, hidden: Boolean) {
        _state.update { s ->
            s.copy(
                words = s.words.map { if (it.id == id) it.copy(hidden = hidden) else it },
                lock = if (hidden && s.lock?.wordId == id) null else s.lock,
                currentId = if (hidden && s.currentId == id) null else s.currentId,
                emphasis = if (hidden && s.emphasis?.wordId == id) null else s.emphasis,
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

    fun clearAll() {
        _state.update { it.copy(words = emptyList(), currentId = null, lock = null, emphasis = null) }
    }

    /** Replaces the word list (loading an episode). When [keepCounts] is false all counts start at zero. */
    fun replaceWords(words: List<Word>, keepCounts: Boolean) {
        val now = clock()
        val fresh = words.mapIndexed { i, w ->
            // Re-id to keep ids unique; preserve list order as recency (first = most recent).
            w.copy(id = newId(), count = if (keepCounts) w.count else 0, lastActivityAt = now - i)
        }
        _state.update { it.copy(words = fresh, currentId = null, lock = null, emphasis = null) }
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

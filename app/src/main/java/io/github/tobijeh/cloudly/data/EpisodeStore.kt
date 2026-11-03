package io.github.tobijeh.cloudly.data

import io.github.tobijeh.cloudly.core.Episode
import io.github.tobijeh.cloudly.core.EpisodeCodec
import io.github.tobijeh.cloudly.core.Word
import java.io.File

/** Episode word lists stored as JSON in app-private storage. Transcripts are never written here. */
class EpisodeStore(filesDir: File) {
    private val dir = File(filesDir, "episodes").apply { mkdirs() }
    private val currentFile = File(filesDir, "current_words.json")

    fun list(): List<Episode> = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        .mapNotNull { runCatching { EpisodeCodec.decode(it.readText()) }.getOrNull() }
        .sortedByDescending { it.savedAt }

    fun save(name: String, words: List<Word>) {
        val e = Episode(name.trim().ifBlank { "Episode" }, System.currentTimeMillis(), words)
        writeAtomic(File(dir, EpisodeCodec.fileNameFor(e.name)), EpisodeCodec.encode(e))
    }

    fun delete(name: String) {
        File(dir, EpisodeCodec.fileNameFor(name)).delete()
    }

    /** Autosave of the working list so a restart mid-show keeps the cloud. */
    fun saveCurrent(words: List<Word>) {
        writeAtomic(currentFile, EpisodeCodec.encode(Episode("current", System.currentTimeMillis(), words)))
    }

    fun loadCurrent(): List<Word> =
        runCatching { EpisodeCodec.decode(currentFile.readText()).words }.getOrDefault(emptyList())

    private fun writeAtomic(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
    }
}

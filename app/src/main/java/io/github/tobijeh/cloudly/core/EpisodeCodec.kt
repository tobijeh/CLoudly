package io.github.tobijeh.cloudly.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A saved word list for one episode. Never contains transcript text. */
@Serializable
data class Episode(
    val name: String,
    val savedAt: Long,
    val words: List<Word>,
    val version: Int = 1,
)

object EpisodeCodec {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    fun encode(e: Episode): String = json.encodeToString(Episode.serializer(), e)
    fun decode(s: String): Episode = json.decode(s)
    private fun Json.decode(s: String) = decodeFromString(Episode.serializer(), s)

    /** Safe file name for an episode (keeps any script, drops path characters). */
    fun fileNameFor(name: String): String {
        val cleaned = name.trim().replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(80)
        return (cleaned.ifBlank { "episode" }) + ".json"
    }
}

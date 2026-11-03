package io.github.tobijeh.cloudly.data

import android.content.Context
import io.github.tobijeh.cloudly.core.CloudSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Settings persisted in SharedPreferences. Milestone 1 subset; Milestone 3 adds the rest. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<CloudSettings> = _settings.asStateFlow()

    private fun read(): CloudSettings {
        val autoMin = prefs.getInt(KEY_AUTO_RELEASE_MIN, 0)
        return CloudSettings(
            maxVisible = prefs.getInt(KEY_MAX_VISIBLE, CloudSettings.DEFAULT_MAX_VISIBLE)
                .coerceIn(CloudSettings.MIN_VISIBLE, CloudSettings.MAX_VISIBLE),
            countTapsAsMentions = prefs.getBoolean(KEY_COUNT_TAPS, true),
            autoReleaseMs = if (autoMin > 0) autoMin * 60_000L else null,
        )
    }

    fun update(f: (CloudSettings) -> CloudSettings) {
        val s = f(_settings.value)
        prefs.edit()
            .putInt(KEY_MAX_VISIBLE, s.maxVisible)
            .putBoolean(KEY_COUNT_TAPS, s.countTapsAsMentions)
            .putInt(KEY_AUTO_RELEASE_MIN, ((s.autoReleaseMs ?: 0L) / 60_000L).toInt())
            .apply()
        _settings.value = s
    }

    private companion object {
        const val KEY_MAX_VISIBLE = "max_visible"
        const val KEY_COUNT_TAPS = "count_taps"
        const val KEY_AUTO_RELEASE_MIN = "auto_release_min"
    }
}

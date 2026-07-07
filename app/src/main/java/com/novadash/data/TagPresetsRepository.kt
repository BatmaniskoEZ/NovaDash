package com.novadash.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User-defined quick tags offered when saving a moment (tapped on the Android Auto screen,
 * managed in app Settings). Stored locally as a JSON string list in SharedPreferences and
 * shared as a Hilt singleton with the car service. First run seeds [DEFAULTS]; once the user
 * edits the list (even to empty) their choice is persisted.
 */
@Singleton
class TagPresetsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("novadash_tags", Context.MODE_PRIVATE)
    private val _tags = MutableStateFlow(load())
    val tags: StateFlow<List<String>> = _tags

    /** Adds a trimmed tag if non-blank and not already present (case-insensitive). */
    fun add(tag: String) {
        val t = tag.trim()
        if (t.isNotEmpty() && _tags.value.none { it.equals(t, ignoreCase = true) }) {
            update(_tags.value + t)
        }
    }

    fun remove(tag: String) = update(_tags.value.filterNot { it == tag })

    fun resetToDefaults() = update(DEFAULTS)

    private fun update(list: List<String>) {
        _tags.value = list
        save(list)
    }

    private fun load(): List<String> {
        if (!prefs.contains(KEY)) return DEFAULTS
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return DEFAULTS
        return (0 until arr.length()).map { arr.getString(it) }
    }

    private fun save(list: List<String>) {
        val arr = JSONArray()
        list.forEach(arr::put)
        prefs.edit { putString(KEY, arr.toString()) }
    }

    companion object {
        val DEFAULTS = listOf("Accident", "Near-miss", "Police", "Retard", "Noway", "Okthen")
        private const val KEY = "tags"
    }
}

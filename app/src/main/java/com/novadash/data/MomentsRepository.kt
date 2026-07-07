package com.novadash.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores flagged moments (time + tag) locally as JSON in SharedPreferences. Shared as a Hilt
 * singleton by the phone UI and the Android Auto service, so a moment saved in the car shows
 * up on the phone. Mirrors [MarkersRepository].
 */
@Singleton
class MomentsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("novadash_moments", Context.MODE_PRIVATE)
    private val _moments = MutableStateFlow(load())
    /** Newest first. */
    val moments: StateFlow<List<SavedMoment>> = _moments

    fun add(epochMillis: Long, tag: String = "", source: String = ""): SavedMoment {
        val moment = SavedMoment(
            id = "$epochMillis-${(0..9999).random()}",
            epochMillis = epochMillis,
            tag = tag,
            source = source,
        )
        update(_moments.value + moment)
        return moment
    }

    fun updateTag(id: String, tag: String) {
        update(_moments.value.map { if (it.id == id) it.copy(tag = tag) else it })
    }

    fun delete(id: String) {
        update(_moments.value.filterNot { it.id == id })
    }

    private fun update(list: List<SavedMoment>) {
        val sorted = list.sortedByDescending { it.epochMillis }
        _moments.value = sorted
        save(sorted)
    }

    private fun load(): List<SavedMoment> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SavedMoment(
                o.getString("id"),
                o.getLong("epochMillis"),
                o.optString("tag", ""),
                o.optString("source", ""),
            )
        }.sortedByDescending { it.epochMillis }
    }

    private fun save(list: List<SavedMoment>) {
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(JSONObject().apply {
                put("id", m.id)
                put("epochMillis", m.epochMillis)
                put("tag", m.tag)
                put("source", m.source)
            })
        }
        prefs.edit { putString(KEY, arr.toString()) }
    }

    private companion object {
        const val KEY = "moments"
    }
}

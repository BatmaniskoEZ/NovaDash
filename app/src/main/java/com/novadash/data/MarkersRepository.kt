package com.novadash.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores per-clip cut segments (keyed by the clip's file name) and exports them as a
 * LosslessCut `.llc` project. Segments persist locally in SharedPreferences as JSON; the
 * exported `.llc` lands in the shared Download/NovaDash folder for easy transfer to a PC.
 */
@Singleton
class MarkersRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("novadash_markers", Context.MODE_PRIVATE)
    private val _markers = MutableStateFlow(load())
    val markers: StateFlow<Map<String, List<Segment>>> = _markers

    fun segmentsFor(clipName: String): List<Segment> = _markers.value[clipName].orEmpty()

    fun setSegments(clipName: String, segments: List<Segment>) {
        val next = _markers.value.toMutableMap()
        if (segments.isEmpty()) next.remove(clipName) else next[clipName] = segments
        _markers.value = next
        save(next)
    }

    /**
     * Write a LosslessCut project for [mediaFileName] using [segments]. Returns the output Uri
     * (Download/NovaDash/<name>.llc) or null if there were no usable (complete) segments.
     */
    fun exportLlc(mediaFileName: String, segments: List<Segment>): Uri? {
        val complete = segments.filter { it.isComplete }
        if (complete.isEmpty()) return null
        val json = JSONObject().apply {
            put("version", 1)
            put("mediaFileName", mediaFileName)
            put("cutSegments", JSONArray().apply {
                complete.forEach { seg ->
                    put(JSONObject().apply {
                        put("start", seg.start)
                        put("end", seg.end)
                        put("name", seg.name)
                    })
                }
            })
        }
        val bytes = json.toString(2).toByteArray()
        val fileName = mediaFileName.substringBeforeLast('.') + ".llc"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) writeToDownloads(fileName, bytes)
        else writeToLegacy(fileName, bytes)
    }

    private fun writeToDownloads(fileName: String, bytes: ByteArray): Uri? {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        // Replace any previous export for this clip.
        resolver.delete(
            collection,
            "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
            arrayOf(fileName, "%${Environment.DIRECTORY_DOWNLOADS}/NovaDash%"),
        )
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/NovaDash")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return null
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: run {
            resolver.delete(uri, null, null); return null
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        return uri
    }

    private fun writeToLegacy(fileName: String, bytes: ByteArray): Uri {
        val dir = File(context.getExternalFilesDir(null), "llc").apply { mkdirs() }
        val out = File(dir, fileName)
        out.writeBytes(bytes)
        return Uri.fromFile(out)
    }

    private fun load(): Map<String, List<Segment>> {
        val raw = prefs.getString(KEY, null) ?: return emptyMap()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val out = HashMap<String, List<Segment>>()
        for (key in root.keys()) {
            val arr = root.getJSONArray(key)
            val segs = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Segment(
                    start = o.getDouble("start"),
                    end = if (o.isNull("end")) null else o.getDouble("end"),
                    name = o.optString("name", ""),
                )
            }
            out[key] = segs
        }
        return out
    }

    private fun save(data: Map<String, List<Segment>>) {
        val root = JSONObject()
        data.forEach { (key, segs) ->
            root.put(key, JSONArray().apply {
                segs.forEach { s ->
                    put(JSONObject().apply {
                        put("start", s.start)
                        if (s.end != null) put("end", s.end) else put("end", JSONObject.NULL)
                        put("name", s.name)
                    })
                }
            })
        }
        prefs.edit { putString(KEY, root.toString()) }
    }

    private companion object {
        const val KEY = "segments"
    }
}

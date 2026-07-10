package com.novadash.data

import android.content.Context
import android.net.Uri
import com.novadash.gps.NmeaParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Derives clip start times from the GPS track embedded in the footage instead of trusting the
 * camera's clock. Filenames carry the camera's wall clock, which drifts and doesn't follow DST,
 * so a moment flagged with phone time can miss its clip by up to an hour. The embedded NMEA
 * `$GxRMC` sentences carry satellite UTC time and recur throughout the file, so the first one
 * sits within the first couple of MB — reading just the head of a clip (streamed from the
 * camera and closed early, or opened locally for downloaded clips) yields the true recording
 * time without a full download.
 *
 * Clips recorded in one go share the same clock error, so we don't probe every clip: the list
 * is split into recording sessions (gap > [SESSION_GAP_MS] between consecutive clips), one clip
 * per session near a moment is probed, and the measured offset is applied to the whole session.
 *
 * Only clips already downloaded to the phone are probed. Files are NEVER streamed from the
 * camera here: reading file data while the camera is recording can wedge this firmware and
 * corrupt its storage (an eMMC auto-formatted itself on boot after exactly that), and this
 * class runs from the Moments tab with no guarantee recording is paused. Clips still on the
 * camera keep their filename time and are handled by the ±1h offset fallback in the matcher.
 */
@Singleton
class GpsClockCorrector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Probe result per file name; empty Optional = probed, no usable GPS fix in the head. */
    private val probed = ConcurrentHashMap<String, Optional<Long>>()

    // One head-read at a time: the camera's HTTP server is single-threaded.
    private val readMutex = Mutex()

    /**
     * Returns [groups] with GPS-corrected start times applied to every video session that has
     * a moment within [SLACK_MS] of it. Groups the probe can't improve are returned unchanged.
     */
    suspend fun correct(groups: List<RecordingGroup>, momentEpochs: List<Long>): List<RecordingGroup> {
        if (momentEpochs.isEmpty()) return groups
        val videos = groups.filter { it.primary.isVideo && it.primary.startEpochMillis > 0 }
            .sortedBy { it.primary.startEpochMillis }
        if (videos.isEmpty()) return groups

        val offsets = HashMap<String, Long>() // group key -> camera clock error (ms)
        for (session in splitSessions(videos)) {
            val nearMoment = momentEpochs.any { m ->
                m in (session.first().primary.startEpochMillis - SLACK_MS)..
                    (session.last().primary.startEpochMillis + SLACK_MS)
            }
            if (!nearMoment) continue
            val offset = sessionOffset(session) ?: continue
            session.forEach { g -> offsets[g.key] = offset }
        }
        if (offsets.isEmpty()) return groups
        return groups.map { g ->
            offsets[g.key]?.let { g.copy(startOverrideMillis = g.primary.startEpochMillis - it) } ?: g
        }
    }

    /** Consecutive clips with less than [SESSION_GAP_MS] between starts form one session. */
    private fun splitSessions(sorted: List<RecordingGroup>): List<List<RecordingGroup>> {
        val sessions = mutableListOf<MutableList<RecordingGroup>>()
        for (g in sorted) {
            val last = sessions.lastOrNull()?.last()
            if (last == null || g.primary.startEpochMillis - last.primary.startEpochMillis > SESSION_GAP_MS) {
                sessions += mutableListOf(g)
            } else {
                sessions.last() += g
            }
        }
        return sessions
    }

    /**
     * Camera clock error for one session: filename time minus GPS time of a probed clip.
     * Probes the middle clip first (GPS may still be acquiring a fix at session start), then
     * falls back to the last and first clips.
     */
    private suspend fun sessionOffset(session: List<RecordingGroup>): Long? {
        val candidates = listOf(session[session.size / 2], session.last(), session.first())
            .distinctBy { it.key }
        for (group in candidates) {
            val file = group.front?.takeIf { it.isVideo }
                ?: group.rear?.takeIf { it.isVideo } ?: continue
            val gpsStart = trueStartMillis(file) ?: continue
            return file.startEpochMillis - gpsStart
        }
        return null
    }

    /** UTC instant the clip actually started recording, from the first dated GPS fix in its
     *  head; null if the head has no usable fix. Cached per file name. */
    suspend fun trueStartMillis(file: MediaFile): Long? {
        probed[file.name]?.let { return it.orElse(null) }
        val result = runCatching { readFirstFixEpoch(file) }.getOrNull()
        probed[file.name] = Optional.ofNullable(result)
        return result
    }

    private suspend fun readFirstFixEpoch(file: MediaFile): Long? = readMutex.withLock {
        val url = file.downloadUrl
        // Local files only — see the class doc; never stream from the camera.
        if (!url.startsWith("content://") && !url.startsWith("file://")) return null
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(Uri.parse(url))
                ?.use { scanForFixEpoch(it) }
        }
    }

    /** Chunked scan of the first [HEAD_BYTES] for a dated RMC sentence (same overlap trick as
     *  GpsExtractor, so sentences straddling a chunk boundary are still caught). */
    private fun scanForFixEpoch(input: InputStream): Long? {
        val overlap = 128
        var carry = ""
        var remaining = HEAD_BYTES
        val buffer = ByteArray(256 * 1024)
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (read == -1) break
            remaining -= read
            val text = carry + String(buffer, 0, read, Charsets.ISO_8859_1)
            NmeaParser.parse(text).firstOrNull { it.epochMillis != null }
                ?.let { return it.epochMillis }
            carry = text.takeLast(overlap)
        }
        return null
    }

    private companion object {
        const val SESSION_GAP_MS = 5 * 60_000L
        /** How far a moment may sit from a session and still trigger a probe — generous enough
         *  for a DST hour plus drift. */
        const val SLACK_MS = 2 * 3_600_000L
        const val HEAD_BYTES = 2 * 1024 * 1024
    }
}

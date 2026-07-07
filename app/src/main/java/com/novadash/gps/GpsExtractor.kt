package com.novadash.gps

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts an embedded GPS track from a downloaded Novatek clip. These cameras store raw
 * NMEA sentences as ASCII inside the MP4 payload, so we scan the file for `$G..RMC` text and
 * decode it with [NmeaParser]. Scanning is chunked with an overlap window so sentences that
 * straddle a chunk boundary are still caught.
 */
@Singleton
class GpsExtractor @Inject constructor() {

    suspend fun extract(stream: InputStream): List<TrackPoint> = withContext(Dispatchers.IO) {
        val points = mutableListOf<TrackPoint>()
        val overlap = 128 // max NMEA sentence length, kept across chunk boundaries
        var carry = ""
        stream.buffered().use { input ->
            val buffer = ByteArray(1 shl 20) // 1 MiB
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                // Latin-1 keeps every byte 1:1; non-text bytes become harmless chars.
                val text = carry + String(buffer, 0, read, Charsets.ISO_8859_1)
                points += NmeaParser.parse(text)
                carry = text.takeLast(overlap)
            }
        }
        // Adjacent chunks can re-emit a sentence in the overlap; drop exact consecutive dupes.
        points.dedupeConsecutive()
    }

    private fun List<TrackPoint>.dedupeConsecutive(): List<TrackPoint> {
        val out = ArrayList<TrackPoint>(size)
        for (p in this) if (out.lastOrNull() != p) out.add(p)
        return out
    }
}

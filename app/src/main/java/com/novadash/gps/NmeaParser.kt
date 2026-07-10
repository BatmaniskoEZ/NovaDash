package com.novadash.gps

/** One decoded GPS fix from the clip's embedded track. */
data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Double?,
    /** UTC instant of the fix (RMC time+date fields) — satellite time, immune to the camera's
     *  clock being wrong. Null when the sentence carries no date. */
    val epochMillis: Long? = null,
)

/**
 * Parses NMEA `$GxRMC` sentences into [TrackPoint]s. Novatek dashcams embed raw NMEA in the
 * MP4 data, so extraction is simply "find every RMC sentence and decode it". Handles the
 * common talker IDs (GP/GN/GL) and ignores invalid ('V' status) fixes.
 *
 * RMC layout: `$GPRMC,hhmmss,A,llll.ll,N,yyyyy.yy,E,speedKnots,course,ddmmyy,...*cs`
 */
object NmeaParser {

    private val RMC = Regex("\\\$G[PNL]RMC,[^*\\n\\r]*")

    fun parse(text: String): List<TrackPoint> =
        RMC.findAll(text).mapNotNull { decode(it.value) }.toList()

    private fun decode(sentence: String): TrackPoint? {
        val f = sentence.split(",")
        if (f.size < 8) return null
        if (f[2] != "A") return null // 'A' = valid, 'V' = void

        val lat = coordinate(f[3], f[4]) ?: return null
        val lon = coordinate(f[5], f[6]) ?: return null
        val speedKmh = f[7].toDoubleOrNull()?.let { it * 1.852 } // knots -> km/h
        return TrackPoint(lat, lon, speedKmh, epochMillis = fixEpoch(f))
    }

    /** UTC epoch from RMC time (field 1, `hhmmss[.sss]`) + date (field 9, `ddmmyy`). */
    private fun fixEpoch(f: List<String>): Long? {
        val time = f.getOrNull(1)?.substringBefore('.')
            ?.takeIf { it.length == 6 && it.all(Char::isDigit) } ?: return null
        val date = f.getOrNull(9)
            ?.takeIf { it.length == 6 && it.all(Char::isDigit) } ?: return null
        return runCatching {
            java.text.SimpleDateFormat("ddMMyyHHmmss", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                .parse(date + time)?.time
        }.getOrNull()
    }

    /** NMEA coordinates are ddmm.mmmm / dddmm.mmmm; convert to signed decimal degrees. */
    private fun coordinate(value: String, hemisphere: String): Double? {
        if (value.isBlank()) return null
        val dot = value.indexOf('.')
        if (dot < 3) return null
        val degDigits = dot - 2
        val degrees = value.substring(0, degDigits).toDoubleOrNull() ?: return null
        val minutes = value.substring(degDigits).toDoubleOrNull() ?: return null
        var decimal = degrees + minutes / 60.0
        if (hemisphere == "S" || hemisphere == "W") decimal = -decimal
        return decimal
    }
}

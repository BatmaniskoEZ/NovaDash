package com.novadash.gps

/** One decoded GPS fix from the clip's embedded track. */
data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Double?,
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
        return TrackPoint(lat, lon, speedKmh)
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

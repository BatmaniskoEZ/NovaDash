package com.novadash.gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NmeaParserTest {

    @Test
    fun decodesValidRmc() {
        // 4807.038,N / 01131.000,E => 48.1173, 11.5167
        val nmea = "\$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A"
        val points = NmeaParser.parse(nmea)
        assertEquals(1, points.size)
        assertEquals(48.1173, points[0].latitude, 1e-4)
        assertEquals(11.5167, points[0].longitude, 1e-4)
        assertEquals(22.4 * 1.852, points[0].speedKmh!!, 1e-3) // knots -> km/h
    }

    @Test
    fun skipsVoidFixesAndHandlesSouthWest() {
        val text = buildString {
            append("noise\$GPRMC,000000,V,0000.000,N,00000.000,E,,,010100,,*00\n")
            append("\$GNRMC,010101,A,3345.000,S,15112.000,E,010.0,,010100,,*00 trailing")
        }
        val points = NmeaParser.parse(text)
        assertEquals(1, points.size) // void 'V' fix skipped
        assertTrue(points[0].latitude < 0) // South
        assertEquals(-33.75, points[0].latitude, 1e-4)
        assertEquals(151.2, points[0].longitude, 1e-4)
    }
}

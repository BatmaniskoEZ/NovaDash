package com.novadash.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class MomentMatcherTest {

    private var lastSync = 0L
    private val matcher = MomentMatcher { lastSync }

    /** Same parse as MediaFile.startEpochMillis (device-local TZ). */
    private fun epoch(stamp: String): Long =
        SimpleDateFormat("yyyyMMddHHmmss", Locale.US).parse(stamp)!!.time

    private fun clip(stamp: String, seq: Int) = RecordingGroup(
        front = MediaFile(
            name = "${stamp}_%07dF.MP4".format(seq),
            cameraPath = "A:\\$stamp.MP4",
            size = 0,
            time = null,
            isVideo = true,
            isEvent = false,
            isRear = false,
            downloadUrl = "http://cam/$stamp",
            thumbnailUrl = "",
        ),
        rear = null,
    )

    private val hour = 3_600_000L

    @Test
    fun exactClockMatchBeatsShiftedCandidate() {
        lastSync = 0L // never synced: everything eligible for ±1h too
        val exact = clip("20260711100000", 20) // 30s before the moment
        val shifted = clip("20260711090010", 10) // ~1h before: the -1h decoy
        val moment = epoch("20260711100030")

        assertEquals(exact, matcher.matchingGroup(listOf(shifted, exact), moment))
    }

    @Test
    fun shiftedFallbackRescuesOnlyPreSyncClips() {
        val clip = clip("20260711090010", 10) // named 1h behind the real event
        val moment = epoch("20260711100030")

        lastSync = epoch("20260711120000") // clip named before the sync: era unknown
        assertEquals(clip, matcher.matchingGroup(listOf(clip), moment))

        lastSync = epoch("20260711080000") // clip named after the sync: provably correct
        assertNull(matcher.matchingGroup(listOf(clip), moment))
    }

    @Test
    fun containingClipBeatsOneStartingJustAfter() {
        // Real case: camera 1h behind, clips named 18:07 and 18:08, moment at 19:07:11.
        // At -1h the adjusted moment (18:07:11) sits 11s into the 18:07 clip; the 18:08 clip
        // starting 49s later must not win via the start-slop tolerance.
        lastSync = epoch("20260711200000") // both clips pre-sync
        val containing = clip("20260711180700", 30)
        val justAfter = clip("20260711180800", 32)
        val moment = epoch("20260711190711")

        assertEquals(containing, matcher.matchingGroup(listOf(containing, justAfter), moment))
    }

    @Test
    fun startSlopStillMatchesWhenNoContainingClip() {
        lastSync = 0L
        val justAfter = clip("20260711180800", 32) // starts 49s after the adjusted moment
        val moment = epoch("20260711190711")

        assertEquals(justAfter, matcher.matchingGroup(listOf(justAfter), moment))
    }

    @Test
    fun momentPositionsUseExactAndShiftedOffsets() {
        val clip = clip("20260711100000", 1)
        val duration = 60_000L
        val inClip = epoch("20260711100010") // 10s in
        val shiftedInClip = epoch("20260711110030") // 30s in via the -1h offset
        val outside = epoch("20260711103000") // 30 min later: in no variant

        lastSync = 0L
        assertArrayEquals(
            longArrayOf(10_000L, 30_000L),
            matcher.momentPositionsInClip(clip, listOf(inClip, shiftedInClip, outside), duration),
        )

        lastSync = epoch("20260711080000") // clip post-sync: shifted position must vanish
        assertArrayEquals(
            longArrayOf(10_000L),
            matcher.momentPositionsInClip(clip, listOf(inClip, shiftedInClip, outside), duration),
        )
    }

    @Test
    fun noPositionsWithoutDuration() {
        val clip = clip("20260711100000", 1)
        assertArrayEquals(
            LongArray(0),
            matcher.momentPositionsInClip(clip, listOf(epoch("20260711100010")), 0L),
        )
    }
}

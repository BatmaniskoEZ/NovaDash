package com.novadash.data

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Matches saved moments (phone wall-clock instants) to recordings (named with the camera's
 * clock). The exact clock is tried first and wins outright; the ±1h offsets only rescue clips
 * named before the last camera clock sync (see [ClockSyncStore]) — this camera has no GPS
 * module to correct times from (see GpsClockCorrector), and a whole-hour DST error is the
 * common clock failure. Clips named after the sync are provably correct and never match
 * shifted.
 */
@Singleton
class MomentMatcher internal constructor(
    private val lastSyncMillis: () -> Long,
) {
    @Inject
    constructor(clockSync: ClockSyncStore) : this({ clockSync.lastSyncMillis })

    /** Whether a clip may match at a shifted (±1h) offset: only when named before the last
     *  clock sync (era unknown). Before any sync (0) everything is eligible. */
    private fun RecordingGroup.eligibleShifted(): Boolean {
        val lastSync = lastSyncMillis()
        return lastSync == 0L || startEpochMillis < lastSync
    }

    /** The clip that was recording at the moment (latest group started at/before it), trying
     *  the offsets in priority order — the first offset with a candidate wins. */
    fun matchingGroup(groups: List<RecordingGroup>, momentEpochMillis: Long): RecordingGroup? =
        CLOCK_OFFSETS_MS.firstNotNullOfOrNull { off ->
            val m = momentEpochMillis + off
            val eligible = groups
                .filter { off == 0L || it.eligibleShifted() }
                .filter { it.startEpochMillis > 0 }
            // The clip that contains the moment: latest start at/before it. Only when none
            // exists, accept one that started just after (small camera-clock slop) — the slack
            // must never outrank a containing clip, or a moment 11s into clip A gets matched
            // to clip B starting 49s later.
            eligible
                .filter { it.startEpochMillis <= m }
                .maxByOrNull { it.startEpochMillis }
                ?.takeIf { m - it.startEpochMillis < MAX_MATCH_MS }
                ?: eligible
                    .filter { it.startEpochMillis in (m + 1)..(m + CLIP_TOLERANCE_MS) }
                    .minByOrNull { it.startEpochMillis }
        }

    /** Recordings whose start falls within [-backMin, +fwdMin] of the moment — at the exact
     *  clock, plus the ±1h offsets for pre-sync clips (so hour-misnamed ones still show up). */
    fun groupsInWindow(
        groups: List<RecordingGroup>,
        momentEpochMillis: Long,
        backMin: Int,
        fwdMin: Int,
    ): List<RecordingGroup> =
        CLOCK_OFFSETS_MS.flatMap { off ->
            val from = momentEpochMillis + off - backMin * 60_000L
            val to = momentEpochMillis + off + fwdMin * 60_000L
            groups
                .filter { off == 0L || it.eligibleShifted() }
                .filter { it.startEpochMillis in from..to }
        }.distinctBy { it.key }.sortedBy { it.startEpochMillis }

    /**
     * Positions (ms into the clip) of the given moments inside [group], for timeline markers.
     * Each moment contributes at most one position — the first eligible offset whose delta
     * lands within the clip's duration. Sorted and de-duplicated.
     */
    fun momentPositionsInClip(
        group: RecordingGroup,
        momentEpochs: List<Long>,
        durationMs: Long,
    ): LongArray {
        val start = group.startEpochMillis
        if (durationMs <= 0 || start <= 0) return LongArray(0)
        return momentEpochs.mapNotNull { epoch ->
            CLOCK_OFFSETS_MS.firstNotNullOfOrNull { off ->
                if (off != 0L && !group.eligibleShifted()) null
                else (epoch + off - start).takeIf { it in 0..durationMs }
            }
        }.distinct().sorted().toLongArray()
    }

    private companion object {
        const val CLIP_TOLERANCE_MS = 70_000L // clips are ~60s; small slack past the start
        const val MAX_MATCH_MS = 10 * 60_000L // don't "match" a moment to a clip >10 min older
        /** Camera-clock errors to try, in priority order: exact first, then a DST hour either
         *  way. The first offset that produces a match wins. */
        val CLOCK_OFFSETS_MS = listOf(0L, -3_600_000L, 3_600_000L)
    }
}

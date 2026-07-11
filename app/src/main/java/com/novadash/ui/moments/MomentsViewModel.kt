package com.novadash.ui.moments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novadash.data.AppMode
import com.novadash.data.CameraChoice
import com.novadash.data.ClockSyncStore
import com.novadash.data.FileRepository
import com.novadash.data.GpsClockCorrector
import com.novadash.data.MomentsRepository
import com.novadash.data.RecordingGroup
import com.novadash.data.SavedMoment
import com.novadash.data.SessionState
import com.novadash.net.NovaResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MomentsViewModel @Inject constructor(
    private val momentsRepo: MomentsRepository,
    private val files: FileRepository,
    private val clockCorrector: GpsClockCorrector,
    private val clockSync: ClockSyncStore,
    session: SessionState,
) : ViewModel() {

    val offline: Boolean = session.mode.value == AppMode.OFFLINE
    val moments: StateFlow<List<SavedMoment>> = momentsRepo.moments

    // Clip groups (camera SD online, downloaded offline) used to match moments to footage.
    private val _groups = MutableStateFlow<List<RecordingGroup>>(emptyList())
    val groups: StateFlow<List<RecordingGroup>> = _groups.asStateFlow()

    init {
        loadGroups()
    }

    fun loadGroups() {
        viewModelScope.launch {
            val loaded = if (offline) files.downloadedGroups()
            else when (val r = files.list()) {
                is NovaResult.Ok -> RecordingGroup.pair(r.value)
                is NovaResult.Err -> emptyList()
            }
            _groups.value = loaded
            // Replace filename times with GPS-derived ones where the camera clock was wrong
            // (probes read only clip heads; groups re-emit once corrected).
            _groups.value = clockCorrector.correct(loaded, moments.value.map { it.epochMillis })
        }
    }

    fun saveNow() {
        momentsRepo.add(System.currentTimeMillis(), source = SavedMoment.SOURCE_PHONE)
    }

    fun updateTag(id: String, tag: String) = momentsRepo.updateTag(id, tag)
    fun delete(id: String) = momentsRepo.delete(id)

    /**
     * Whether a clip may match at a shifted (±1h) offset: only if it was named before the
     * camera clock was last synced to phone time — anything named after the sync is provably
     * correctly named and must only match exactly. Before any sync (0) everything is eligible.
     */
    private fun RecordingGroup.eligibleShifted(): Boolean {
        val lastSync = clockSync.lastSyncMillis
        return lastSync == 0L || startEpochMillis < lastSync
    }

    /**
     * The clip that was recording at the moment (latest group started at/before it). The exact
     * clock is tried first and wins outright; the ±1h offsets in [CLOCK_OFFSETS_MS] only rescue
     * clips recorded before the last clock sync, whose names can be a DST hour off.
     */
    fun matchingGroup(moment: SavedMoment): RecordingGroup? =
        CLOCK_OFFSETS_MS.firstNotNullOfOrNull { off ->
            val m = moment.epochMillis + off
            _groups.value
                .filter { off == 0L || it.eligibleShifted() }
                .filter { it.startEpochMillis in 1 until m + CLIP_TOLERANCE_MS }
                .maxByOrNull { it.startEpochMillis }
                ?.takeIf { m - it.startEpochMillis < MAX_MATCH_MS }
        }

    /** Recordings whose start falls within [-backMin, +fwdMin] of the moment — at the exact
     *  clock, plus the ±1h offsets for pre-sync clips (so hour-misnamed ones still show up). */
    fun groupsInWindow(moment: SavedMoment, backMin: Int, fwdMin: Int): List<RecordingGroup> =
        CLOCK_OFFSETS_MS.flatMap { off ->
            val from = moment.epochMillis + off - backMin * 60_000L
            val to = moment.epochMillis + off + fwdMin * 60_000L
            _groups.value
                .filter { off == 0L || it.eligibleShifted() }
                .filter { it.startEpochMillis in from..to }
        }.distinctBy { it.key }.sortedBy { it.startEpochMillis }

    fun download(groups: List<RecordingGroup>, choice: CameraChoice) {
        groups.forEach { g ->
            val targets = when (choice) {
                CameraChoice.FRONT -> listOfNotNull(g.front)
                CameraChoice.REAR -> listOfNotNull(g.rear)
                CameraChoice.BOTH -> listOfNotNull(g.front, g.rear)
            }
            targets.forEach { files.download(it) }
        }
    }

    private companion object {
        const val CLIP_TOLERANCE_MS = 70_000L // clips are ~60s; small slack past the start
        const val MAX_MATCH_MS = 10 * 60_000L // don't "match" a moment to a clip >10 min older
        /** Camera-clock errors to try, in priority order: exact first, then a DST hour either
         *  way (this camera has no GPS module, so misnamed clips can't be corrected from the
         *  footage — see GpsClockCorrector — and a whole-hour clock error is the common case).
         *  The first offset that produces a match wins. */
        val CLOCK_OFFSETS_MS = listOf(0L, -3_600_000L, 3_600_000L)
    }
}

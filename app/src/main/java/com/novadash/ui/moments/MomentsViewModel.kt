package com.novadash.ui.moments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novadash.data.AppMode
import com.novadash.data.CameraChoice
import com.novadash.data.FileRepository
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
            _groups.value = if (offline) files.downloadedGroups()
            else when (val r = files.list()) {
                is NovaResult.Ok -> RecordingGroup.pair(r.value)
                is NovaResult.Err -> emptyList()
            }
        }
    }

    fun saveNow() {
        momentsRepo.add(System.currentTimeMillis(), source = SavedMoment.SOURCE_PHONE)
    }

    fun updateTag(id: String, tag: String) = momentsRepo.updateTag(id, tag)
    fun delete(id: String) = momentsRepo.delete(id)

    /** The clip that was recording at the moment (latest group started at/before it). */
    fun matchingGroup(moment: SavedMoment): RecordingGroup? =
        _groups.value
            .filter { it.startEpochMillis in 1 until moment.epochMillis + CLIP_TOLERANCE_MS }
            .maxByOrNull { it.startEpochMillis }
            ?.takeIf { moment.epochMillis - it.startEpochMillis < MAX_MATCH_MS }

    /** Recordings whose start falls within [-backMin, +fwdMin] of the moment. */
    fun groupsInWindow(moment: SavedMoment, backMin: Int, fwdMin: Int): List<RecordingGroup> {
        val from = moment.epochMillis - backMin * 60_000L
        val to = moment.epochMillis + fwdMin * 60_000L
        return _groups.value.filter { it.startEpochMillis in from..to }.sortedBy { it.startEpochMillis }
    }

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
    }
}

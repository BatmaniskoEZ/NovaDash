package com.novadash.ui.moments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novadash.data.AppMode
import com.novadash.data.CameraChoice
import com.novadash.data.MomentMatcher
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
    private val matcher: MomentMatcher,
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

    /** The clip that was recording at the moment — see [MomentMatcher.matchingGroup]. */
    fun matchingGroup(moment: SavedMoment): RecordingGroup? =
        matcher.matchingGroup(_groups.value, moment.epochMillis)

    /** Recordings around the moment for the download picker — see [MomentMatcher.groupsInWindow]. */
    fun groupsInWindow(moment: SavedMoment, backMin: Int, fwdMin: Int): List<RecordingGroup> =
        matcher.groupsInWindow(_groups.value, moment.epochMillis, backMin, fwdMin)

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

}

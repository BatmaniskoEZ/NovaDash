package com.novadash.ui.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novadash.data.CameraChoice
import com.novadash.data.DownloadState
import com.novadash.data.FileRepository
import com.novadash.data.MediaFile
import com.novadash.data.RecordingGroup
import com.novadash.net.NovaResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Filter tabs over the SD-card listing. */
enum class FileFilter { ALL, VIDEO, PHOTO, EVENT, MARKED }

data class FilesUiState(
    val loading: Boolean = false,
    val groups: List<RecordingGroup> = emptyList(),
    val filter: FileFilter = FileFilter.ALL,
    val selected: Set<String> = emptySet(),
    /** Clip names that have saved cut markers, for the "Marked" filter. */
    val markedNames: Set<String> = emptySet(),
    val error: String? = null,
) {
    val selectionMode: Boolean get() = selected.isNotEmpty()

    val visibleGroups: List<RecordingGroup>
        get() = when (filter) {
            FileFilter.ALL -> groups
            FileFilter.VIDEO -> groups.filter { it.primary.isVideo }
            FileFilter.PHOTO -> groups.filter { !it.primary.isVideo }
            FileFilter.EVENT -> groups.filter { it.isEvent }
            FileFilter.MARKED -> groups.filter { it.primary.name in markedNames }
        }

    /** Visible groups bucketed by day, preserving order (newest first). */
    val sections: List<Pair<String, List<RecordingGroup>>>
        get() = visibleGroups.groupBy { it.day }.toList()
}

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val repository: FileRepository,
    markersRepo: com.novadash.data.MarkersRepository,
    session: com.novadash.data.SessionState,
) : ViewModel() {

    private val _state = MutableStateFlow(FilesUiState())
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    val downloads: StateFlow<Map<String, DownloadState>> = repository.downloads

    /** Offline = browse downloaded clips locally; online = the camera's SD card. */
    val offline: Boolean = session.mode.value == com.novadash.data.AppMode.OFFLINE

    init {
        refresh()
        // Keep the "Marked" filter in sync with saved cut markers.
        viewModelScope.launch {
            markersRepo.markers.collect { m ->
                _state.value = _state.value.copy(markedNames = m.keys.toSet())
            }
        }
        // Drop clips deleted elsewhere (e.g. from the playback screen) without a manual refresh.
        viewModelScope.launch {
            repository.deletedNames.collect { name ->
                _state.value = _state.value.copy(
                    groups = _state.value.groups.filterNot {
                        it.primary.name == name || it.front?.name == name || it.rear?.name == name
                    },
                )
            }
        }
    }

    fun refresh() {
        if (offline) {
            // Local library — no camera. Load already-downloaded clips.
            _state.value = _state.value.copy(loading = false, groups = repository.downloadedGroups())
            return
        }
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val r = repository.list()) {
                is NovaResult.Ok ->
                    _state.value = _state.value.copy(loading = false, groups = RecordingGroup.pair(r.value))
                is NovaResult.Err ->
                    _state.value = _state.value.copy(loading = false, error = r.message)
            }
        }
    }

    fun setFilter(filter: FileFilter) {
        _state.value = _state.value.copy(filter = filter)
    }

    // --- selection ---
    fun toggleSelected(group: RecordingGroup) {
        val cur = _state.value.selected
        _state.value = _state.value.copy(
            selected = if (group.key in cur) cur - group.key else cur + group.key,
        )
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selected = emptySet())
    }

    private fun selectedGroups(): List<RecordingGroup> =
        _state.value.groups.filter { it.key in _state.value.selected }

    fun downloadSelected(choice: CameraChoice) {
        selectedGroups().forEach { g ->
            val targets = when (choice) {
                CameraChoice.FRONT -> listOfNotNull(g.front)
                CameraChoice.REAR -> listOfNotNull(g.rear)
                CameraChoice.BOTH -> listOfNotNull(g.front, g.rear)
            }
            targets.forEach { repository.download(it) }
        }
        clearSelection()
    }

    fun cancelDownload(group: RecordingGroup) {
        group.front?.let { repository.cancel(it.cameraPath) }
        group.rear?.let { repository.cancel(it.cameraPath) }
    }

    fun cancelPath(path: String) = repository.cancel(path)

    fun deleteSelected() {
        viewModelScope.launch {
            selectedGroups().forEach { deleteGroupFiles(it) }
            clearSelection()
            refresh()
        }
    }

    fun download(file: MediaFile) = repository.download(file)

    fun delete(group: RecordingGroup) {
        viewModelScope.launch {
            val err = deleteGroupFiles(group)
            if (err != null) _state.value = _state.value.copy(error = err)
            else _state.value = _state.value.copy(groups = _state.value.groups - group)
        }
    }

    /** Delete both files of a group; returns the first error, or null on success. */
    private suspend fun deleteGroupFiles(group: RecordingGroup): String? {
        group.front?.let { repository.delete(it)?.let { e -> return e } }
        group.rear?.let { repository.delete(it)?.let { e -> return e } }
        return null
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}

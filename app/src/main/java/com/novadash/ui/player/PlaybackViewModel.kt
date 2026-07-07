package com.novadash.ui.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novadash.data.DownloadState
import com.novadash.data.FileRepository
import com.novadash.data.MarkersRepository
import com.novadash.data.MediaFile
import com.novadash.data.RecordingGroup
import com.novadash.data.Segment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the playback screen: shares download state, and stores per-clip cut markers. */
@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val files: FileRepository,
    private val markersRepo: MarkersRepository,
) : ViewModel() {

    val downloads: StateFlow<Map<String, DownloadState>> = files.downloads
    val markers: StateFlow<Map<String, List<Segment>>> = markersRepo.markers

    /** Enqueue one or more files (the repository downloads them one at a time). */
    fun download(vararg targets: MediaFile) = targets.forEach { files.download(it) }

    fun cancel(vararg targets: MediaFile) = targets.forEach { files.cancel(it.cameraPath) }

    /**
     * Delete the whole recording (front + rear, or a lone photo): from the camera SD if it's a
     * camera clip, from the phone if it's a local/downloaded copy. Reports an error or null.
     */
    fun deleteRecording(group: RecordingGroup, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            var error: String? = null
            val deleted = mutableListOf<String>()
            for (f in listOfNotNull(group.front, group.rear)) {
                val err = if (f.downloadUrl.startsWith("http", ignoreCase = true)) files.delete(f)
                else files.deleteLocal(f.downloadUrl)
                if (err != null) {
                    error = err
                    break
                }
                deleted += f.name
            }
            // Let any open Files listing drop these without a manual refresh.
            files.markDeleted(deleted)
            onResult(error)
        }
    }

    fun segmentsFor(clip: String): List<Segment> = markersRepo.segmentsFor(clip)

    /** Start a new segment at [posSec] (replaces a still-open pending segment). */
    fun markIn(clip: String, posSec: Double) {
        val segs = markersRepo.segmentsFor(clip).toMutableList()
        if (segs.lastOrNull()?.isComplete == false) segs[segs.lastIndex] = Segment(posSec)
        else segs.add(Segment(posSec))
        markersRepo.setSegments(clip, segs)
    }

    /** Close the open segment at [posSec]; ignored if there's no open in-point before it. */
    fun markOut(clip: String, posSec: Double) {
        val segs = markersRepo.segmentsFor(clip).toMutableList()
        val last = segs.lastOrNull() ?: return
        if (!last.isComplete && posSec > last.start) {
            segs[segs.lastIndex] = last.copy(end = posSec)
            markersRepo.setSegments(clip, segs)
        }
    }

    fun deleteSegment(clip: String, index: Int) {
        val segs = markersRepo.segmentsFor(clip).toMutableList()
        if (index in segs.indices) {
            segs.removeAt(index)
            markersRepo.setSegments(clip, segs)
        }
    }

    /** Export a .llc for [mediaFileName] using the recording's segments (stored under [clip]). */
    fun exportLlc(clip: String, mediaFileName: String): Uri? =
        markersRepo.exportLlc(mediaFileName, markersRepo.segmentsFor(clip))
}

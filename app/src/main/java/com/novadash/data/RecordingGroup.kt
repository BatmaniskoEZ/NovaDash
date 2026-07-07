package com.novadash.data

/**
 * One recording as the user thinks of it: a front clip and (optionally) its rear counterpart.
 * The camera stores them as two files with consecutive sequence numbers — front `..._NF.MP4`
 * and rear `..._(N+1)R.MP4` — so we pair a front with the rear at sequence+1.
 */
data class RecordingGroup(
    val front: MediaFile?,
    val rear: MediaFile?,
) {
    /** The file to show/play by default (front if present, otherwise the rear-only clip). */
    val primary: MediaFile get() = front ?: rear!!
    val hasRear: Boolean get() = rear != null
    val hasFront: Boolean get() = front != null
    val key: String get() = primary.cameraPath
    val day: String get() = primary.day
    val isEvent: Boolean get() = primary.isEvent
    val startEpochMillis: Long get() = primary.startEpochMillis

    companion object {
        /**
         * Collapse a flat file list into front/rear pairs. A rear file is matched to the front
         * whose sequence is exactly one less; unmatched rears become rear-only groups. Photos
         * (no rear concept) each become their own group. Result keeps the input ordering.
         */
        fun pair(files: List<MediaFile>): List<RecordingGroup> {
            val rearsBySeq = files.filter { it.isRear && it.sequence >= 0 }.associateBy { it.sequence }
            val consumedRears = HashSet<Int>()
            val groups = mutableListOf<RecordingGroup>()

            for (file in files) {
                if (file.isRear) continue // handled via its front (or as leftover below)
                val rear = rearsBySeq[file.sequence + 1]
                if (rear != null) consumedRears += rear.sequence
                groups += RecordingGroup(front = file, rear = rear)
            }
            // Rear files that never matched a front (e.g. front deleted) still show up.
            for (file in files) {
                if (file.isRear && file.sequence !in consumedRears) {
                    groups += RecordingGroup(front = null, rear = file)
                }
            }
            return groups
        }
    }
}

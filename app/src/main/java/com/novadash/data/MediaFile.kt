package com.novadash.data

import com.novadash.net.NovaClient
import com.novadash.net.NovaCommands
import com.novadash.net.model.NovaFileEntry

/** UI-facing model for one clip/photo on the camera SD card. */
data class MediaFile(
    val name: String,
    val cameraPath: String,
    val size: Long,
    val time: String?,
    val isVideo: Boolean,
    val isEvent: Boolean,
    val isRear: Boolean,
    val downloadUrl: String,
    val thumbnailUrl: String,
) {
    /** Sequence number embedded in the name, e.g. `..._0000003F.MP4` -> 3 (front/rear pair
     *  as consecutive numbers). -1 if it can't be parsed. */
    val sequence: Int
        get() = name.substringAfterLast('_', "").takeWhile { it.isDigit() }.toIntOrNull() ?: -1

    /** Day portion of the timestamp for section grouping, e.g. "2026/07/04". */
    val day: String
        get() = time?.substringBefore(' ')?.takeIf { it.isNotBlank() } ?: "Unknown date"

    /** Recording start time (device local TZ) parsed from the name's yyyyMMddHHmmss prefix,
     *  for matching against saved moments; 0 if unparseable. */
    val startEpochMillis: Long
        get() {
            val ts = name.substringBefore('_')
            if (ts.length < 14 || !ts.all { it.isDigit() }) return 0L
            return runCatching {
                java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US)
                    .parse(ts)?.time ?: 0L
            }.getOrDefault(0L)
        }

    companion object {
        /** Build a MediaFile for a clip already on the phone (offline library). The local Uri
         *  is used for both playback and (video-frame) thumbnail; time is parsed from the name. */
        fun fromLocal(name: String, uri: android.net.Uri): MediaFile {
            val ts = name.substringBefore('_')
            val time = if (ts.length >= 14) {
                "%s/%s/%s %s:%s:%s".format(
                    ts.substring(0, 4), ts.substring(4, 6), ts.substring(6, 8),
                    ts.substring(8, 10), ts.substring(10, 12), ts.substring(12, 14),
                )
            } else null
            val uriStr = uri.toString()
            return MediaFile(
                name = name,
                cameraPath = name, // no camera path offline; the name is the stable key
                size = 0,
                time = time,
                isVideo = name.endsWith(".MP4", ignoreCase = true),
                isEvent = false,
                isRear = name.substringBeforeLast('.').endsWith("R", ignoreCase = true),
                downloadUrl = uriStr,
                thumbnailUrl = uriStr,
            )
        }

        fun from(entry: NovaFileEntry, baseUrl: String = NovaClient.CAMERA_BASE_URL): MediaFile? {
            val name = entry.name ?: return null
            val path = entry.path ?: return null
            val download = entry.downloadUrl(baseUrl) ?: return null
            // Videos: thumbnail via the file URL + ?custom=1&cmd=4001 (camera returns a JPEG;
            // str form gives PAR_ERR). Photos are already images, so load the JPG directly.
            val thumb = if (name.endsWith(".JPG", ignoreCase = true)) download
            else "$download?custom=1&cmd=${NovaCommands.GET_THUMBNAIL}"
            return MediaFile(
                name = name,
                cameraPath = path,
                size = entry.size,
                time = entry.time,
                isVideo = entry.isVideo,
                isEvent = entry.isEvent,
                isRear = entry.isRear,
                downloadUrl = download,
                thumbnailUrl = thumb,
            )
        }
    }
}

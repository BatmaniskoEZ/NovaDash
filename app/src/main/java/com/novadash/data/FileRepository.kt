package com.novadash.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.novadash.net.NovaApi
import com.novadash.net.NovaClient
import com.novadash.net.NovaCommands
import com.novadash.net.NovaResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Download progress for a single file, keyed by camera path in [FileRepository.downloads]. */
sealed interface DownloadState {
    /** Waiting behind another download (camera serves one at a time). */
    data object Queued : DownloadState
    data class InProgress(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val bytesPerSec: Long,
    ) : DownloadState {
        val fraction: Float
            get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }
    /** [uri] is a playable location — a MediaStore content:// (Gallery) or file:// on old APIs. */
    data class Done(val uri: String) : DownloadState
    data class Failed(val message: String) : DownloadState
}

/** A clip already saved to the phone, for offline playback / GPS map. */
data class DownloadedClip(val name: String, val uri: Uri)

/**
 * Lists, downloads, and deletes SD-card media via the Novatek API. Downloads are saved into
 * the shared Movies/NovaDash folder via MediaStore so they show up in the phone's Gallery and
 * are easy to pull onto a PC (API 29+); older devices fall back to app-private storage.
 */
@Singleton
class FileRepository @Inject constructor(
    private val client: NovaClient,
    private val api: NovaApi,
    private val appScope: kotlinx.coroutines.CoroutineScope,
    @ApplicationContext private val context: Context,
) {
    private val _downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadState>> = _downloads.asStateFlow()

    /** Names of files just deleted (by any screen) so listings can drop them without a refresh. */
    private val _deletedNames = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val deletedNames: SharedFlow<String> = _deletedNames.asSharedFlow()

    /** Announce that files with these names were deleted (front/rear of a recording). */
    fun markDeleted(names: List<String>) = names.forEach { _deletedNames.tryEmit(it) }

    // Serial download queue — the camera's HTTP server handles one transfer at a time.
    private val queue = kotlinx.coroutines.channels.Channel<MediaFile>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    @Volatile private var activePath: String? = null
    @Volatile private var activeJob: kotlinx.coroutines.Job? = null
    private val byPath = java.util.concurrent.ConcurrentHashMap<String, MediaFile>()

    init {
        appScope.launch { worker() }
    }

    private suspend fun worker() {
        for (file in queue) {
            // Skip if it was cancelled while queued.
            if (_downloads.value[file.cameraPath] !is DownloadState.Queued) continue
            activePath = file.cameraPath
            val job = appScope.launch { runDownload(file) }
            activeJob = job
            job.join()
            activePath = null
            activeJob = null
        }
    }

    private val relativeDir = "${Environment.DIRECTORY_MOVIES}/NovaDash"

    /** App-private fallback dir for API < 29 (no MediaStore RELATIVE_PATH there). */
    private val legacyDir: File
        get() = File(context.getExternalFilesDir(null), "downloads").apply { mkdirs() }

    suspend fun list(): NovaResult<List<MediaFile>> =
        when (val r = client.fileList()) {
            is NovaResult.Ok -> {
                val files = r.value.files.mapNotNull { MediaFile.from(it, client.baseUrl) }
                    .sortedByDescending { it.name } // newest first (timestamp-prefixed names)
                markAlreadyDownloaded(files)
                NovaResult.Ok(files)
            }
            is NovaResult.Err -> r
        }

    /** Enqueue a download (no-op if it's already queued, running, or finished). */
    fun download(file: MediaFile) {
        val existing = _downloads.value[file.cameraPath]
        if (existing is DownloadState.Queued || existing is DownloadState.InProgress ||
            existing is DownloadState.Done
        ) return
        byPath[file.cameraPath] = file
        setState(file.cameraPath, DownloadState.Queued)
        queue.trySend(file)
    }

    /** Cancel a queued or in-flight download and clear its state. */
    fun cancel(path: String) {
        when (_downloads.value[path]) {
            is DownloadState.Queued -> clearState(path) // worker will skip it
            is DownloadState.InProgress -> if (activePath == path) activeJob?.cancel() else clearState(path)
            else -> Unit
        }
    }

    private suspend fun runDownload(file: MediaFile) {
        setState(file.cameraPath, DownloadState.InProgress(0, file.size, 0))
        val result = runCatching {
            withContext(Dispatchers.IO) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) downloadToMediaStore(file)
                else downloadToLegacy(file)
            }
        }
        result.fold(
            onSuccess = { setState(file.cameraPath, DownloadState.Done(it)) },
            onFailure = { e ->
                if (e is kotlinx.coroutines.CancellationException) clearState(file.cameraPath)
                else setState(file.cameraPath, DownloadState.Failed(e.message ?: "Download failed"))
            },
        )
    }

    private suspend fun downloadToMediaStore(file: MediaFile): String {
        val resolver = context.contentResolver
        // Photos go to Pictures/NovaDash (Images), videos to Movies/NovaDash (Video).
        val collection = if (file.isVideo)
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val dir = if (file.isVideo) relativeDir else "${Environment.DIRECTORY_PICTURES}/NovaDash"
        val mime = if (file.isVideo) "video/mp4" else "image/jpeg"
        val pending = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, dir)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, pending) ?: throw IOException("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { out -> streamTo(file, out) }
                ?: throw IOException("Cannot open output stream")
        } catch (e: Throwable) {
            resolver.delete(uri, null, null)
            throw e
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return uri.toString()
    }

    private suspend fun downloadToLegacy(file: MediaFile): String {
        val target = File(legacyDir, file.name)
        try {
            target.outputStream().use { out -> streamTo(file, out) }
        } catch (e: Throwable) {
            target.delete()
            throw e
        }
        return Uri.fromFile(target).toString()
    }

    private suspend fun streamTo(file: MediaFile, output: OutputStream) {
        val body = api.download(file.downloadUrl)
        val total = body.contentLength().takeIf { it > 0 } ?: file.size
        body.byteStream().use { input ->
            val buf = ByteArray(64 * 1024)
            var copied = 0L
            var windowStart = System.currentTimeMillis()
            var windowBytes = 0L
            var speed = 0L
            while (true) {
                currentCoroutineContext().ensureActive() // makes cancel responsive between reads
                val read = input.read(buf)
                if (read == -1) break
                output.write(buf, 0, read)
                copied += read
                windowBytes += read
                val now = System.currentTimeMillis()
                val elapsed = now - windowStart
                if (elapsed >= 500) { // recompute speed twice a second
                    speed = windowBytes * 1000 / elapsed
                    windowStart = now
                    windowBytes = 0
                    setState(file.cameraPath, DownloadState.InProgress(copied, total, speed))
                }
            }
        }
    }

    private fun clearState(path: String) {
        byPath.remove(path)
        _downloads.update { it - path }
    }

    /** Downloaded clips as front/rear-paired groups for the offline library, newest first. */
    fun downloadedGroups(): List<RecordingGroup> {
        val files = downloadedClips().map { MediaFile.fromLocal(it.name, it.uri) }
            .sortedByDescending { it.name }
        return RecordingGroup.pair(files)
    }

    /** Clips already saved to the phone (for the map/GPS tab), newest first. */
    fun downloadedClips(): List<DownloadedClip> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return legacyDir.listFiles { f -> f.extension.equals("mp4", true) }
                ?.sortedByDescending { it.name }
                ?.map { DownloadedClip(it.name, Uri.fromFile(it)) }
                ?: emptyList()
        }
        return queryDownloaded().values
            .sortedByDescending { it.name }
    }

    /** MediaStore lookup of our downloaded videos: display name -> clip. */
    private fun queryDownloaded(): Map<String, DownloadedClip> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyMap()
        val out = HashMap<String, DownloadedClip>()
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME)
        val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("%$relativeDir%")
        context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            while (c.moveToNext()) {
                val name = c.getString(nameCol)
                val uri = Uri.withAppendedPath(collection, c.getLong(idCol).toString())
                out[name] = DownloadedClip(name, uri)
            }
        }
        return out
    }

    /**
     * Reconcile the download map with what's actually on the phone: mark present files Done and
     * clear a stale Done for any file that was deleted externally (Bug: a locally-deleted clip
     * kept showing as "downloaded"). Active downloads (queued/in-progress) are left untouched.
     */
    private fun markAlreadyDownloaded(files: List<MediaFile>) {
        val byName = queryDownloaded()
        _downloads.update { current ->
            val next = current.toMutableMap()
            files.forEach { f ->
                val onDisk = byName[f.name]
                val existing = next[f.cameraPath]
                when {
                    existing is DownloadState.InProgress || existing is DownloadState.Queued -> Unit
                    onDisk != null -> next[f.cameraPath] = DownloadState.Done(onDisk.uri.toString())
                    existing is DownloadState.Done -> next.remove(f.cameraPath) // deleted externally
                }
            }
            next
        }
    }

    /** Delete one file on the camera (cmd 4003, str = camera path). */
    suspend fun delete(file: MediaFile): String? =
        when (val r = client.command(NovaCommands.DELETE_FILE, str = file.cameraPath)) {
            is NovaResult.Ok -> null
            is NovaResult.Err -> r.message
        }

    /** Delete a downloaded file from the phone by its MediaStore/content Uri (we own it, so no
     *  user consent prompt). Also clears any cached download state for it. Returns error or null. */
    fun deleteLocal(uriString: String): String? = try {
        val rows = context.contentResolver.delete(Uri.parse(uriString), null, null)
        _downloads.update { states -> states.filterValues { (it as? DownloadState.Done)?.uri != uriString } }
        if (rows > 0) null else "File not found on phone"
    } catch (e: Exception) {
        e.message ?: "Couldn't delete file"
    }

    private fun setState(path: String, state: DownloadState) {
        _downloads.update { it + (path to state) }
    }
}

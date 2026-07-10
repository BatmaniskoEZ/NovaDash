package com.novadash.data

import com.novadash.net.NotifyClient
import com.novadash.net.NovaClient
import com.novadash.net.NovaCommands
import com.novadash.net.NovaResult
import com.novadash.net.NovaStatus
import com.novadash.net.WifiGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** High-level connection state to the camera, surfaced to the UI. */
sealed interface CameraConnection {
    data object Disconnected : CameraConnection
    data object Connecting : CameraConnection
    data class Connected(val firmware: String?) : CameraConnection
    data class Error(val message: String) : CameraConnection
}

/** Which physical lens to stream. Front (av4) is confirmed; rear stream name is a guess. */
enum class CameraLens(val streamPath: String) {
    FRONT("av4"),
    REAR("av5"),
}

/**
 * Owns the camera session: binds to the AP network, verifies the camera responds, tracks
 * recording state (from command results and pushed :8192 events), and exposes live-view
 * URLs plus capture controls.
 */
@Singleton
class CameraRepository @Inject constructor(
    private val client: NovaClient,
    private val notify: NotifyClient,
    private val wifiGate: WifiGate,
    private val appScope: CoroutineScope,
) {
    private val _connection = MutableStateFlow<CameraConnection>(CameraConnection.Disconnected)
    val connection: StateFlow<CameraConnection> = _connection.asStateFlow()

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    init {
        // Reflect camera-initiated recording changes pushed over the :8192 socket.
        appScope.launch {
            notify.events.collect { event ->
                when (event.status ?: event.cmd) {
                    NovaStatus.RECORD_STARTED -> _recording.value = true
                    NovaStatus.RECORD_STOPPED -> _recording.value = false
                }
            }
        }
    }

    /** RTSP live-view URL for the given lens, e.g. rtsp://192.168.1.254/liveRTSP/av4. */
    fun liveUrl(lens: CameraLens = CameraLens.FRONT): String =
        "rtsp://${NovaClient.CAMERA_HOST}/liveRTSP/${lens.streamPath}"

    /** Bind to the camera Wi-Fi, ping it, read firmware, and start the notification socket. */
    suspend fun connect() {
        _connection.value = CameraConnection.Connecting
        wifiGate.bind()
        // requestNetwork binds asynchronously; wait for it before the first request, or it
        // leaves on the default (mobile) route and never reaches the camera's local IP.
        val bound = wifiGate.awaitBound()

        when (val r = client.command(NovaCommands.PING)) {
            is NovaResult.Ok -> Unit // reachable
            is NovaResult.Err -> {
                val hint = if (!bound) {
                    " — the phone isn't bound to the dashcam Wi-Fi. Make sure you're on the " +
                        "camera's AP (not mobile data)."
                } else ""
                _connection.value = CameraConnection.Error(
                    "Can't reach camera at ${NovaClient.CAMERA_HOST}: ${r.message}$hint",
                )
                return
            }
        }

        val firmware = when (val r = client.command(NovaCommands.GET_VERSION)) {
            is NovaResult.Ok -> r.value.payload
            is NovaResult.Err -> null
        }

        // Push the phone's clock to the camera (the stock app does the same on connect). The
        // camera's RTC drifts and doesn't follow DST, and clip filenames come from its clock,
        // so an unsynced camera breaks moment-to-clip matching. Best-effort; failures ignored.
        val now = java.util.Date()
        fun fmt(pattern: String) =
            java.text.SimpleDateFormat(pattern, java.util.Locale.US).format(now)
        client.command(NovaCommands.SET_DATE, str = fmt("yyyy-M-d"))
        client.command(NovaCommands.SET_TIME, str = fmt("HH:mm:ss"))

        _connection.value = CameraConnection.Connected(firmware)

        // Read the real recording state (cmd 3014 -> cmd 2001 value) so the UI matches the
        // camera on first connect; the :8192 events keep it in sync afterwards.
        when (val r = client.settingsDump()) {
            is NovaResult.Ok -> r.value[NovaCommands.MOVIE_RECORD]?.let { _recording.value = it == 1 }
            is NovaResult.Err -> Unit
        }

        notify.start(appScope)
    }

    fun disconnect() {
        notify.stop()
        wifiGate.unbind()
        _connection.value = CameraConnection.Disconnected
    }

    /** Toggle recording (cmd 2001). Returns an error message on failure, null on success. */
    suspend fun toggleRecording(): String? {
        val target = if (_recording.value) 0 else 1
        return when (val r = client.command(NovaCommands.MOVIE_RECORD, par = target)) {
            is NovaResult.Ok -> {
                _recording.value = target == 1
                null
            }
            is NovaResult.Err -> r.message
        }
    }

    /**
     * Take a still snapshot. This dashcam only captures while recording, via cmd 2017
     * (MOVIE_SNAPSHOT) — the stock app's `getIMAGE` path. Cmd 1001 (photo mode) is unsupported
     * here and wedges the HTTP server, so it's deliberately not used.
     */
    suspend fun takePhoto(): String? {
        if (!_recording.value) return "Start recording first — this camera only takes photos while recording."
        return when (val r = client.command(NovaCommands.MOVIE_SNAPSHOT)) {
            is NovaResult.Ok -> null
            is NovaResult.Err -> r.message
        }
    }

    private var pausedForBrowsing = false

    /**
     * Stop recording while the user browses Files or changes Settings. This camera (like the
     * stock app) needs recording stopped to reliably serve the album and apply setting
     * changes. Remembers whether it paused so [resumeFromBrowsing] can restore the prior state.
     *
     * Also stops the camera live-view stream (2015=0): this firmware wedges its streaming
     * pipeline (needs a power-cycle) if a file listing overlaps an open live-view session — the
     * same fragility as the photo path. The phone-side player is already gone (Live tab left).
     */
    suspend fun pauseForBrowsing() {
        client.command(NovaCommands.LIVEVIEW, par = 0)
        if (_recording.value) {
            client.command(NovaCommands.MOVIE_RECORD, par = 0)
            _recording.value = false
            pausedForBrowsing = true
        }
    }

    /** Resume recording (if we paused it) and re-enable the live-view stream for the Live tab. */
    suspend fun resumeFromBrowsing() {
        if (pausedForBrowsing) {
            client.command(NovaCommands.MOVIE_RECORD, par = 1)
            _recording.value = true
            pausedForBrowsing = false
        }
        client.command(NovaCommands.LIVEVIEW, par = 1)
    }
}

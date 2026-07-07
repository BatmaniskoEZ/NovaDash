package com.novadash.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One asynchronous notification pushed by the camera over its :8192 socket. The payload is
 * a small XML fragment carrying a status/notify code (see [NovaStatus]) — e.g. recording
 * started/stopped, SD inserted/removed. We extract the first `<Cmd>`/`<Status>` integers
 * and expose them; the raw text is kept for diagnostics.
 */
data class NovaEvent(val cmd: Int?, val status: Int?, val raw: String)

/**
 * Best-effort client for the camera's event socket (port 8192, the stock app's
 * NovaMessageService channel). Connects, streams pushed notifications as a [SharedFlow],
 * and silently reconnects. Live view and recording work without it — it just lets the UI
 * react to camera-initiated state changes (record auto-start, card removed, power off).
 */
@Singleton
class NotifyClient @Inject constructor() {

    private val _events = MutableSharedFlow<NovaEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<NovaEvent> = _events

    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) { loop() }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun loop() {
        while (currentCoroutineContext().isActive) {
            runCatching { connectAndRead() }
            delay(RECONNECT_DELAY_MS) // backoff before reconnecting
        }
    }

    private suspend fun connectAndRead() {
        Socket().use { socket ->
            socket.connect(
                InetSocketAddress(NovaClient.CAMERA_HOST, NovaClient.NOTIFY_PORT),
                CONNECT_TIMEOUT_MS,
            )
            val reader = socket.getInputStream().bufferedReader()
            val buffer = StringBuilder()
            while (currentCoroutineContext().isActive) {
                val line = reader.readLine() ?: break
                buffer.append(line)
                // Each push is one `<Function>...</Function>` (or `<LIST>`) block.
                if (line.contains("</Function>") || line.contains("</LIST>")) {
                    emit(buffer.toString())
                    buffer.setLength(0)
                }
            }
        }
    }

    private suspend fun emit(raw: String) {
        _events.emit(
            NovaEvent(
                cmd = firstInt(raw.substringAfter("<Cmd>", "")),
                status = firstInt(raw.substringAfter("<Status>", "")),
                raw = raw,
            )
        )
    }

    private fun firstInt(s: String): Int? = INT.find(s)?.value?.toIntOrNull()

    companion object {
        private const val CONNECT_TIMEOUT_MS = 4000
        private const val RECONNECT_DELAY_MS = 2000L
        private val INT = Regex("-?\\d+")
    }
}

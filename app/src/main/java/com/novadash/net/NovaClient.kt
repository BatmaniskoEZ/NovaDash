package com.novadash.net

import com.novadash.net.model.NovaFileList
import com.novadash.net.model.NovaFunction
import com.novadash.net.model.NovaVideoModeList
import com.novadash.net.model.NovaWifiInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Result of a control command: either a parsed response or a failure. */
sealed interface NovaResult<out T> {
    data class Ok<T>(val value: T) : NovaResult<T>
    data class Err(val status: Int, val message: String, val cause: Throwable? = null) :
        NovaResult<Nothing>
}

/**
 * Serializes access to the camera's single-threaded HTTP server and normalizes errors.
 *
 * The Novatek firmware drops overlapping requests, so every command goes through one
 * [Mutex]. Non-zero status codes are surfaced as [NovaResult.Err] with a friendly message
 * from [NovaStatus].
 */
@Singleton
class NovaClient @Inject constructor(
    private val api: NovaApi,
) {
    private val lock = Mutex()

    /** Base URL of the camera. Centralised so tests / alt firmware can override it. */
    val baseUrl: String = CAMERA_BASE_URL

    suspend fun command(cmd: Int, par: Int? = null, str: String? = null): NovaResult<NovaFunction> =
        lock.withLock {
            runCatching { api.command(cmd = cmd, par = par, str = str) }
                .fold(
                    onSuccess = { fn ->
                        if (NovaStatus.isSuccess(fn.status)) NovaResult.Ok(fn)
                        else NovaResult.Err(fn.status, NovaStatus.message(fn.status))
                    },
                    onFailure = { e -> NovaResult.Err(NovaStatus.FAIL, transportMessage(e), e) },
                )
        }

    suspend fun fileList(): NovaResult<NovaFileList> = lock.withLock {
        runCatching { api.fileList() }.fold(
            onSuccess = { NovaResult.Ok(it) },
            onFailure = { e -> NovaResult.Err(NovaStatus.FAIL, transportMessage(e), e) },
        )
    }

    suspend fun wifiInfo(): NovaResult<NovaWifiInfo> = lock.withLock {
        runCatching { api.wifiInfo() }.fold(
            onSuccess = { NovaResult.Ok(it) },
            onFailure = { e -> NovaResult.Err(NovaStatus.FAIL, transportMessage(e), e) },
        )
    }

    suspend fun videoModes(): NovaResult<NovaVideoModeList> = lock.withLock {
        runCatching { api.videoModes() }.fold(
            onSuccess = { NovaResult.Ok(it) },
            onFailure = { e -> NovaResult.Err(NovaStatus.FAIL, transportMessage(e), e) },
        )
    }

    /** cmd 3014 → map of setting-cmd id to its current value, parsed from the raw pairs. */
    suspend fun settingsDump(): NovaResult<Map<Int, Int>> = lock.withLock {
        runCatching { api.settingsDump().string() }.fold(
            onSuccess = { body ->
                val map = SETTING_PAIR.findAll(body).associate {
                    it.groupValues[1].toInt() to it.groupValues[2].toInt()
                }
                NovaResult.Ok(map)
            },
            onFailure = { e -> NovaResult.Err(NovaStatus.FAIL, transportMessage(e), e) },
        )
    }

    /** True if the camera answers the heartbeat command. */
    suspend fun ping(): Boolean =
        command(NovaCommands.PING) is NovaResult.Ok

    private fun transportMessage(e: Throwable): String =
        e.message?.takeIf { it.isNotBlank() } ?: "Cannot reach camera at $CAMERA_HOST"

    companion object {
        const val CAMERA_HOST = "192.168.1.254"
        const val CAMERA_BASE_URL = "http://$CAMERA_HOST/"
        const val NOTIFY_PORT = 8192

        // Matches each <Cmd>id</Cmd><Status>value</Status> pair in a cmd 3014 dump.
        private val SETTING_PAIR = Regex("<Cmd>(\\d+)</Cmd>\\s*<Status>(-?\\d+)</Status>")
    }
}

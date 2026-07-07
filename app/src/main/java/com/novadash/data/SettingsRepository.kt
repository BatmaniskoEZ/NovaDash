package com.novadash.data

import com.novadash.net.NovaClient
import com.novadash.net.NovaCommands
import com.novadash.net.NovaResult
import javax.inject.Inject
import javax.inject.Singleton

data class WifiCredentials(val ssid: String, val passphrase: String)

/** A selectable recording resolution (from cmd 3030); [index] is the value for cmd 2002. */
data class ResolutionOption(val index: Int, val label: String)

/** Current recording configuration read from the camera (cmd 3030 + cmd 3014). */
data class RecordingSettings(
    val resolutions: List<ResolutionOption>,
    val currentResolution: Int,
    val hdr: Boolean,
    val audio: Boolean,
    val gsensor: Int,
    val volume: Int,
    val loop: Int,
    val datetime: Boolean,
    val frequency: Int,
) {
    companion object {
        // Value->label maps confirmed against the stock app's traffic on this camera.
        val GSENSOR_LEVELS = listOf(0 to "Off", 1 to "High", 2 to "Middle", 3 to "Low")
        val VOLUME_LEVELS = listOf(0 to "Off", 1 to "Low", 2 to "Medium", 3 to "High")
        // cmd 2003 is an INDEX into [1,3,5] min (not literal minutes): 0=1min, 1=3min, 2=5min.
        // Matches the stock app's `settings_loop_recording_text` array and the camera's dump.
        val LOOP_INTERVALS = listOf(0 to "1 min", 1 to "3 min", 2 to "5 min")
        val FREQUENCIES = listOf(0 to "50 Hz", 1 to "60 Hz")
    }
}

/** Outcome of an experimental command probe, shown in the advanced panel. */
data class ProbeResult(val cmd: Int, val status: Int?, val message: String)

/**
 * Camera settings operations: Wi-Fi credentials, audio/voice controls, persistence, format,
 * and the experimental undocumented-command probes for silencing voice prompts.
 *
 * The voice/beep story (see memory/MEMORY.md): FL_BEEP isn't exposed by any single command,
 * so [muteVoice] turns off the reachable levers — clip audio (2007) and beep volume (2505) —
 * and [probe] lets the user try the undocumented dispatch-table commands one by one.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val client: NovaClient,
) {
    suspend fun wifi(): NovaResult<WifiCredentials> = when (val r = client.wifiInfo()) {
        is NovaResult.Ok -> NovaResult.Ok(
            WifiCredentials(r.value.ssid.orEmpty(), r.value.passphrase.orEmpty()),
        )
        is NovaResult.Err -> r
    }

    /** Load recording resolution options (3030) plus current values (3014). */
    suspend fun recording(): NovaResult<RecordingSettings> {
        val modes = when (val r = client.videoModes()) {
            is NovaResult.Ok -> r.value.items.map { ResolutionOption(it.index, it.name.orEmpty()) }
            is NovaResult.Err -> return r
        }
        val values = when (val r = client.settingsDump()) {
            is NovaResult.Ok -> r.value
            is NovaResult.Err -> return r
        }
        return NovaResult.Ok(
            RecordingSettings(
                resolutions = modes,
                currentResolution = values[NovaCommands.MOVIE_SIZE] ?: -1,
                hdr = values[NovaCommands.MOVIE_HDR] == 1,
                audio = values[NovaCommands.MOVIE_AUDIO] == 1,
                gsensor = values[NovaCommands.GSENSOR_SENS] ?: 0,
                volume = values[NovaCommands.VOLUME] ?: 0,
                loop = values[NovaCommands.MOVIE_LOOP] ?: 0,
                datetime = values[NovaCommands.DATETIME_STAMP] == 1,
                frequency = values[NovaCommands.FREQUENCY] ?: 0,
            ),
        )
    }

    /**
     * Resolution and WDR reinitialise the video pipeline, so — mirroring the stock app — we
     * stop the liveview stream (2015=0), apply the change, then restart it (2015=1). Caller
     * must have recording stopped first (the browsing auto-pause handles that).
     */
    suspend fun setResolution(index: Int): String? = wrappedInLiveview {
        errorOf(NovaCommands.MOVIE_SIZE, par = index)
    }

    suspend fun setHdr(on: Boolean): String? = wrappedInLiveview {
        errorOf(NovaCommands.MOVIE_HDR, par = if (on) 1 else 0)
    }

    suspend fun setGsensor(level: Int): String? = errorOf(NovaCommands.GSENSOR_SENS, par = level)
    suspend fun setVolume(level: Int): String? = errorOf(NovaCommands.VOLUME, par = level)
    /** [index] is the loop-interval index (0=1min, 1=3min, 2=5min), not the minute count. */
    suspend fun setLoop(index: Int): String? = errorOf(NovaCommands.MOVIE_LOOP, par = index)
    suspend fun setDatetime(on: Boolean): String? =
        errorOf(NovaCommands.DATETIME_STAMP, par = if (on) 1 else 0)
    suspend fun setFrequency(value: Int): String? = errorOf(NovaCommands.FREQUENCY, par = value)

    private suspend fun wrappedInLiveview(block: suspend () -> String?): String? {
        errorOf(NovaCommands.LIVEVIEW, par = 0)
        val result = block()
        errorOf(NovaCommands.LIVEVIEW, par = 1)
        return result
    }

    suspend fun setSsid(ssid: String): String? = errorOf(NovaCommands.SET_SSID, str = ssid)
    suspend fun setPassword(pw: String): String? = errorOf(NovaCommands.SET_PASSWORD, str = pw)

    /** Persist current menu settings to PStore so they survive a reboot (cmd 3021). */
    suspend fun save(): String? = errorOf(NovaCommands.SAVE_SETTINGS)

    suspend fun setRecordAudio(enabled: Boolean): String? =
        errorOf(NovaCommands.MOVIE_AUDIO, par = if (enabled) 1 else 0)

    /**
     * Mute the camera's prompts/beeps by dropping the volume (cmd 6807) to 0, then persist.
     * This is the real volume lever confirmed from the stock app (unlike the speculative
     * FL_BEEP path). Returns the first error encountered, or null on success.
     */
    suspend fun muteVoice(): String? =
        setVolume(0) ?: save()

    suspend fun formatSd(): String? = errorOf(NovaCommands.FORMAT_SD)

    /** Fire one arbitrary command (experimental probe or user-entered) and report the result. */
    suspend fun probe(cmd: Int, par: Int? = 0, str: String? = null): ProbeResult =
        when (val r = client.command(cmd, par = par, str = str)) {
            is NovaResult.Ok -> {
                val payload = r.value.payload?.let { " '$it'" } ?: ""
                ProbeResult(cmd, r.value.status, "OK (status ${r.value.status})$payload")
            }
            is NovaResult.Err -> ProbeResult(cmd, r.status, r.message)
        }

    private suspend fun errorOf(cmd: Int, par: Int? = null, str: String? = null): String? =
        when (val r = client.command(cmd, par = par, str = str)) {
            is NovaResult.Ok -> null
            is NovaResult.Err -> r.message
        }
}

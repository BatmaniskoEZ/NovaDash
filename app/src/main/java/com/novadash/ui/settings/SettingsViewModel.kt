package com.novadash.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novadash.data.ProbeResult
import com.novadash.data.RecordingSettings
import com.novadash.data.SettingsRepository
import com.novadash.data.WifiCredentials
import com.novadash.net.NovaCommands
import com.novadash.net.NovaResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val wifi: WifiCredentials? = null,
    val recording: RecordingSettings? = null,
    val probes: List<ProbeResult> = emptyList(),
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    val experimentalCommands: List<Int> = NovaCommands.EXPERIMENTAL

    init {
        loadWifi()
        loadRecording()
    }

    private fun loadWifi() {
        viewModelScope.launch {
            when (val r = repository.wifi()) {
                is NovaResult.Ok -> _state.value = _state.value.copy(wifi = r.value)
                is NovaResult.Err -> _state.value = _state.value.copy(message = r.message)
            }
        }
    }

    private fun loadRecording() {
        viewModelScope.launch {
            when (val r = repository.recording()) {
                is NovaResult.Ok -> _state.value = _state.value.copy(recording = r.value)
                is NovaResult.Err -> _state.value = _state.value.copy(message = r.message)
            }
        }
    }

    fun setResolution(index: Int) = act { repository.setResolution(index) }
    fun setHdr(on: Boolean) = act { repository.setHdr(on) }
    fun setLoop(index: Int) = act { repository.setLoop(index) }
    fun setRecordAudio(on: Boolean) = act { repository.setRecordAudio(on) }
    fun setGsensor(level: Int) = act { repository.setGsensor(level) }
    fun setVolume(level: Int) = act { repository.setVolume(level) }
    fun setDatetime(on: Boolean) = act { repository.setDatetime(on) }
    fun setFrequency(value: Int) = act { repository.setFrequency(value) }

    fun muteVoice() = act(successMessage = "Voice/beep muted") { repository.muteVoice() }

    fun saveSettings() = act(successMessage = "Settings saved to camera") { repository.save() }

    fun setWifi(ssid: String, password: String) = act(successMessage = "Wi-Fi updated — reconnect") {
        repository.setSsid(ssid) ?: repository.setPassword(password) ?: repository.save()
    }

    fun formatSd() = act(successMessage = "SD card formatted") { repository.formatSd() }

    fun probe(cmd: Int) {
        viewModelScope.launch {
            val result = repository.probe(cmd)
            _state.value = _state.value.copy(probes = _state.value.probes + result)
        }
    }

    /** Send a user-entered command. Blank par/str are omitted from the request. */
    fun sendCustom(cmd: Int, par: Int?, str: String?) {
        viewModelScope.launch {
            val result = repository.probe(cmd, par, str?.takeIf { it.isNotBlank() })
            _state.value = _state.value.copy(probes = _state.value.probes + result)
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    /**
     * Runs a setting command, surfaces any error, then re-reads the camera so the UI always
     * reflects the camera's real state (rejected changes snap back).
     */
    private fun act(successMessage: String? = null, block: suspend () -> String?) {
        viewModelScope.launch {
            val err = block()
            if (err != null) {
                _state.value = _state.value.copy(message = err)
            } else if (successMessage != null) {
                _state.value = _state.value.copy(message = successMessage)
            }
            // Refresh recording state from the camera after any change.
            when (val r = repository.recording()) {
                is NovaResult.Ok -> _state.value = _state.value.copy(recording = r.value)
                is NovaResult.Err -> Unit
            }
        }
    }
}

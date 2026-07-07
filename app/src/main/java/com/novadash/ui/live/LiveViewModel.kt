package com.novadash.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novadash.data.CameraLens
import com.novadash.data.CameraRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val repository: CameraRepository,
) : ViewModel() {

    val recording: StateFlow<Boolean> = repository.recording

    private val _lens = MutableStateFlow(CameraLens.FRONT)
    val lens: StateFlow<CameraLens> = _lens.asStateFlow()

    /** Transient one-shot message (errors / confirmations) for a snackbar. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val streamUrl: String get() = repository.liveUrl(_lens.value)

    fun switchLens() {
        _lens.value = if (_lens.value == CameraLens.FRONT) CameraLens.REAR else CameraLens.FRONT
    }

    fun toggleRecording() {
        viewModelScope.launch { repository.toggleRecording()?.let { _message.value = it } }
    }

    /** Suspends through the capture so the caller can stop/reload the RTSP player around it
     *  (this firmware crashes if a photo is taken while liveview is streaming). */
    suspend fun capturePhoto() {
        _message.value = repository.takePhoto() ?: "Photo captured"
    }

    fun consumeMessage() {
        _message.value = null
    }
}

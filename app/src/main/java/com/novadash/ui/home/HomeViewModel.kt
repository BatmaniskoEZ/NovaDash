package com.novadash.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novadash.data.AppMode
import com.novadash.data.CameraRepository
import com.novadash.data.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: CameraRepository,
    session: SessionState,
) : ViewModel() {

    val mode: StateFlow<AppMode> = session.mode
    val offline: Boolean get() = mode.value == AppMode.OFFLINE

    /**
     * Called when the active tab changes. Browsing tabs (Files/Settings) need recording
     * stopped; returning to a non-browsing tab resumes it. No-op offline (no camera).
     */
    fun setBrowsing(browsing: Boolean) {
        if (offline) return
        viewModelScope.launch {
            if (browsing) repository.pauseForBrowsing() else repository.resumeFromBrowsing()
        }
    }
}

package com.novadash.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novadash.data.AppMode
import com.novadash.data.CameraConnection
import com.novadash.data.CameraRepository
import com.novadash.data.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val repository: CameraRepository,
    private val session: SessionState,
) : ViewModel() {

    val connection: StateFlow<CameraConnection> = repository.connection

    fun connect() {
        session.setMode(AppMode.ONLINE)
        viewModelScope.launch { repository.connect() }
    }

    /** Enter the hub without the camera, to browse downloaded clips / map / moments. */
    fun goOffline() {
        session.setMode(AppMode.OFFLINE)
    }

    fun disconnect() {
        repository.disconnect()
    }
}

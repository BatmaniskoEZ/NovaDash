package com.novadash.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the app is talking to the camera (ONLINE) or just browsing downloaded data (OFFLINE). */
enum class AppMode { ONLINE, OFFLINE }

/** App-wide session mode, set at the Connect screen and read by the hub/tabs. */
@Singleton
class SessionState @Inject constructor() {
    private val _mode = MutableStateFlow(AppMode.ONLINE)
    val mode: StateFlow<AppMode> = _mode.asStateFlow()

    fun setMode(mode: AppMode) {
        _mode.value = mode
    }
}

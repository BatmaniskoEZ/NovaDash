package com.novadash.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers when the camera clock was last set to phone time (CameraRepository does that on
 * every connect). Clips whose filename time is after this instant are provably correctly
 * named — moment matching uses that to keep the ±1h DST fallback away from them.
 */
@Singleton
class ClockSyncStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("novadash_clock_sync", Context.MODE_PRIVATE)

    /** Epoch millis of the last successful clock sync; 0 if the camera was never synced. */
    val lastSyncMillis: Long
        get() = prefs.getLong(KEY, 0L)

    fun markSynced(epochMillis: Long) {
        prefs.edit { putLong(KEY, epochMillis) }
    }

    private companion object {
        const val KEY = "last_sync_millis"
    }
}

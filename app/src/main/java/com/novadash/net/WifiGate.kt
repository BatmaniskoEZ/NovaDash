package com.novadash.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pins this process's network traffic to the camera's Wi-Fi access point.
 *
 * The camera AP has no internet uplink, so on modern Android the system keeps the phone's
 * default (mobile-data) network active and would otherwise route our HTTP calls there. We
 * grab the Wi-Fi network explicitly and bind the process to it, so OkHttp reaches
 * 192.168.1.254. Call [bind] once the phone is connected to the camera's SSID, and
 * [unbind] when leaving the screen.
 */
@Singleton
class WifiGate @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private val _bound = MutableStateFlow(false)
    val bound: StateFlow<Boolean> = _bound

    private var callback: ConnectivityManager.NetworkCallback? = null

    fun bind() {
        if (callback != null) return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            // Explicitly do NOT require INTERNET — the camera AP has none.
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                cm.bindProcessToNetwork(network)
                _bound.value = true
            }

            override fun onLost(network: Network) {
                cm.bindProcessToNetwork(null)
                _bound.value = false
            }
        }
        callback = cb
        cm.requestNetwork(request, cb)
    }

    /**
     * Suspends until the process is actually bound to the camera Wi-Fi, or the timeout
     * elapses. Binding via [requestNetwork] is asynchronous, so callers must await this
     * before issuing the first request — otherwise it leaves on the default (mobile) route
     * and can't reach the camera's local address. Returns true if bound.
     */
    suspend fun awaitBound(timeoutMs: Long = 8000): Boolean {
        if (_bound.value) return true
        return withTimeoutOrNull(timeoutMs) { bound.first { it } } ?: false
    }

    fun unbind() {
        callback?.let { cm.unregisterNetworkCallback(it) }
        callback = null
        cm.bindProcessToNetwork(null)
        _bound.value = false
    }
}

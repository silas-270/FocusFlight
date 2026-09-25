package com.silas270.blocktime.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks whether the device's default network can actually reach the internet.
 *
 * "Connected" means the network is both INTERNET-capable and VALIDATED. A captive portal or
 * a Wi-Fi network with no uplink counts as offline, because tile downloads would just hang
 * until they timed out.
 *
 * Seeded synchronously in the constructor so the first frame already shows the right state,
 * then updated by the default-network callback. It is registered on the application context
 * and lives for the whole process (see [OfflineModeController.getInstance]).
 */
class ConnectivityMonitor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val _isConnected = MutableStateFlow(currentlyConnected())
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            _isConnected.value = capabilities.isUsable()
        }

        override fun onLost(network: Network) {
            _isConnected.value = false
        }
    }

    init {
        try {
            connectivityManager?.registerDefaultNetworkCallback(callback)
        } catch (e: RuntimeException) {
            // Only thrown if the per-app callback limit is hit; the seeded value stays in place.
            Log.e(TAG, "Could not register network callback", e)
        }
    }

    private fun currentlyConnected(): Boolean {
        val cm = connectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.isUsable()
    }

    private fun NetworkCapabilities.isUsable(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    private companion object {
        const val TAG = "ConnectivityMonitor"
    }
}

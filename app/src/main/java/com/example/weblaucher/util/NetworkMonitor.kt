package com.example.weblaucher.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

class NetworkMonitor(
    context: Context,
    private val onChanged: (Boolean) -> Unit
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            onChanged(true)
        }

        override fun onLost(network: Network) {
            onChanged(false)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val available = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            onChanged(available)
        }
    }

    fun register() {
        if (registered) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        registered = true
    }

    fun unregister() {
        if (!registered) return
        connectivityManager.unregisterNetworkCallback(callback)
        registered = false
    }
}

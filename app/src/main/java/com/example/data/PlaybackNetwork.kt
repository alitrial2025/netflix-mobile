package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class PlaybackNetwork(val online: Boolean = false, val wifiOrEthernet: Boolean = false)

fun playbackNetwork(context: Context): PlaybackNetwork {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return PlaybackNetwork()
    val network = manager.activeNetwork ?: return PlaybackNetwork()
    val caps = manager.getNetworkCapabilities(network) ?: return PlaybackNetwork()
    return PlaybackNetwork(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
}

fun observePlaybackNetwork(context: Context) = callbackFlow {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { trySend(playbackNetwork(context)) }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(playbackNetwork(context)) }
        override fun onLost(network: Network) { trySend(playbackNetwork(context)) }
    }
    manager?.registerDefaultNetworkCallback(callback)
    trySend(playbackNetwork(context))
    awaitClose { manager?.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()

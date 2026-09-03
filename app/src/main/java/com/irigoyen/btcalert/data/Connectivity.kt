package com.irigoyen.btcalert.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.irigoyen.btcalert.model.NetworkStatus

/**
 * Reads the device's network state. A failed HTTP call looks the same whether the phone is in
 * airplane mode or Coinbase is down; this is what tells those apart so the UI can say something
 * useful instead of listing four hostname errors.
 */
object Connectivity {

    fun status(context: Context): NetworkStatus {
        // If the service is somehow unavailable, assume online: never let this check be the
        // reason a fetch doesn't happen.
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return NetworkStatus.ONLINE
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return NetworkStatus.NONE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return NetworkStatus.NONE
        // VALIDATED means Android probed the network and got a real answer back. It's missing on a
        // captive portal and for a beat right after a network comes up, so UNVALIDATED still gets a
        // fetch attempt — it only changes how a failure is worded.
        return if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) NetworkStatus.ONLINE
        else NetworkStatus.UNVALIDATED
    }

    fun isOffline(context: Context): Boolean = status(context) == NetworkStatus.NONE
}

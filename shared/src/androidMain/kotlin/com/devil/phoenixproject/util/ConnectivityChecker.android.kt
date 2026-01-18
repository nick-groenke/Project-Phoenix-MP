package com.devil.phoenixproject.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

actual class ConnectivityChecker(private val context: Context) {
    actual fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

package dev.vinkyv.tvcontrol.utils

import android.content.Context

object DeviceStorage {
    private const val PREFS_NAME = "device_credentials"

    // Helper to get SharedPreferences instance
    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Save credentials for a specific device
    fun saveCredential(context: Context, deviceId: String, credentialValue: String) {
        getPrefs(context).edit()
            .putString("device_$deviceId", credentialValue)
            .apply()
    }

    // Retrieve credentials for a specific device
    fun getCredential(context: Context, deviceId: String): String {
        return getPrefs(context).getString("device_$deviceId", null).orEmpty()
    }

    fun hasCredential(context: Context, deviceId: String): Boolean {
        return getPrefs(context).contains("device_$deviceId")
    }

    // Delete credentials for a specific device (unpairing)
    fun removeCredential(context: Context, deviceId: String) {
        getPrefs(context).edit()
            .remove("device_$deviceId")
            .apply()
    }

    // Get a list of all paired device IDs
    fun getAllPairedDeviceIds(context: Context): List<String> {
        return getPrefs(context).all.keys
            .filter { it.startsWith("device_") }
            .map { it.removePrefix("device_") }
    }
}
package dev.vinkyv.tvcontrol.utils

import android.content.Context
import org.json.JSONException
import org.json.JSONObject

/** Credentials and last known address for a paired Apple TV. */
data class PairedDevice(
    val uuid: String,
    val name: String,
    val ip: String,
    /** Protocol name (as in pyatv's Protocol enum) -> credentials string. */
    val credentials: Map<String, String>
) {
    fun credentialsJson(): String = JSONObject(credentials).toString()
}

object DeviceStorage {
    private const val PREFS_NAME = "device_credentials"
    private const val DEVICE_PREFIX = "device_"
    private const val LAST_DEVICE_KEY = "last_device"

    private fun getPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getDevice(context: Context, uuid: String): PairedDevice? {
        val raw = getPrefs(context).getString(DEVICE_PREFIX + uuid, null) ?: return null
        return try {
            val obj = JSONObject(raw)
            val creds = obj.optJSONObject("credentials") ?: JSONObject()
            PairedDevice(
                uuid = uuid,
                name = obj.optString("name"),
                ip = obj.optString("ip"),
                credentials = creds.keys().asSequence().associateWith { creds.getString(it) }
            )
        } catch (_: JSONException) {
            // Older app versions stored the raw Companion credentials string
            PairedDevice(uuid, name = "", ip = "", credentials = mapOf(PROTOCOL_COMPANION to raw))
        }
    }

    fun hasCredential(context: Context, uuid: String, protocol: String): Boolean =
        !getDevice(context, uuid)?.credentials?.get(protocol).isNullOrEmpty()

    fun saveCredential(context: Context, device: DiscoveredDevice, protocol: String, credentials: String) {
        val existing = getDevice(context, device.uuid)?.credentials.orEmpty()
        save(context, PairedDevice(device.uuid, device.name, device.ip, existing + (protocol to credentials)))
    }

    /** Refresh name/address of an already paired device (IP may change between sessions). */
    fun updateAddress(context: Context, device: DiscoveredDevice) {
        val existing = getDevice(context, device.uuid) ?: return
        if (existing.ip != device.ip || existing.name != device.name) {
            save(context, existing.copy(name = device.name, ip = device.ip))
        }
    }

    private fun save(context: Context, device: PairedDevice) {
        val obj = JSONObject()
            .put("name", device.name)
            .put("ip", device.ip)
            .put("credentials", JSONObject(device.credentials))
        getPrefs(context).edit().putString(DEVICE_PREFIX + device.uuid, obj.toString()).apply()
    }

    fun removeDevice(context: Context, uuid: String) {
        val prefs = getPrefs(context)
        prefs.edit().apply {
            remove(DEVICE_PREFIX + uuid)
            if (prefs.getString(LAST_DEVICE_KEY, null) == uuid) remove(LAST_DEVICE_KEY)
        }.apply()
    }

    fun getAllPairedDevices(context: Context): List<PairedDevice> =
        getPrefs(context).all.keys
            .filter { it.startsWith(DEVICE_PREFIX) }
            .mapNotNull { getDevice(context, it.removePrefix(DEVICE_PREFIX)) }

    fun setLastDevice(context: Context, uuid: String?) {
        getPrefs(context).edit().putString(LAST_DEVICE_KEY, uuid).apply()
    }

    fun getLastDevice(context: Context): PairedDevice? =
        getPrefs(context).getString(LAST_DEVICE_KEY, null)?.let { getDevice(context, it) }
}

const val PROTOCOL_COMPANION = "Companion"
const val PROTOCOL_AIRPLAY = "AirPlay"

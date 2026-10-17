package dev.vinkyv.tvcontrol.utils

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

data class DiscoveredDevice(
    val name: String = "",
    val port: Int = 0,
    val ip: String = "",
    val uuid: String = ""
)

object Discovery {
    private const val TAG = "Discovery"
    private const val COMPANION_SERVICE = "_companion-link._tcp.local."

    /**
     * Browses for Apple TVs (Companion link service) and emits the growing device list.
     * Discovery runs until the collector cancels (e.g. with a timeout).
     */
    fun devices(context: Context): Flow<List<DiscoveredDevice>> = callbackFlow {
        val devices = LinkedHashMap<String, DiscoveredDevice>()
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val multicastLock = wifiManager.createMulticastLock("AtvDiscovery").apply {
            setReferenceCounted(false)
            acquire()
        }
        var jmdns: JmDNS? = null

        val listener = object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) {
                event.dns.requestServiceInfo(event.type, event.name, 1000)
            }

            override fun serviceRemoved(event: ServiceEvent) = Unit

            override fun serviceResolved(event: ServiceEvent) {
                val info = event.info
                val ip = info.inet4Addresses.firstOrNull()?.hostAddress ?: return
                // rpMRtID is the stable Companion identifier; fall back to the service name
                val uuid = info.getPropertyString("rpMRtID") ?: info.name
                val device = DiscoveredDevice(name = info.name, port = info.port, ip = ip, uuid = uuid)
                val snapshot = synchronized(devices) {
                    if (devices[uuid] == device) return
                    devices[uuid] = device
                    devices.values.toList()
                }
                trySend(snapshot)
            }
        }

        try {
            val address = getWifiIpAddress() ?: throw IOException("No Wi-Fi address found")
            jmdns = JmDNS.create(address).apply { addServiceListener(COMPANION_SERVICE, listener) }
        } catch (e: IOException) {
            Log.e(TAG, "Discovery failed", e)
            close(e)
        }

        awaitClose {
            try {
                jmdns?.removeServiceListener(COMPANION_SERVICE, listener)
                jmdns?.close()
            } catch (e: IOException) {
                Log.w(TAG, "Failed to close JmDNS", e)
            }
            if (multicastLock.isHeld) multicastLock.release()
        }
    }.flowOn(Dispatchers.IO)

    /** Prefer the Wi-Fi interface so mDNS isn't sent over mobile data. */
    private fun getWifiIpAddress(): InetAddress? {
        val candidates = Collections.list(NetworkInterface.getNetworkInterfaces())
            .filter { it.isUp && !it.isLoopback && it.supportsMulticast() }
            .sortedByDescending { it.name.startsWith("wlan") }

        return candidates.firstNotNullOfOrNull { networkInterface ->
            Collections.list(networkInterface.inetAddresses)
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
        }
    }
}

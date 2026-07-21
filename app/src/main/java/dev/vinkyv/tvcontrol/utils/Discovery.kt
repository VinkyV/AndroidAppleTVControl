package dev.vinkyv.tvcontrol.utils

import android.app.Activity.WIFI_SERVICE
import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val host: String = "",
    val port: Int = 0,
    val ip: String = "",
    val uuid: String = ""
)

class Discovery {
     fun discover(context: Context, onResult: (List<DiscoveredDevice>) -> Unit) {
         CoroutineScope(Dispatchers.IO).launch {
             val devices = mutableListOf<DiscoveredDevice>()
             var jmdns: JmDNS? = null
             var multicastLock: WifiManager.MulticastLock? = null

             class CompanionListener : ServiceListener {
                 override fun serviceAdded(event: ServiceEvent) {
                     jmdns?.requestServiceInfo(
                         event.type,
                         event.name
                     )
                 }

                 override fun serviceRemoved(event: ServiceEvent) {
                 }

                 override fun serviceResolved(event: ServiceEvent) {
                     val info = event.info

                     val device = info.inet4Addresses.first().hostAddress?.let {
                         DiscoveredDevice(
                             name = info.name,
                             host = info.hostAddresses.first(),
                             port = info.port,
                             ip = it,
                             uuid = info.getPropertyString("rpMRtID")
                         )
                     }

                     synchronized(devices) {
                         if (!devices.contains(device)) {
                             if (device != null) {
                                 devices.add(device)
                             }

                             CoroutineScope(Dispatchers.Main).launch {
                                 onResult(devices.toList())
                             }
                         }
                     }
                 }
             }

             try {
                 val wifiManager =
                     context.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager

                 multicastLock = wifiManager.createMulticastLock("JmDNSDiscovery")
                 multicastLock.setReferenceCounted(true)
                 multicastLock.acquire()

                 val address = getWifiIpAddress() ?: throw IOException("No WIFI address found")

                 jmdns = JmDNS.create(address)

                 jmdns.addServiceListener("_companion-link._tcp.local.", CompanionListener())

                 delay(5000)
             } catch (e: Exception) {
                 Log.e("JmDNS", e.stackTraceToString())
             } finally {
                 jmdns?.close()

                 multicastLock?.let {
                     if (it.isHeld) {
                         it.release()
                     }
                 }
             }
         }
    }

    private fun getWifiIpAddress(): InetAddress? {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())

        for (networkInterface in interfaces) {
            if (!networkInterface.isUp || networkInterface.isLoopback) continue

            val addresses = Collections.list(networkInterface.inetAddresses)

            for (address in addresses) {
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    return address
                }
            }
        }

        return null
    }
}

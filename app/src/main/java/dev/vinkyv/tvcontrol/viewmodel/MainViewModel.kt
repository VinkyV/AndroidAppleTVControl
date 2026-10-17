package dev.vinkyv.tvcontrol.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.vinkyv.tvcontrol.atvSession
import dev.vinkyv.tvcontrol.session.ConnectionState
import dev.vinkyv.tvcontrol.utils.DeviceStorage
import dev.vinkyv.tvcontrol.utils.DiscoveredDevice
import dev.vinkyv.tvcontrol.utils.Discovery
import dev.vinkyv.tvcontrol.utils.PROTOCOL_AIRPLAY
import dev.vinkyv.tvcontrol.utils.PROTOCOL_COMPANION
import dev.vinkyv.tvcontrol.utils.PairedDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class PinRequest(val deviceName: String, val protocol: String)

/** UI state for discovery and pairing; the connection itself lives in [dev.vinkyv.tvcontrol.session.AtvSession]. */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val context get() = getApplication<Application>()
    val session = app.atvSession
    private val atv = session.atv

    val connection = session.connection
    val busyStatus = session.busyStatus
    val nowPlaying = session.nowPlaying
    val artwork = session.artwork
    val messages = session.messages

    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    private val _pairedDevices = MutableStateFlow(DeviceStorage.getAllPairedDevices(app))
    val pairedDevices: StateFlow<List<PairedDevice>> = _pairedDevices.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _pinRequest = MutableStateFlow<PinRequest?>(null)
    val pinRequest: StateFlow<PinRequest?> = _pinRequest.asStateFlow()
    private var pinResult: CompletableDeferred<String?>? = null

    private var discoveryJob: Job? = null

    /** True while the audio screen is visible: hardware volume keys then control the Apple TV. */
    var volumeKeysControlTv = false

    // ---------- discovery ----------

    fun startDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            _isDiscovering.value = true
            try {
                withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) {
                    Discovery.devices(context).collect { list ->
                        _devices.value = list
                        list.forEach { DeviceStorage.updateAddress(context, it) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                session.message("Discovery failed: ${e.message}")
            } finally {
                _isDiscovering.value = false
                refreshPairedDevices()
            }
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
    }

    // ---------- pairing / connection ----------

    fun selectDevice(device: DiscoveredDevice) = session.runBusy {
        stopDiscovery()
        if (!DeviceStorage.hasCredential(context, device.uuid, PROTOCOL_COMPANION)) {
            if (!pairProtocol(device, PROTOCOL_COMPANION)) return@runBusy
            // AirPlay credentials unlock now playing info, artwork and seeking (MRP over AirPlay)
            pairProtocol(device, PROTOCOL_AIRPLAY, optional = true)
        }
        DeviceStorage.getDevice(context, device.uuid)?.let { session.connectTo(it) }
    }

    fun connectPaired(device: PairedDevice) = session.runBusy { session.connectTo(device) }

    /** Pair AirPlay for the connected device so metadata becomes available. */
    fun pairAirPlay() {
        val current = (connection.value as? ConnectionState.Connected)?.device ?: return
        session.runBusy {
            val target = DiscoveredDevice(name = current.name, ip = current.ip, uuid = current.uuid)
            if (pairProtocol(target, PROTOCOL_AIRPLAY)) {
                DeviceStorage.getDevice(context, current.uuid)?.let { session.connectTo(it) }
            }
        }
    }

    fun submitPin(pin: String) {
        pinResult?.complete(pin)
    }

    fun cancelPin() {
        pinResult?.complete(null)
    }

    fun disconnect() = session.disconnect()

    fun forgetDevice(device: PairedDevice) {
        val connected = connection.value as? ConnectionState.Connected
        if (connected?.device?.uuid == device.uuid) session.disconnect()
        DeviceStorage.removeDevice(context, device.uuid)
        refreshPairedDevices()
    }

    private suspend fun pairProtocol(device: DiscoveredDevice, protocol: String, optional: Boolean = false): Boolean {
        session.setBusyStatus("Pairing $protocol with ${device.name}…")
        val start = atv.startPairing(device.ip, protocol)
        if (!start.ok) {
            if (!(optional && start.code == "unsupported")) session.message("$protocol pairing failed: ${start.message}")
            return false
        }
        if (!start.data.optBoolean("device_provides_pin", true)) {
            atv.cancelPairing()
            session.message("$protocol pairing without an on-screen PIN is not supported")
            return false
        }

        val pin = requestPin(device.name, protocol)
        if (pin == null) {
            atv.cancelPairing()
            return false
        }

        session.setBusyStatus("Verifying PIN…")
        val result = atv.submitPin(pin)
        if (!result.ok) {
            session.message(
                if (result.code == "bad_pin") "Incorrect PIN, try again" else "$protocol pairing failed: ${result.message}"
            )
            return false
        }
        val credentials = result.data.optString("credentials")
        if (credentials.isEmpty() || credentials == "null") {
            session.message("$protocol pairing returned no credentials")
            return false
        }
        DeviceStorage.saveCredential(context, device, protocol, credentials)
        refreshPairedDevices()
        return true
    }

    private suspend fun requestPin(deviceName: String, protocol: String): String? {
        val deferred = CompletableDeferred<String?>()
        pinResult = deferred
        _pinRequest.value = PinRequest(deviceName, protocol)
        return try {
            deferred.await()
        } finally {
            _pinRequest.value = null
            pinResult = null
        }
    }

    private fun refreshPairedDevices() {
        _pairedDevices.value = DeviceStorage.getAllPairedDevices(context)
    }

    // ---------- playback ----------

    fun sendCommand(command: String, action: String? = null) = session.sendCommand(command, action)

    fun togglePlayPause() = session.togglePlayPause()

    fun seekTo(seconds: Int) = session.seekTo(seconds)

    suspend fun observeNowPlaying() = session.observeNowPlaying()

    /** Handles a hardware volume key press; returns false if it should go to the phone instead. */
    fun onVolumeKey(up: Boolean): Boolean {
        if (!volumeKeysControlTv || connection.value !is ConnectionState.Connected) return false
        session.adjustVolume(up)
        return true
    }

    override fun onCleared() {
        pinResult?.complete(null)
        super.onCleared()
    }

    private companion object {
        const val DISCOVERY_TIMEOUT_MS = 10_000L
    }
}

package dev.vinkyv.tvcontrol

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AtvController(context: Context) {
    init {
        if (!Python.isStarted()) Python.start(AndroidPlatform(context))
        try {
            Python.getInstance().getModule("main")
        } catch (e: Exception) {
            Log.e("ChaquopyTest", "pyatv import failed", e)
        }
    }

    private val bridge = Python.getInstance().getModule("main").get("bridge")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    fun pair(address: String, callback: (Boolean, String) -> Unit) {
        scope.launch {
            val output = bridge?.callAttr("start_pairing", address, "Companion").toString()
            val isSuccess = output.contains("\"ok\"")
            Log.d("AtvController", "pair(): $output")

            withContext(Dispatchers.Main) {
                callback(isSuccess, output)
            }
        }
    }

    fun submitPin(pin: String, callback: (Boolean, String) -> Unit) {
        scope.launch {
            val output = bridge?.callAttr("submit_pin", pin).toString()
            val isSuccess = output.contains("\"ok\"")
            Log.d("AtvController", "submitPin(): $output")

            withContext(Dispatchers.Main) {
                callback(isSuccess, output)
            }
        }
    }

    fun connect(address: String, protocolName: String, credentials: String, callback: (Boolean, String) -> Unit) {
        scope.launch {
            val output = bridge?.callAttr("connect", address, protocolName, credentials).toString()
            val isSuccess = output.contains("\"ok\"")
            Log.d("AtvController", "connect(): $output")

            if (isSuccess) {
                _isConnected.value = true
            }

            withContext(Dispatchers.Main) {
                callback(isSuccess, output)
            }
        }
    }

    fun sendCommand(command: String, callback: (Boolean, String) -> Unit) {
        scope.launch {
            val output = bridge?.callAttr("send_command", command).toString()
            val isSuccess = output.contains("\"ok\"")
            Log.d("AtvController", "sendCommand(): $output")

            if (!isSuccess && _isConnected.value) {
                _isConnected.value = false
            }

            withContext(Dispatchers.Main) {
                callback(isSuccess, output)
            }
        }
    }

    fun getPlaying(callback: (Boolean, String) -> Unit) {
        scope.launch {
            val output = bridge?.callAttr("get_playing").toString()
            val isSuccess = output.contains("\"ok\"")
            Log.d("AtvController", "getPlaying(): $output")

            if (!isSuccess && _isConnected.value) {
                _isConnected.value = false
            }

            withContext(Dispatchers.Main) {
                callback(isSuccess, output)
            }
        }
    }
}
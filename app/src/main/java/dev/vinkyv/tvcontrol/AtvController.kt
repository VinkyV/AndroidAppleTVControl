package dev.vinkyv.tvcontrol

import android.content.Context
import android.util.Log
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/** Result of a call into the Python bridge. */
data class BridgeResult(val ok: Boolean, val code: String, val message: String, val data: JSONObject) {
    val isNotConnected get() = code == CODE_NOT_CONNECTED

    companion object {
        const val CODE_NOT_CONNECTED = "not_connected"

        fun parse(raw: String?): BridgeResult {
            if (raw == null) return BridgeResult(false, "error", "no response", JSONObject())
            return try {
                val json = JSONObject(raw)
                val ok = json.optString("status") == "ok"
                BridgeResult(ok, json.optString("code", if (ok) "ok" else "error"), json.optString("message"), json)
            } catch (e: JSONException) {
                BridgeResult(false, "error", raw, JSONObject())
            }
        }
    }
}

/** Thin, thread-safe wrapper around the Python `main.bridge` object (pyatv). */
class AtvController(context: Context) {
    init {
        if (!Python.isStarted()) Python.start(AndroidPlatform(context.applicationContext))
    }

    // Importing pyatv is slow, so the module is loaded lazily on the first (background) call
    private val bridge: PyObject by lazy {
        Python.getInstance().getModule("main").get("bridge")
            ?: error("Python bridge not initialised")
    }

    /** Calls a bridge method returning a JSON string. Never throws. */
    private suspend fun call(method: String, vararg args: Any?): BridgeResult = withContext(Dispatchers.IO) {
        val result = try {
            BridgeResult.parse(bridge.callAttr(method, *args)?.toString())
        } catch (e: Exception) {
            Log.e(TAG, "$method failed", e)
            BridgeResult(false, "error", e.message ?: e.javaClass.simpleName, JSONObject())
        }
        Log.d(TAG, "$method -> ${result.code} ${result.message}")
        result
    }

    suspend fun startPairing(address: String, protocol: String) = call("start_pairing", address, protocol)

    suspend fun submitPin(pin: String) = call("submit_pin", pin)

    suspend fun cancelPairing() = call("cancel_pairing")

    /** [credentialsJson] is a JSON object mapping protocol name to credentials. */
    suspend fun connect(address: String, credentialsJson: String) = call("connect", address, credentialsJson)

    suspend fun disconnect() = call("disconnect")

    /** [action] is null for a tap, or "double" / "hold". */
    suspend fun sendCommand(command: String, action: String? = null) = call("send_command", command, action.orEmpty())

    suspend fun setPosition(seconds: Int) = call("set_position", seconds)

    suspend fun getPlaying() = call("get_playing")

    suspend fun getArtwork(size: Int): ByteArray? = withContext(Dispatchers.IO) {
        try {
            bridge.callAttr("get_artwork", size)?.toJava(ByteArray::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "get_artwork failed", e)
            null
        }
    }

    private companion object {
        const val TAG = "AtvController"
    }
}

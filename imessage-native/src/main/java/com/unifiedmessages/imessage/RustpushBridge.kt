package com.unifiedmessages.imessage

import androidx.annotation.Keep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Compile-only bridge probe. It does not imply successful Apple registration. */
@Keep
class RustpushBridge {
    companion object {
        private val nativeLoaded: Boolean = runCatching { System.loadLibrary("unified_imessage_native") }.isSuccess
    }

    fun engineVersion(): String =
        if (nativeLoaded) nativeEngineVersion() else "native-library-unavailable"

    suspend fun probeRelay(endpoint: String, pairingCode: String): String = withContext(Dispatchers.IO) {
        if (nativeLoaded) nativeProbeRelay(endpoint, pairingCode) else "native-library-unavailable"
    }

    private external fun nativeEngineVersion(): String
    private external fun nativeProbeRelay(endpoint: String, pairingCode: String): String
}

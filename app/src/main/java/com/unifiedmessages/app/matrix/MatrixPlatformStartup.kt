package com.unifiedmessages.app.matrix

import android.app.Application
import android.util.Log
import com.unifiedmessages.app.BuildConfig
import org.matrix.rustcomponents.sdk.LogLevel
import org.matrix.rustcomponents.sdk.TracingConfiguration
import org.matrix.rustcomponents.sdk.initPlatform

/** Initializes the Android-specific SDK before any Matrix client or sync worker can start. */
object MatrixPlatformStartup {
    private const val TAG = "MatrixPlatformStartup"

    @Volatile
    private var attempted = false

    @Volatile
    var errorMessage: String? = null
        private set

    @Synchronized
    fun initialize(application: Application) {
        if (attempted) return
        attempted = true
        try {
            // Native TLS verification loads this class by name. Check it before calling into Rust.
            Class.forName("org.rustls.platformverifier.CertificateVerifier", false, application.classLoader)
            initPlatform(
                config = TracingConfiguration(
                    logLevel = LogLevel.INFO,
                    traceLogPacks = emptyList(),
                    extraTargets = emptyList(),
                    // The SDK sends this stream to Android Logcat. Keep release builds quiet.
                    writeToStdoutOrSystem = BuildConfig.DEBUG,
                    writeToFiles = null,
                    sentryConfig = null,
                ),
                useLightweightTokioRuntime = false,
            )
            Log.i(TAG, "Matrix Android platform and TLS verifier initialized")
            if (BuildConfig.DEBUG) Log.i("MatrixDebug", "Matrix SDK tracing enabled for Logcat (SDK network targets use their built-in levels)")
        } catch (error: Throwable) {
            errorMessage = "Matrix could not start securely. Update or reinstall UnifiedMessages, then try again."
            Log.e(TAG, "Matrix Android platform initialization failed", error)
        }
    }

    fun requireInitialized() {
        check(attempted && errorMessage == null) {
            errorMessage ?: "Matrix platform is not initialized yet. Restart UnifiedMessages."
        }
    }
}

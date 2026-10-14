package com.unifiedmessages.app.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Required by the default SMS role; handles the system's quick text reply action. */
@AndroidEntryPoint
class RespondViaMessageService : Service() {
    @Inject lateinit var transport: SmsTransport

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_RESPOND_VIA_MESSAGE) return START_NOT_STICKY
        val address = intent.data?.schemeSpecificPart?.takeIf { it.isNotBlank() }
        val body = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
        if (address == null || body == null) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                transport.sendToAddress(address, body)
            } catch (error: Exception) {
                Log.e(TAG, "Could not send quick reply", error)
            } finally {
                stopSelfResult(startId)
            }
        }
        return START_NOT_STICKY
    }

    private companion object {
        const val TAG = "RespondViaMessage"
        const val ACTION_RESPOND_VIA_MESSAGE = "android.intent.action.RESPOND_VIA_MESSAGE"
    }
}

package com.unifiedmessages.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Receives the explicit sent and carrier delivery PendingIntents for each SMS segment. */
@AndroidEntryPoint
class SmsStatusReceiver : BroadcastReceiver() {
    @Inject lateinit var transport: SmsTransport

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val callbackResult = resultCode
        CoroutineScope(Dispatchers.IO).launch {
            try {
                transport.handleStatus(intent, callbackResult)
            } catch (error: Exception) {
                Log.e(TAG, "Failed to update SMS status", error)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object { const val TAG = "SmsStatusReceiver" }
}

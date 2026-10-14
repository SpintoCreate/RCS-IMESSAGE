package com.unifiedmessages.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Android sends this broadcast only to the current default SMS application. */
@AndroidEntryPoint
class SmsDeliverReceiver : BroadcastReceiver() {
    @Inject lateinit var transport: SmsTransport

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                transport.handleDelivery(intent)
                pending.setResultCode(Telephony.Sms.Intents.RESULT_SMS_HANDLED)
            } catch (error: Exception) {
                Log.e(TAG, "Failed to save incoming SMS", error)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object { const val TAG = "SmsDeliverReceiver" }
}

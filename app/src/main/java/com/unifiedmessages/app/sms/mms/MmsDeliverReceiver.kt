package com.unifiedmessages.app.sms.mms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives the carrier WAP push only while UnifiedMessages holds the default SMS role. */
@AndroidEntryPoint
class MmsDeliverReceiver : BroadcastReceiver() {
    @Inject lateinit var transport: MmsTransport

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val pending = goAsync()
        val incoming = Intent(intent)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                transport.handleDelivery(incoming)
            } catch (error: Exception) {
                Log.e("MmsDeliverReceiver", "Could not process MMS push", error)
            } finally {
                pending.finish()
            }
        }
    }
}

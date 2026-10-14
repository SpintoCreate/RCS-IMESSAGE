package com.unifiedmessages.app.sms

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.unifiedmessages.app.data.local.DeliveryStatus
import com.unifiedmessages.app.data.local.ProviderIds
import com.unifiedmessages.app.data.local.UnifiedMessagesDatabase
import com.unifiedmessages.app.provider.DeliveryUpdated
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Owns the carrier SMS path. Every sent or received SMS is first written to the system provider. */
@Singleton
class SmsTransport @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val importer: TelephonyImporter,
    private val processor: SmsEventProcessor,
    private val database: UnifiedMessagesDatabase,
) {
    private val resolver get() = context.contentResolver
    private val callbackState = context.getSharedPreferences("sms_callback_parts", Context.MODE_PRIVATE)
    private val callbackLock = Any()

    suspend fun send(threadId: String, text: String) = withContext(Dispatchers.IO) {
        require(text.isNotBlank()) { "Cannot send an empty SMS" }
        requireDefaultSmsRole()
        require(ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            "SMS permission is required"
        }
        val thread = database.threadDao().getThread(threadId) ?: error("Conversation no longer exists")
        require(thread.providerId == PROVIDER_ID) { "This conversation is not an SMS thread" }
        val recipients = database.threadDao().getParticipants(threadId)
            .filterNot { it.isSelf }
            .map { it.address }
            .let { ThreadRecipients.requireSendableExisting(it, PhoneNumbers.deviceRegion(context)) }
        require(recipients.size == 1) { "Group conversations require MMS" }
        CarrierThreadVerifier.requireSafeRecipients(context, database, thread, recipients)
        sendToAddress(
            recipients.single(),
            text,
            thread.providerThreadId.toLongOrNull(),
            database.messageDao().getLatestSubscriptionId(threadId),
        )
    }

    /** Also handles the system's quick-reply service, which may target a new phone number. */
    suspend fun sendToAddress(
        address: String,
        text: String,
        knownThreadId: Long? = null,
        preferredSubscriptionId: Int? = null,
    ) = withContext(Dispatchers.IO) {
        require(address.isNotBlank() && text.isNotBlank()) { "A recipient and message are required" }
        requireDefaultSmsRole()
        require(ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            "SMS permission is required"
        }
        val systemThreadId = knownThreadId ?: Telephony.Threads.getOrCreateThreadId(context, address)
        val subscriptionId = preferredSubscriptionId?.takeIf(SubscriptionManager::isValidSubscriptionId)
            ?: SubscriptionManager.getDefaultSmsSubscriptionId()
        require(SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
            "Choose a default SMS SIM in phone settings before sending"
        }
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, text)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.THREAD_ID, systemThreadId)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
            put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
        }
        val uri = resolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values)
            ?: error("Could not save SMS in the system message store")
        val systemId = uri.lastPathSegment?.toLongOrNull()
            ?: error("System message store returned an invalid SMS URI")
        try {
            importer.importSms(uri)
            check(database.messageDao().getMessage(ProviderIds.message(PROVIDER_ID, "sms:$systemId")) != null) {
                "Outgoing SMS was not imported into the app database"
            }
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            }
            val parts = smsManager.divideMessage(text)
            require(parts.isNotEmpty() && parts.size <= MAX_PARTS) { "SMS is too long to send" }
            val sentIntents = ArrayList<PendingIntent>(parts.size)
            val deliveryIntents = ArrayList<PendingIntent>(parts.size)
            parts.indices.forEach { index ->
                sentIntents += callbackIntent(systemId, ACTION_SENT, index, parts.size)
                deliveryIntents += callbackIntent(systemId, ACTION_DELIVERED, index, parts.size)
            }
            if (parts.size == 1) {
                smsManager.sendTextMessage(address, null, parts.single(), sentIntents.single(), deliveryIntents.single())
            } else {
                smsManager.sendMultipartTextMessage(address, null, parts, sentIntents, deliveryIntents)
            }
        } catch (error: Exception) {
            updateSystemStatus(uri, sent = false)
            try {
                processor.ingest(statusEvent(systemId, "dispatch-failed", DeliveryStatus.FAILED))
            } catch (_: Exception) {
                // The system row still records the failure and can be imported on the next scan.
            }
            throw error
        }
    }

    /** Called only for SMS_DELIVER, which Android directs to the current default SMS app. */
    suspend fun handleDelivery(intent: Intent) = withContext(Dispatchers.IO) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return@withContext
        requireDefaultSmsRole()
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isEmpty()) return@withContext
        val address = parts.firstNotNullOfOrNull {
            (it.displayOriginatingAddress ?: it.originatingAddress)?.takeIf(String::isNotBlank)
        } ?: return@withContext
        val body = parts.joinToString(separator = "") { it.displayMessageBody ?: it.messageBody.orEmpty() }
        if (body.isBlank()) return@withContext
        val date = parts.first().timestampMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        val subscriptionId = intent.getIntExtra(
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            SubscriptionManager.INVALID_SUBSCRIPTION_ID,
        )
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, date)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
            }
        }
        val uri = resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            ?: error("Could not save incoming SMS in the system message store")
        importer.importSms(uri)
    }

    /** Sent and delivery callbacks have unique immutable PendingIntents for every multipart segment. */
    suspend fun handleStatus(intent: Intent, resultCode: Int) = withContext(Dispatchers.IO) {
        val action = intent.action
        if (action != ACTION_SENT && action != ACTION_DELIVERED) return@withContext
        val systemId = intent.getLongExtra(EXTRA_SYSTEM_ID, -1L)
        val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, -1)
        val partCount = intent.getIntExtra(EXTRA_PART_COUNT, -1)
        if (systemId < 0L || partCount !in 1..MAX_PARTS || partIndex !in 0 until partCount) return@withContext
        val success = resultCode == Activity.RESULT_OK
        val aggregate = recordCallback(systemId, action, partIndex, partCount, success)
        val uri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, systemId.toString())
        when {
            !success -> {
                updateSystemStatus(uri, sent = false)
                processor.ingest(statusEvent(systemId, "$action:$partIndex:failed", DeliveryStatus.FAILED))
            }
            aggregate.allSucceeded && !aggregate.anyFailed && action == ACTION_SENT -> {
                updateSystemStatus(uri, sent = true)
                processor.ingest(statusEvent(systemId, "sent-complete", DeliveryStatus.SENT))
            }
            aggregate.allSucceeded && !aggregate.anyFailed && action == ACTION_DELIVERED -> {
                resolver.update(uri, ContentValues().apply { put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_COMPLETE) }, null, null)
                processor.ingest(statusEvent(systemId, "delivered-complete", DeliveryStatus.DELIVERED))
            }
        }
    }

    private fun callbackIntent(systemId: Long, action: String, partIndex: Int, partCount: Int): PendingIntent {
        val intent = Intent(context, SmsStatusReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("unifiedmessages://sms-status/$systemId/$action/$partIndex")
            putExtra(EXTRA_SYSTEM_ID, systemId)
            putExtra(EXTRA_PART_INDEX, partIndex)
            putExtra(EXTRA_PART_COUNT, partCount)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun statusEvent(systemId: Long, eventSuffix: String, status: DeliveryStatus) = DeliveryUpdated(
        providerId = PROVIDER_ID,
        eventId = "sms-status:$systemId:$eventSuffix",
        occurredAt = System.currentTimeMillis(),
        providerMessageId = "sms:$systemId",
        status = status,
    )

    private fun updateSystemStatus(uri: Uri, sent: Boolean) {
        resolver.update(
            uri,
            ContentValues().apply {
                put(Telephony.Sms.TYPE, if (sent) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_FAILED)
                put(Telephony.Sms.STATUS, if (sent) Telephony.Sms.STATUS_PENDING else Telephony.Sms.STATUS_FAILED)
            },
            null,
            null,
        )
    }

    private data class CallbackAggregate(val allSucceeded: Boolean, val anyFailed: Boolean)

    /** Synchronous commit keeps multipart progress when Android restarts the process between callbacks. */
    private fun recordCallback(
        systemId: Long,
        action: String,
        partIndex: Int,
        partCount: Int,
        success: Boolean,
    ): CallbackAggregate = synchronized(callbackLock) {
        val base = "$systemId:$action"
        val completed = callbackState.getString("$base:completed", "")
            .orEmpty().split(',').mapNotNull(String::toIntOrNull).toMutableSet()
        if (success) completed += partIndex
        val failed = callbackState.getBoolean("$base:failed", false) || !success
        callbackState.edit(commit = true) {
            putString("$base:completed", completed.sorted().joinToString(","))
            putBoolean("$base:failed", failed)
        }
        CallbackAggregate(completed.size == partCount, failed)
    }

    private fun requireDefaultSmsRole() {
        val roles = context.getSystemService(RoleManager::class.java)
        check(roles.isRoleAvailable(RoleManager.ROLE_SMS) && roles.isRoleHeld(RoleManager.ROLE_SMS)) {
            "UnifiedMessages must be the default SMS app"
        }
    }

    companion object {
        const val PROVIDER_ID = "sms"
        const val ACTION_SENT = "com.unifiedmessages.app.sms.SENT"
        const val ACTION_DELIVERED = "com.unifiedmessages.app.sms.DELIVERED"
        const val EXTRA_SYSTEM_ID = "system_sms_id"
        const val EXTRA_PART_INDEX = "sms_part_index"
        const val EXTRA_PART_COUNT = "sms_part_count"
        private const val MAX_PARTS = 255
    }
}

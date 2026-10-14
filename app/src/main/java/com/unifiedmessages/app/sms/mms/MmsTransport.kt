package com.unifiedmessages.app.sms.mms

import android.app.Activity
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ContentUris
import android.content.ContentValues
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Base64
import android.util.Log
import com.android.mms.dom.smil.parser.SmilXmlSerializer
import com.google.android.mms.pdu_alt.CharacterSets
import com.google.android.mms.pdu_alt.DeliveryInd
import com.google.android.mms.pdu_alt.EncodedStringValue
import com.google.android.mms.pdu_alt.NotificationInd
import com.google.android.mms.pdu_alt.PduBody
import com.google.android.mms.pdu_alt.PduComposer
import com.google.android.mms.pdu_alt.PduHeaders
import com.google.android.mms.pdu_alt.PduParser
import com.google.android.mms.pdu_alt.PduPart
import com.google.android.mms.pdu_alt.PduPersister
import com.google.android.mms.pdu_alt.RetrieveConf
import com.google.android.mms.pdu_alt.SendConf
import com.google.android.mms.pdu_alt.SendReq
import com.google.android.mms.smil.SmilHelper
import com.unifiedmessages.app.data.local.DeliveryStatus
import com.unifiedmessages.app.data.local.UnifiedMessagesDatabase
import com.unifiedmessages.app.provider.DeliveryUpdated
import com.unifiedmessages.app.sms.CarrierThreadVerifier
import com.unifiedmessages.app.sms.PhoneNumbers
import com.unifiedmessages.app.sms.SmsEventProcessor
import com.unifiedmessages.app.sms.TelephonyImporter
import com.unifiedmessages.app.sms.ThreadRecipients
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** MMS uses Android's public transfer API; the bundled codec only composes and persists PDUs. */
@Singleton
class MmsTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importer: TelephonyImporter,
    private val processor: SmsEventProcessor,
    private val database: UnifiedMessagesDatabase,
) {
    private val preferences = context.getSharedPreferences("mms_transfers", Context.MODE_PRIVATE)
    private val transferMutex = Mutex()

    /** Sends a text or media MMS in an existing SMS/MMS thread. */
    suspend fun send(threadId: String, body: String?, attachmentUris: List<Uri>) = withContext(Dispatchers.IO) {
        require(isDefaultSmsApp()) { "UnifiedMessages must be the default SMS app to send MMS" }
        require(!body.isNullOrBlank() || attachmentUris.isNotEmpty()) { "MMS must contain text or media" }
        val thread = database.threadDao().getThread(threadId) ?: error("Unknown conversation")
        require(thread.source.equals("SMS", true) || thread.source.equals("MMS", true)) {
            "This conversation is not a carrier message"
        }
        val recipients = database.threadDao().getParticipants(threadId)
            .filterNot { it.isSelf }
            .map { it.address }
            .let { ThreadRecipients.requireSendableExisting(it, PhoneNumbers.deviceRegion(context)) }
        CarrierThreadVerifier.requireSafeRecipients(context, database, thread, recipients)
        val orderedRecipients = recipients.sorted()

        val subscriptionId = database.messageDao().getLatestSubscriptionId(threadId)
            ?.takeIf(SubscriptionManager::isValidSubscriptionId)
            ?: SmsManager.getDefaultSmsSubscriptionId()
                .takeIf(SubscriptionManager::isValidSubscriptionId)
            ?: error("Choose a default SMS SIM in Android settings before sending MMS")
        val manager = managerFor(subscriptionId)
        val request = composeRequest(orderedRecipients, body, attachmentUris, subscriptionId, manager)
        val pdu = PduComposer(context, request).make() ?: error("Could not encode MMS")
        val outboxUri = PduPersister.getPduPersister(context).persist(
            request, Telephony.Mms.Outbox.CONTENT_URI, true, orderedRecipients.size > 1, null, subscriptionId,
        ) ?: error("Could not save MMS in system outbox")
        val systemId = ContentUris.parseId(outboxUri)
        val token = UUID.randomUUID().toString()
        val fileName = "mms-send-$token.pdu"
        val file = File(context.cacheDir, fileName)
        try {
            file.writeBytes(pdu)
            savePending(token, PendingTransfer("send", outboxUri.toString(), fileName, subscriptionId))
            importer.importMms(outboxUri)
            manager.sendMultimediaMessage(context, fileUri(fileName), null, null, callback(token, mutable = true))
        } catch (error: Exception) {
            try {
                markSentResult(outboxUri, systemId, token, success = false)
            } catch (statusError: Exception) {
                Log.e(TAG, "Could not record failed MMS send", statusError)
            }
            clearPending(token, fileName)
            throw error
        }
    }

    /** Handles WAP PUSH notifications and MMS delivery reports. Called from the default-SMS receiver. */
    suspend fun handleDelivery(intent: Intent) = withContext(Dispatchers.IO) {
        if (!isDefaultSmsApp()) return@withContext
        val bytes = intent.getByteArrayExtra("data") ?: return@withContext
        val pdu = PduParser(bytes).parse() ?: return@withContext
        val subscriptionId = intent.getIntExtra("subscription", SmsManager.getDefaultSmsSubscriptionId())
        when (pdu) {
            is NotificationInd -> {
                transferMutex.lock()
                try { queueDownload(pdu, subscriptionId) } finally { transferMutex.unlock() }
            }
            is DeliveryInd -> applyDeliveryReport(pdu, bytes)
        }
    }

    /** Completes a platform MMS send/download callback, even if the app was recreated meanwhile. */
    suspend fun handleTransfer(intent: Intent, resultCode: Int) = withContext(Dispatchers.IO) {
        transferMutex.lock()
        try {
            val token = intent.getStringExtra(EXTRA_TOKEN) ?: return@withContext
            val pending = pending(token) ?: return@withContext
            if (intent.getIntExtra(EXTRA_ATTEMPT, 0) != pending.attempt) return@withContext
            val uri = Uri.parse(pending.messageUri)
            when (pending.kind) {
                "send" -> {
                    val systemId = ContentUris.parseId(uri)
                    val response = preferences.getString("$token.response", null)?.let {
                        Base64.decode(it, Base64.DEFAULT)
                    }
                    markSentResult(uri, systemId, token, resultCode == Activity.RESULT_OK, response)
                    clearPending(token, pending.fileName)
                }
                "download" -> {
                    if (resultCode != Activity.RESULT_OK) {
                        markDownloadFailed(uri)
                        scheduleDownloadRetry(token, pending)
                        return@withContext
                    }
                    try {
                        finishDownloadedMessage(token, pending)
                    } catch (error: Exception) {
                        markDownloadFailed(uri)
                        scheduleDownloadRetry(token, pending)
                        Log.e(TAG, "Could not save downloaded MMS; retry scheduled", error)
                    }
                }
            }
        } finally {
            transferMutex.unlock()
        }
    }

    /** Retries a failed download with the saved carrier URL, including after process restart. */
    suspend fun retryDownload(token: String) = withContext(Dispatchers.IO) {
        transferMutex.lock()
        try {
            val pending = pending(token) ?: return@withContext
            if (pending.kind != "download" || pending.attempt >= MAX_DOWNLOAD_RETRIES) return@withContext
            if (preferences.getString("$token.inbox", null) != null) {
                try {
                    finishDownloadedMessage(token, pending)
                } catch (error: Exception) {
                    Log.e(TAG, "Could not import downloaded MMS; retry scheduled", error)
                    val attempt = pending.attempt + 1
                    check(preferences.edit().putInt("$token.attempt", attempt).commit()) {
                        "Could not save MMS import retry state"
                    }
                    scheduleDownloadRetry(token, pending.copy(attempt = attempt))
                }
                return@withContext
            }
            val location = pending.location ?: error("Missing saved MMS download URL")
            val attempt = pending.attempt + 1
            val oldFile = File(context.cacheDir, pending.fileName)
            val fileName = "mms-download-$token-$attempt.pdu"
            check(preferences.edit().putString("$token.file", fileName)
                .putInt("$token.attempt", attempt).commit()) {
                "Could not save MMS retry state"
            }
            oldFile.delete()
            try {
                managerFor(pending.subscriptionId).downloadMultimediaMessage(
                    context, location, fileUri(fileName), null, callback(token, attempt),
                )
            } catch (error: Exception) {
                Log.e(TAG, "MMS retry could not be started", error)
                markDownloadFailed(Uri.parse(pending.messageUri))
                scheduleDownloadRetry(token, pending.copy(fileName = fileName, attempt = attempt))
            }
        } finally {
            transferMutex.unlock()
        }
    }

    private suspend fun finishDownloadedMessage(token: String, pending: PendingTransfer) {
        val inboxUri = preferences.getString("$token.inbox", null)?.let(Uri::parse) ?: run {
            val bytes = File(context.cacheDir, pending.fileName).readBytes()
            val pdu = PduParser(bytes).parse() as? RetrieveConf
                ?: error("MMS download did not contain a retrieve response")
            val uri = findDownloadedMessage(pdu) ?: PduPersister.getPduPersister(context).persist(
                pdu, Telephony.Mms.Inbox.CONTENT_URI, true, true, null, pending.subscriptionId,
            ) ?: error("Could not save downloaded MMS")
            check(preferences.edit().putString("$token.inbox", uri.toString()).commit()) {
                "Could not save the downloaded MMS row"
            }
            context.contentResolver.update(uri, ContentValues().apply {
                put(Telephony.Mms.READ, 0)
                put(Telephony.Mms.SEEN, 0)
            }, null, null)
            uri
        }
        importer.importMms(inboxUri)
        context.contentResolver.delete(Uri.parse(pending.messageUri), null, null)
        clearPending(token, pending.fileName)
    }

    private fun findDownloadedMessage(pdu: RetrieveConf): Uri? {
        val messageId = pdu.messageId?.toString(Charsets.ISO_8859_1) ?: return null
        return context.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID),
            "${Telephony.Mms.MESSAGE_ID} = ? AND ${Telephony.Mms.MESSAGE_TYPE} = ? AND " +
                "${Telephony.Mms.MESSAGE_BOX} = ?",
            arrayOf(messageId, PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF.toString(),
                Telephony.Mms.MESSAGE_BOX_INBOX.toString()),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, cursor.getLong(0)) else null
        }
    }

    /** Save the small carrier send response before a callback receiver schedules asynchronous work. */
    fun captureSendResponse(intent: Intent) {
        val token = intent.getStringExtra(EXTRA_TOKEN) ?: return
        if (pending(token)?.kind != "send") return
        val response = intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA)
            ?.takeIf { it.isNotEmpty() && it.size <= MAX_SEND_RESPONSE_BYTES } ?: return
        check(preferences.edit().putString("$token.response", Base64.encodeToString(response, Base64.NO_WRAP))
            .commit()) { "Could not save MMS carrier response" }
    }

    private suspend fun markSentResult(
        uri: Uri, systemId: Long, token: String, success: Boolean, response: ByteArray? = null,
    ) {
        val confirmation = response?.let { bytes ->
            runCatching { PduParser(bytes).parse() as? SendConf }.getOrNull()
        }
        val accepted = success && (confirmation == null ||
            confirmation.responseStatus == PduHeaders.RESPONSE_STATUS_OK)
        context.contentResolver.update(uri, ContentValues().apply {
            put(Telephony.Mms.MESSAGE_BOX, if (accepted) Telephony.Mms.MESSAGE_BOX_SENT else Telephony.Mms.MESSAGE_BOX_FAILED)
            confirmation?.let { conf ->
                put(Telephony.Mms.RESPONSE_STATUS, conf.responseStatus)
                conf.messageId?.let { put(Telephony.Mms.MESSAGE_ID, it.toString(Charsets.ISO_8859_1)) }
            }
        }, null, null)
        // Import first so a callback following process death still has a Room target.
        importer.importMms(ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, systemId))
        processor.ingest(DeliveryUpdated(
            providerId = "sms",
            eventId = "mms-transfer:$token",
            occurredAt = System.currentTimeMillis(),
            providerMessageId = "mms:$systemId",
            status = if (accepted) DeliveryStatus.SENT else DeliveryStatus.FAILED,
        ))
    }

    private fun queueDownload(notification: NotificationInd, subscriptionId: Int) {
        val rawLocation = notification.contentLocation?.toString(Charsets.ISO_8859_1)
            ?.takeIf { it.isNotBlank() } ?: return
        val appendTransactionId = managerFor(subscriptionId).carrierConfigValues
            ?.getBoolean(SmsManager.MMS_CONFIG_APPEND_TRANSACTION_ID, false) == true
        val transactionId = notification.transactionId?.toString(Charsets.ISO_8859_1)
        val location = if (appendTransactionId && rawLocation.endsWith('=') && !transactionId.isNullOrEmpty()) {
            "$rawLocation$transactionId"
        } else rawLocation
        if (location != rawLocation) notification.setContentLocation(location.toByteArray(Charsets.ISO_8859_1))
        // The carrier can retransmit the same push. Keep one notification and one download.
        val existing = findNotification(location)
        val notificationUri = existing ?: PduPersister.getPduPersister(context).persist(
            notification, Telephony.Mms.Inbox.CONTENT_URI, false, true, null, subscriptionId,
        ) ?: return
        val key = "location:${digest(location.toByteArray(Charsets.UTF_8))}"
        if (preferences.getString(key, null) != null) return
        val token = UUID.randomUUID().toString()
        val fileName = "mms-download-$token.pdu"
        savePending(token, PendingTransfer("download", notificationUri.toString(), fileName,
            subscriptionId, key, location))
        try {
            managerFor(subscriptionId).downloadMultimediaMessage(
                context, location, fileUri(fileName), null, callback(token, 0),
            )
        } catch (error: Exception) {
            Log.e(TAG, "MMS download could not be started", error)
            markDownloadFailed(notificationUri)
            scheduleDownloadRetry(token, checkNotNull(pending(token)))
        }
    }

    private fun scheduleDownloadRetry(token: String, transfer: PendingTransfer) {
        if (transfer.attempt >= MAX_DOWNLOAD_RETRIES) {
            clearPending(token, transfer.fileName)
            return
        }
        val delay = RETRY_DELAY_MS * (1L shl (transfer.attempt * 2))
        val extras = PersistableBundle().apply {
            putString(EXTRA_TOKEN, token)
            putString(EXTRA_JOB_KIND, JOB_KIND_RETRY)
        }
        val request = JobInfo.Builder(
            (token.hashCode() xor RETRY_JOB_SALT) and Int.MAX_VALUE,
            ComponentName(context, MmsTransferJobService::class.java),
        ).setExtras(extras)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setMinimumLatency(delay)
            .setPersisted(true)
            .setBackoffCriteria(RETRY_DELAY_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()
        val scheduler = context.getSystemService(JobScheduler::class.java)
        check(scheduler?.schedule(request) == JobScheduler.RESULT_SUCCESS) {
            "Could not schedule MMS download retry"
        }
    }

    private suspend fun applyDeliveryReport(report: DeliveryInd, rawPdu: ByteArray) {
        val messageId = report.messageId?.toString(Charsets.ISO_8859_1) ?: return
        val uri = context.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID),
            "${Telephony.Mms.MESSAGE_ID} = ?",
            arrayOf(messageId),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, cursor.getLong(0)) else null
        } ?: return
        importer.importMms(uri)
        val status = when (report.status) {
            PduHeaders.STATUS_RETRIEVED, PduHeaders.STATUS_FORWARDED -> DeliveryStatus.DELIVERED
            PduHeaders.STATUS_EXPIRED, PduHeaders.STATUS_REJECTED, PduHeaders.STATUS_UNREACHABLE -> DeliveryStatus.FAILED
            else -> return
        }
        processor.ingest(DeliveryUpdated(
            providerId = "sms",
            eventId = "mms-report:${digest(rawPdu)}",
            occurredAt = System.currentTimeMillis(),
            providerMessageId = "mms:${ContentUris.parseId(uri)}",
            status = status,
        ))
    }

    private fun composeRequest(
        recipients: List<String>, body: String?, attachments: List<Uri>, subscriptionId: Int, manager: SmsManager,
    ): SendReq {
        val maxBytes = manager.carrierConfigValues?.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE, 1_048_576)
            ?.coerceIn(64 * 1024, 5 * 1024 * 1024) ?: 1_048_576
        var totalBytes = 0
        val pduBody = PduBody()
        fun addPart(name: String, mime: String, data: ByteArray) {
            totalBytes += data.size
            require(totalBytes < maxBytes - 8_192) { "MMS is larger than the carrier's size limit" }
            pduBody.addPart(PduPart().apply {
                setContentType(mime.toByteArray(Charsets.US_ASCII))
                setContentLocation(name.toByteArray(Charsets.US_ASCII))
                setContentId(name.substringBeforeLast('.').toByteArray(Charsets.US_ASCII))
                if (mime.startsWith("text/")) setCharset(CharacterSets.UTF_8)
                setData(data)
            })
        }
        body?.takeIf { it.isNotBlank() }?.let { addPart("text.txt", "text/plain", it.toByteArray(Charsets.UTF_8)) }
        attachments.forEachIndexed { index, uri ->
            val mime = context.contentResolver.getType(uri) ?: error("Unknown attachment type")
            val original = uri.lastPathSegment.orEmpty().substringAfterLast('/')
            val safe = original.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60).ifBlank { "attachment" }
            val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    require(totalBytes + output.size() < maxBytes - 8_192) { "MMS is larger than the carrier's size limit" }
                }
                output.toByteArray()
            } ?: error("Could not open MMS attachment")
            addPart("${index}_$safe", mime, bytes)
        }
        val smil = ByteArrayOutputStream().also { SmilXmlSerializer.serialize(SmilHelper.createSmilDocument(pduBody), it) }
        pduBody.addPart(0, PduPart().apply {
            setContentId("smil".toByteArray())
            setContentLocation("smil.xml".toByteArray())
            setContentType("application/smil".toByteArray())
            setData(smil.toByteArray())
        })
        return SendReq().apply {
            recipients.forEach { addTo(EncodedStringValue(it)) }
            try { prepareFromAddress(context, "", subscriptionId) } catch (_: SecurityException) { /* insert-address-token remains valid */ }
            setDate(System.currentTimeMillis() / 1_000)
            setBody(pduBody)
            setMessageSize(totalBytes.toLong())
            setMessageClass(PduHeaders.MESSAGE_CLASS_PERSONAL_STR.toByteArray())
            setExpiry(7 * 24 * 60 * 60L)
            setDeliveryReport(PduHeaders.VALUE_YES)
        }
    }

    @Suppress("DEPRECATION")
    private fun managerFor(subscriptionId: Int): SmsManager = when {
        !SubscriptionManager.isValidSubscriptionId(subscriptionId) -> SmsManager.getDefault()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
        else -> SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
    }

    private fun fileUri(fileName: String): Uri = Uri.Builder()
        .scheme("content").authority("${context.packageName}.MmsFileProvider").appendPath(fileName).build()

    private fun callback(token: String, attempt: Int = 0, mutable: Boolean = false): PendingIntent = PendingIntent.getBroadcast(
        context,
        (token.hashCode() xor attempt) and Int.MAX_VALUE,
        Intent(context, MmsTransferReceiver::class.java)
            .setAction("${context.packageName}.MMS_TRANSFER.$token.$attempt")
            .putExtra(EXTRA_TOKEN, token)
            .putExtra(EXTRA_ATTEMPT, attempt),
        PendingIntent.FLAG_UPDATE_CURRENT or
            (if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE),
    )

    private fun findNotification(location: String): Uri? = context.contentResolver.query(
        Telephony.Mms.CONTENT_URI,
        arrayOf(Telephony.Mms._ID),
        "${Telephony.Mms.MESSAGE_TYPE} = ? AND ${Telephony.Mms.CONTENT_LOCATION} = ?",
        arrayOf(PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND.toString(), location),
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, cursor.getLong(0)) else null
    }

    private fun markDownloadFailed(uri: Uri) {
        context.contentResolver.update(uri, ContentValues().apply {
            put(Telephony.Mms.RETRIEVE_STATUS, PduHeaders.RETRIEVE_STATUS_ERROR_TRANSIENT_FAILURE)
        }, null, null)
    }

    private fun savePending(token: String, transfer: PendingTransfer) {
        check(preferences.edit()
            .putString("$token.kind", transfer.kind)
            .putString("$token.uri", transfer.messageUri)
            .putString("$token.file", transfer.fileName)
            .putInt("$token.sub", transfer.subscriptionId)
            .putString("$token.location", transfer.locationKey)
            .putString("$token.url", transfer.location)
            .putInt("$token.attempt", transfer.attempt)
            .also { editor -> transfer.locationKey?.let { editor.putString(it, token) } }
            .commit()) { "Could not persist MMS transfer before sending" }
    }

    private fun pending(token: String): PendingTransfer? {
        val kind = preferences.getString("$token.kind", null) ?: return null
        return PendingTransfer(
            kind,
            preferences.getString("$token.uri", null) ?: return null,
            preferences.getString("$token.file", null) ?: return null,
            preferences.getInt("$token.sub", SmsManager.getDefaultSmsSubscriptionId()),
            preferences.getString("$token.location", null),
            preferences.getString("$token.url", null),
            preferences.getInt("$token.attempt", 0),
        )
    }

    private fun clearPending(token: String, fileName: String) {
        File(context.cacheDir, fileName).delete()
        val locationKey = preferences.getString("$token.location", null)
        val editor = preferences.edit().remove("$token.kind").remove("$token.uri")
            .remove("$token.file").remove("$token.sub").remove("$token.location")
            .remove("$token.url").remove("$token.attempt").remove("$token.response")
            .remove("$token.inbox")
        if (locationKey != null && preferences.getString(locationKey, null) == token) editor.remove(locationKey)
        editor.commit()
    }

    private fun isDefaultSmsApp(): Boolean = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private data class PendingTransfer(
        val kind: String,
        val messageUri: String,
        val fileName: String,
        val subscriptionId: Int,
        val locationKey: String? = null,
        val location: String? = null,
        val attempt: Int = 0,
    )

    companion object {
        private const val TAG = "MmsTransport"
        private const val MAX_SEND_RESPONSE_BYTES = 4096
        private const val MAX_DOWNLOAD_RETRIES = 3
        private const val RETRY_DELAY_MS = 30_000L
        private const val RETRY_JOB_SALT = 0x4d4d5352
        const val EXTRA_TOKEN = "com.unifiedmessages.app.sms.mms.transfer_token"
        const val EXTRA_ATTEMPT = "com.unifiedmessages.app.sms.mms.transfer_attempt"
        const val EXTRA_JOB_KIND = "com.unifiedmessages.app.sms.mms.job_kind"
        const val JOB_KIND_RETRY = "retry_download"
    }
}

package com.unifiedmessages.app.sms

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.unifiedmessages.app.data.local.ContentType
import com.unifiedmessages.app.data.local.DeliveryStatus
import com.unifiedmessages.app.provider.MessageReceived
import com.unifiedmessages.app.provider.MessageSent
import com.unifiedmessages.app.provider.ProviderAttachment
import com.unifiedmessages.app.provider.ProviderMessage
import com.unifiedmessages.app.provider.ProviderParticipant
import com.unifiedmessages.app.provider.ProviderThread
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads messages already stored by Android's Telephony provider. It never writes to that provider.
 * The event processor deduplicates repeated scans and applies read/delivery changes to Room.
 */
@Singleton
class TelephonyImporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val processor: SmsEventProcessor,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    suspend fun importAll() = withContext(Dispatchers.IO) {
        readAllInTimestampOrder()
    }

    suspend fun importSms(uri: Uri) = withContext(Dispatchers.IO) {
        readSms(uri)
    }

    suspend fun importMms(uri: Uri) = withContext(Dispatchers.IO) {
        readMms(uri)
    }

    private suspend fun readAllInTimestampOrder() {
        val sms = resolver.query(Telephony.Sms.CONTENT_URI, SMS_COLUMNS, null, null, SMS_ORDER)
        try {
            val mms = resolver.query(Telephony.Mms.CONTENT_URI, MMS_COLUMNS, null, null, MMS_ORDER)
            try {
                var hasSms = sms?.moveToFirst() == true
                var hasMms = mms?.moveToFirst() == true
                while (hasSms || hasMms) {
                    val nextIsSms = hasSms && (!hasMms ||
                        (sms?.long(Telephony.Sms.DATE) ?: 0L) <=
                        (mms?.long(Telephony.Mms.DATE) ?: 0L) * 1_000L)
                    if (nextIsSms) {
                        val cursor = checkNotNull(sms)
                        importSmsRow(cursor)
                        hasSms = cursor.moveToNext()
                    } else {
                        val cursor = checkNotNull(mms)
                        importMmsRow(cursor)
                        hasMms = cursor.moveToNext()
                    }
                }
            } finally {
                mms?.close()
            }
        } finally {
            sms?.close()
        }
    }

    private suspend fun readSms(uri: Uri) {
        resolver.query(uri, SMS_COLUMNS, null, null, SMS_ORDER)
            ?.use { cursor ->
                while (cursor.moveToNext()) importSmsRow(cursor)
            }
    }

    private suspend fun importSmsRow(row: Cursor) {
        val rowId = row.long(Telephony.Sms._ID) ?: return
        val threadId = row.long(Telephony.Sms.THREAD_ID)?.takeIf { it > 0L } ?: return
        val type = row.int(Telephony.Sms.TYPE) ?: return
        if (type != Telephony.Sms.MESSAGE_TYPE_INBOX && type != Telephony.Sms.MESSAGE_TYPE_SENT &&
            type != Telephony.Sms.MESSAGE_TYPE_OUTBOX && type != Telephony.Sms.MESSAGE_TYPE_QUEUED &&
            type != Telephony.Sms.MESSAGE_TYPE_FAILED
        ) return

        val address = row.string(Telephony.Sms.ADDRESS)?.takeIf(String::isNotBlank)
        val body = row.string(Telephony.Sms.BODY)
        val timestamp = row.long(Telephony.Sms.DATE) ?: 0L
        val read = row.int(Telephony.Sms.READ) == 1
        val systemStatus = row.int(Telephony.Sms.STATUS) ?: Telephony.Sms.STATUS_NONE
        val outgoing = type != Telephony.Sms.MESSAGE_TYPE_INBOX
        val messageId = "sms:$rowId"
        val providerThread = ProviderThread(
            providerThreadId = threadId.toString(),
            source = "SMS",
            title = address ?: "Unknown",
            participants = address?.let { listOf(ProviderParticipant(it)) }.orEmpty(),
        )
        val providerMessage = ProviderMessage(
            providerMessageId = messageId,
            body = body,
            timestamp = timestamp,
            senderAddress = if (outgoing) null else address,
            isRead = read,
            deliveryStatus = smsDelivery(type, systemStatus),
            subscriptionId = row.int(Telephony.Sms.SUBSCRIPTION_ID),
            systemUri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, rowId).toString(),
            subject = row.string(Telephony.Sms.SUBJECT),
        )
        // A row can later become read or acquire a delivery report. Those states need new events.
        val eventId = "import:$messageId:$type:${if (read) 1 else 0}:$systemStatus"
        if (outgoing) {
            processor.ingest(MessageSent(PROVIDER_ID, eventId, timestamp, providerThread, providerMessage))
        } else {
            processor.ingest(MessageReceived(PROVIDER_ID, eventId, timestamp, providerThread, providerMessage))
        }
    }

    private suspend fun readMms(uri: Uri) {
        resolver.query(uri, MMS_COLUMNS, null, null, MMS_ORDER)
            ?.use { cursor ->
                while (cursor.moveToNext()) importMmsRow(cursor)
            }
    }

    private suspend fun importMmsRow(row: Cursor) {
        val rowId = row.long(Telephony.Mms._ID) ?: return
        val threadId = row.long(Telephony.Mms.THREAD_ID)?.takeIf { it > 0L } ?: return
        val box = row.int(Telephony.Mms.MESSAGE_BOX) ?: return
        val pduType = row.int(Telephony.Mms.MESSAGE_TYPE) ?: return
        if (box != Telephony.Mms.MESSAGE_BOX_INBOX && box != Telephony.Mms.MESSAGE_BOX_SENT &&
            box != Telephony.Mms.MESSAGE_BOX_OUTBOX && box != Telephony.Mms.MESSAGE_BOX_FAILED
        ) return
        // Notification, acknowledgement and delivery-report PDUs are transport metadata.
        if (pduType != MMS_SEND_REQUEST && pduType != MMS_RETRIEVE_CONFIRMATION) return

        val outgoing = box != Telephony.Mms.MESSAGE_BOX_INBOX
        val addresses = readMmsAddresses(rowId)
        val subscriptionId = row.int(Telephony.Mms.SUBSCRIPTION_ID)
        val ownNumber = if (outgoing) null else localPhoneNumber(subscriptionId)
        val sender = addresses.firstOrNull { it.type == MMS_FROM }?.address
        val remoteMmsAddresses = if (outgoing) {
            addresses.filter { it.type == MMS_TO || it.type == MMS_CC }
        } else if (ownNumber == null) {
            // A received MMS normally includes our own number in TO. Without a reliable local
            // number, only FROM is safe as a reply recipient.
            addresses.filter { it.type == MMS_FROM }
        } else {
            addresses.filter { it.type == MMS_FROM || it.type == MMS_CC || it.type == MMS_TO }
                .filterNot { isOwnAddress(it.address, ownNumber) }
        }
        val remoteAddresses = remoteMmsAddresses.map { it.address }.distinct()
        val selfParticipants = if (ownNumber == null) emptyList() else addresses
            .filter { isOwnAddress(it.address, ownNumber) }
            .map { it.address }.distinct().map { ProviderParticipant(it, isSelf = true) }
        val parts = readMmsParts(rowId)
        val body = parts.text.joinToString("\n").ifBlank { null }
        val attachments = parts.attachments
        val timestamp = (row.long(Telephony.Mms.DATE) ?: 0L) * 1_000L
        val read = row.int(Telephony.Mms.READ) == 1
        val systemStatus = row.int(Telephony.Mms.STATUS) ?: 0
        val messageId = "mms:$rowId"
        val providerThread = ProviderThread(
            providerThreadId = threadId.toString(),
            source = "MMS",
            title = remoteAddresses.joinToString(", ").ifBlank { "Unknown" },
            participants = remoteAddresses.map { ProviderParticipant(it) } + selfParticipants,
        )
        val providerMessage = ProviderMessage(
            providerMessageId = messageId,
            body = body,
            contentType = if (attachments.isEmpty() && body != null) ContentType.TEXT else ContentType.MEDIA,
            timestamp = timestamp,
            senderAddress = if (outgoing) null else sender,
            isRead = read,
            deliveryStatus = mmsDelivery(box),
            attachments = attachments,
            subscriptionId = subscriptionId,
            systemUri = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, rowId).toString(),
            subject = row.string(Telephony.Mms.SUBJECT)?.takeUnless { it.equals("null", ignoreCase = true) },
        )
        // A late part or status update must be processed even if the PDU row ID is unchanged.
        val partIds = parts.ids.joinToString(",")
        val eventId = "import:$messageId:$box:${if (read) 1 else 0}:$systemStatus:$partIds:" +
            if (ownNumber == null) "no-local-number" else "local-number-known"
        if (outgoing) {
            processor.ingest(MessageSent(PROVIDER_ID, eventId, timestamp, providerThread, providerMessage))
        } else {
            processor.ingest(MessageReceived(PROVIDER_ID, eventId, timestamp, providerThread, providerMessage))
        }
    }

    private fun readMmsAddresses(mmsId: Long): List<MmsAddress> {
        val addresses = mutableListOf<MmsAddress>()
        resolver.query(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Telephony.Mms.Addr.getAddrUriForMessage(mmsId.toString())
            } else {
                Telephony.Mms.CONTENT_URI.buildUpon().appendPath(mmsId.toString()).appendPath("addr").build()
            },
            arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val address = cursor.string(Telephony.Mms.Addr.ADDRESS)
                    ?.takeIf { it.isNotBlank() && !it.equals("insert-address-token", ignoreCase = true) }
                    ?: continue
                addresses += MmsAddress(address, cursor.int(Telephony.Mms.Addr.TYPE) ?: 0)
            }
        }
        return addresses
    }

    @Suppress("DEPRECATION")
    private fun isOwnAddress(address: String, ownNumber: String): Boolean =
        PhoneNumberUtils.compare(context, address, ownNumber)

    @Suppress("DEPRECATION")
    private fun localPhoneNumber(subscriptionId: Int?): String? {
        val id = subscriptionId?.takeIf(SubscriptionManager::isValidSubscriptionId)
            ?: SmsManager.getDefaultSmsSubscriptionId()
        if (!SubscriptionManager.isValidSubscriptionId(id)) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) !=
            PackageManager.PERMISSION_GRANTED
        ) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(SubscriptionManager::class.java)?.getPhoneNumber(id)
            } else {
                context.getSystemService(TelephonyManager::class.java)
                    ?.createForSubscriptionId(id)?.line1Number
            }?.takeIf(String::isNotBlank)
        } catch (_: SecurityException) {
            null
        }
    }

    private fun readMmsParts(mmsId: Long): MmsParts {
        val text = mutableListOf<String>()
        val attachments = mutableListOf<ProviderAttachment>()
        val ids = mutableListOf<Long>()
        resolver.query(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Telephony.Mms.Part.getPartUriForMessage(mmsId.toString())
            } else {
                Telephony.Mms.CONTENT_URI.buildUpon().appendPath(mmsId.toString()).appendPath("part").build()
            },
            MMS_PART_COLUMNS,
            null,
            null,
            "${Telephony.Mms.Part.SEQ} ASC, ${Telephony.Mms.Part._ID} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val partId = cursor.long(Telephony.Mms.Part._ID) ?: continue
                val mimeType = cursor.string(Telephony.Mms.Part.CONTENT_TYPE)
                    ?.substringBefore(';')?.trim() ?: "application/octet-stream"
                ids += partId
                if (mimeType.equals("application/smil", ignoreCase = true)) continue
                val partUri = ContentUris.withAppendedId(Telephony.Mms.Part.CONTENT_URI, partId)
                if (mimeType.equals("text/plain", ignoreCase = true)) {
                    val value = cursor.string(Telephony.Mms.Part.TEXT)?.takeIf(String::isNotBlank)
                        ?: readTextPart(partUri)
                    value?.takeIf(String::isNotBlank)?.let(text::add)
                } else {
                    attachments += ProviderAttachment(
                        providerAttachmentId = "part:$partId",
                        contentUri = partUri.toString(),
                        mimeType = mimeType,
                        fileName = cursor.string(Telephony.Mms.Part.FILENAME)
                            ?: cursor.string(Telephony.Mms.Part.NAME),
                    )
                }
            }
        }
        return MmsParts(text, attachments, ids)
    }

    private fun readTextPart(uri: Uri): String? = try {
        resolver.openInputStream(uri)?.reader(Charsets.UTF_8)?.use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(4_096)
            while (result.length < MAX_TEXT_PART_CHARS) {
                val read = reader.read(buffer, 0, minOf(buffer.size, MAX_TEXT_PART_CHARS - result.length))
                if (read <= 0) break
                result.append(buffer, 0, read)
            }
            result.toString()
        }
    } catch (_: Exception) {
        // A missing part must not prevent the rest of the conversation from importing.
        null
    }

    private data class MmsAddress(val address: String, val type: Int)
    private data class MmsParts(
        val text: List<String>,
        val attachments: List<ProviderAttachment>,
        val ids: List<Long>,
    )

    private companion object {
        const val PROVIDER_ID = "sms"
        const val MAX_TEXT_PART_CHARS = 1_000_000
        const val MMS_SEND_REQUEST = 128
        const val MMS_RETRIEVE_CONFIRMATION = 132
        // MMS address types from the WAP PDU headers (not exposed as Telephony constants).
        const val MMS_CC = 130
        const val MMS_FROM = 137
        const val MMS_TO = 151

        val SMS_ORDER = "${Telephony.Sms.DATE} ASC, ${Telephony.Sms._ID} ASC"
        val MMS_ORDER = "${Telephony.Mms.DATE} ASC, ${Telephony.Mms._ID} ASC"

        val SMS_COLUMNS = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            Telephony.Sms.STATUS,
            Telephony.Sms.SUBSCRIPTION_ID,
            Telephony.Sms.SUBJECT,
        )
        val MMS_COLUMNS = arrayOf(
            Telephony.Mms._ID,
            Telephony.Mms.THREAD_ID,
            Telephony.Mms.DATE,
            Telephony.Mms.MESSAGE_BOX,
            Telephony.Mms.MESSAGE_TYPE,
            Telephony.Mms.READ,
            Telephony.Mms.STATUS,
            Telephony.Mms.SUBSCRIPTION_ID,
            Telephony.Mms.SUBJECT,
        )
        val MMS_PART_COLUMNS = arrayOf(
            Telephony.Mms.Part._ID,
            Telephony.Mms.Part.CONTENT_TYPE,
            Telephony.Mms.Part.TEXT,
            Telephony.Mms.Part.FILENAME,
            Telephony.Mms.Part.NAME,
            Telephony.Mms.Part.SEQ,
        )
    }
}

private fun Cursor.string(column: String): String? =
    getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let { getString(it) }

private fun Cursor.long(column: String): Long? =
    getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let { getLong(it) }

private fun Cursor.int(column: String): Int? =
    getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let { getInt(it) }

private fun smsDelivery(type: Int, status: Int): DeliveryStatus = when {
    type == Telephony.Sms.MESSAGE_TYPE_INBOX -> DeliveryStatus.RECEIVED
    type == Telephony.Sms.MESSAGE_TYPE_FAILED || status == Telephony.Sms.STATUS_FAILED -> DeliveryStatus.FAILED
    status == Telephony.Sms.STATUS_COMPLETE -> DeliveryStatus.DELIVERED
    type == Telephony.Sms.MESSAGE_TYPE_SENT -> DeliveryStatus.SENT
    else -> DeliveryStatus.PENDING
}

private fun mmsDelivery(box: Int): DeliveryStatus = when (box) {
    Telephony.Mms.MESSAGE_BOX_INBOX -> DeliveryStatus.RECEIVED
    Telephony.Mms.MESSAGE_BOX_SENT -> DeliveryStatus.SENT
    Telephony.Mms.MESSAGE_BOX_FAILED -> DeliveryStatus.FAILED
    else -> DeliveryStatus.PENDING
}

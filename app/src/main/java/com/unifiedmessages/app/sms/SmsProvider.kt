package com.unifiedmessages.app.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.unifiedmessages.app.data.local.ContentType
import com.unifiedmessages.app.data.local.DeliveryStatus
import com.unifiedmessages.app.data.local.MessageDirection
import com.unifiedmessages.app.data.local.ParticipantEntity
import com.unifiedmessages.app.data.local.ProviderIds
import com.unifiedmessages.app.data.local.ThreadEntity
import com.unifiedmessages.app.data.local.UnifiedMessagesDatabase
import com.unifiedmessages.app.provider.Capabilities
import com.unifiedmessages.app.provider.MessageEvent
import com.unifiedmessages.app.provider.MessageProvider
import com.unifiedmessages.app.sms.mms.MmsTransport
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Android's default SMS app role is required before this transport can send or import messages. */
@Singleton
class SmsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: UnifiedMessagesDatabase,
    private val processor: SmsEventProcessor,
    private val importer: TelephonyImporter,
    private val smsTransport: SmsTransport,
    private val mmsTransport: MmsTransport,
) : MessageProvider {
    override val id: String = "sms"
    override val capabilities = Capabilities(
        supportsReactions = false, // Quoted SMS fallbacks are parsed, not native reactions.
        supportsEdit = false,
        supportsUnsend = false,
        supportsReadReceipts = false,
        supportsAppMessages = false,
    )
    override val events: Flow<MessageEvent> = processor.events

    fun isDefaultSmsApp(): Boolean = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    fun hasSmsPermissions(): Boolean = listOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.RECEIVE_MMS,
        Manifest.permission.RECEIVE_WAP_PUSH,
    ).all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    fun isReady(): Boolean = isDefaultSmsApp() && hasSmsPermissions()

    suspend fun importExisting() {
        check(isReady()) { "Set UnifiedMessages as the default SMS app and grant SMS permissions first." }
        importer.importAll()
    }

    suspend fun startThread(address: String): String =
        startThread(listOf(address), PhoneNumbers.deviceRegion(context))

    /** Reuses an exact participant set or creates a matching Android and Room thread. */
    suspend fun startThread(recipients: List<String>, region: String): String = withContext(Dispatchers.IO) {
        check(isReady()) { "SMS access is not ready." }
        val dao = database.threadDao()
        val normalized = ThreadRecipients.requireNormalized(recipients, region)
        // Android's canonical recipient set is authoritative. An imported group MMS may have
        // only its sender in Room when the SIM's own number could not be identified.
        val systemThreadId = Telephony.Threads.getOrCreateThreadId(context, normalized).toString()
        val threadId = ProviderIds.thread(id, systemThreadId)
        val candidates = dao.observeThreads(source = "SMS").first()
            .filter { it.thread.providerId == id }
            .map { summary ->
                ThreadRecipients.Candidate(
                    summary.thread.id,
                    dao.getParticipants(summary.thread.id).map { it.address },
                )
            }
        val resolution = ThreadRecipients.resolve(normalized, region, candidates)
        if (resolution.existingThreadId == threadId) return@withContext threadId
        val ordered = normalized.sorted()
        if (dao.getThread(threadId) == null) {
            dao.insert(
                ThreadEntity(
                    id = threadId,
                    providerId = this@SmsProvider.id,
                    providerThreadId = systemThreadId,
                    source = if (ordered.size > 1) "MMS" else "SMS",
                    title = ordered.joinToString(", "),
                    avatarText = ordered.first().takeLast(2),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
        ordered.forEach { address ->
            dao.insertParticipant(
                ParticipantEntity(
                    id = ProviderIds.participant(this@SmsProvider.id, systemThreadId, address),
                    threadId = threadId,
                    address = address,
                ),
            )
        }
        threadId
    }

    suspend fun sendText(threadId: String, text: String) {
        check(isReady()) { "SMS access is not ready." }
        require(text.isNotBlank()) { "Message is empty." }
        smsTransport.send(threadId, text)
    }

    suspend fun sendThreadText(threadId: String, text: String) {
        database.threadDao().getThread(threadId) ?: error("Conversation no longer exists.")
        val recipients = ThreadRecipients.requireSendableExisting(
            database.threadDao().getParticipants(threadId).map { it.address },
            PhoneNumbers.deviceRegion(context),
        )
        if (recipients.size > 1) {
            sendMms(threadId, text, emptyList())
        } else {
            sendText(threadId, text)
        }
    }

    suspend fun sendMms(threadId: String, text: String?, attachments: List<Uri>) {
        check(isReady()) { "SMS access is not ready." }
        require(!text.isNullOrBlank() || attachments.isNotEmpty()) { "Message is empty." }
        mmsTransport.send(threadId, text, attachments)
    }

    /** A retry creates a new carrier attempt while retaining the failed message for history. */
    suspend fun retryFailedMessage(messageId: String) {
        check(isReady()) { "SMS access is not ready." }
        val messageDao = database.messageDao()
        val message = messageDao.getMessage(messageId) ?: error("Message no longer exists.")
        require(message.direction == MessageDirection.OUTGOING &&
            message.deliveryStatus == DeliveryStatus.FAILED && !message.isDeleted) {
            "Only failed outgoing messages can be retried."
        }
        val thread = database.threadDao().getThread(message.threadId) ?: error("Conversation no longer exists.")
        require(thread.providerId == id) { "This is not an SMS/MMS conversation." }

        if (message.providerMessageId.startsWith("mms:")) {
            val savedAttachments = messageDao.getAttachments(message.id)
            val attachmentUris = savedAttachments.map { attachment ->
                val uri = attachment.contentUri?.takeIf(String::isNotBlank)
                    ?: error("A saved MMS attachment is unavailable for retry.")
                Uri.parse(uri)
            }
            require(message.contentType != ContentType.MEDIA || attachmentUris.isNotEmpty()) {
                "The failed MMS has no saved media to retry."
            }
            mmsTransport.send(thread.id, message.body, attachmentUris)
        } else {
            require(message.contentType == ContentType.TEXT) { "The failed SMS cannot be retried as text." }
            smsTransport.send(thread.id, message.body?.takeIf(String::isNotBlank)
                ?: error("The failed SMS has no text to retry."))
        }
    }
}

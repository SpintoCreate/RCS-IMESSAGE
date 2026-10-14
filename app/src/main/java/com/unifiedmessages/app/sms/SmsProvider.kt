package com.unifiedmessages.app.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat
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

    /** Creates a system thread and a matching empty Room thread so the existing composer can open. */
    suspend fun startThread(address: String): String = withContext(Dispatchers.IO) {
        check(isReady()) { "SMS access is not ready." }
        val normalized = PhoneNumberUtils.stripSeparators(address.trim())
        require(normalized.isNotBlank()) { "Enter a phone number." }
        val systemThreadId = Telephony.Threads.getOrCreateThreadId(context, normalized).toString()
        val id = ProviderIds.thread(this@SmsProvider.id, systemThreadId)
        val dao = database.threadDao()
        if (dao.getThread(id) == null) {
            dao.insert(
                ThreadEntity(
                    id = id,
                    providerId = this@SmsProvider.id,
                    providerThreadId = systemThreadId,
                    source = "SMS",
                    title = normalized,
                    avatarText = normalized.takeLast(2),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
        dao.insertParticipant(
            ParticipantEntity(
                id = ProviderIds.participant(this@SmsProvider.id, systemThreadId, normalized),
                threadId = id,
                address = normalized,
            ),
        )
        id
    }

    suspend fun sendText(threadId: String, text: String) {
        check(isReady()) { "SMS access is not ready." }
        require(text.isNotBlank()) { "Message is empty." }
        smsTransport.send(threadId, text)
    }

    suspend fun sendThreadText(threadId: String, text: String) {
        database.threadDao().getThread(threadId) ?: error("Conversation no longer exists.")
        val recipients = database.threadDao().getParticipants(threadId)
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
}

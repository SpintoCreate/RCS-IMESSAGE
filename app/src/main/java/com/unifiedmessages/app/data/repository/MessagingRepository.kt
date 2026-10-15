package com.unifiedmessages.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.unifiedmessages.app.data.local.ContentType
import com.unifiedmessages.app.data.local.AttachmentEntity
import com.unifiedmessages.app.data.local.DeliveryStatus
import com.unifiedmessages.app.data.local.MessageDirection
import com.unifiedmessages.app.data.local.MessageEntity
import com.unifiedmessages.app.data.local.ThreadEntity
import com.unifiedmessages.app.data.local.ThreadSummary
import com.unifiedmessages.app.data.local.UnifiedMessagesDatabase
import com.unifiedmessages.app.model.ChatMessage
import com.unifiedmessages.app.model.ChatAttachment
import com.unifiedmessages.app.model.Conversation
import com.unifiedmessages.app.model.MessageSource
import com.unifiedmessages.app.provider.MessageRoute
import com.unifiedmessages.app.provider.NetworkType
import com.unifiedmessages.app.matrix.MatrixProvider
import com.unifiedmessages.app.sms.PhoneNumbers
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Room is the sole source of chat data; no fictional conversations are inserted at runtime. */
@Singleton
class MessagingRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: UnifiedMessagesDatabase,
    private val deletedMessagePreferences: DeletedMessagePreferences,
    private val contactsRepository: ContactsRepository,
    private val matrixProvider: MatrixProvider,
) {
    private val deviceRegion = PhoneNumbers.deviceRegion(context)

    fun observeAvailableSources(): Flow<List<MessageSource>> =
        database.threadDao().observeNetworkTypes().map { types ->
            (listOf(MessageSource.SMS) + types.map { it.toDisplaySource() })
                .filter { it != MessageSource.OTHER }.distinct()
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeInbox(query: String, source: MessageSource?): Flow<List<Conversation>> {
        val search = query.trim()
        val numberTerms = SearchMatching.numberTerms(search)
        return combine(
            deletedMessagePreferences.showSenderDeletedMessages,
            database.threadDao().observeRemoteParticipants(),
            contactsRepository.contacts,
        ) { showSenderDeleted, participants, contacts ->
            Triple(showSenderDeleted, participants, contacts)
        }.flatMapLatest { (showSenderDeleted, participants, contacts) ->
            val matchingContactThreadIds = SearchMatching.matchingContactThreadIds(
                search, deviceRegion, participants, contacts,
            )
            database.threadDao().observeThreads(
                query = search,
                source = source?.name,
                showSenderDeleted = showSenderDeleted,
                digits = numberTerms.digits,
                digitsWithoutUsPrefix = numberTerms.digitsWithoutUsPrefix,
                matchingContactThreadIds = matchingContactThreadIds,
            ).map { rows ->
                val recipientsByThread = participants.groupBy { it.threadId }
                val contactsByNumber = contacts.associateBy { it.normalizedNumber }
                rows.map { row ->
                    row.toInboxConversation(
                        showSenderDeleted,
                        recipientsByThread[row.thread.id].orEmpty().map { it.address },
                        search,
                    ).withContacts(contactsByNumber)
                }
            }
        }
    }

    fun observeConversation(threadId: String, query: String = ""): Flow<Conversation?> =
        combine(
            database.threadDao().observeThread(threadId),
            database.messageDao().observeMessagesMatching(threadId, query.trim()),
            database.messageDao().observeAttachments(threadId),
            database.threadDao().observeParticipants(threadId),
            deletedMessagePreferences.showSenderDeletedMessages,
        ) { thread, messages, attachments, participants, showSenderDeleted ->
            thread?.toConversation(messages, attachments, participants.map { it.address }, showSenderDeleted)
        }.combine(database.bridgeRoomDao().observe(threadId)) { conversation, bridge ->
            conversation?.copy(bridgeDisclosure = bridge?.let {
                "This chat uses a ${it.protocolId} bridge. The bridge can read messages; end-to-end encryption does not continue past it."
            })
        }.combine(observeActiveTyping(threadId)) { conversation, typing ->
            conversation?.copy(typingUsers = typing)
        }.combine(contactsRepository.contacts) { conversation, contacts ->
            conversation?.withContacts(contacts.associateBy { it.normalizedNumber })
        }

    private fun observeActiveTyping(threadId: String): Flow<List<String>> = combine(
        database.typingStateDao().observe(threadId),
        flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(5_000)
            }
        },
    ) { states, now ->
        states.filter { it.isTyping && it.updatedAt >= now - 15_000L }.map { it.actorAddress }
    }

    fun observeUnreadCount(threadId: String): Flow<Int> =
        database.threadDao().observeUnreadCount(threadId)

    suspend fun markRead(threadId: String) = withContext(Dispatchers.IO) {
        val providerId = database.threadDao().getThread(threadId)?.providerId
        if (providerId == "sms") {
            val readValues = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }
            database.messageDao().getUnreadSystemUris(threadId).forEach { uri ->
                try {
                    context.contentResolver.update(Uri.parse(uri), readValues, null, null)
                } catch (_: SecurityException) {
                    // The default SMS role may have been removed while the chat was open.
                }
            }
        }
        database.threadDao().markRead(threadId)
        if (providerId == "matrix") {
            try { matrixProvider.markRead(threadId) }
            catch (_: Exception) { /* Local read state remains usable while offline. */ }
        }
    }

    suspend fun setPinned(threadId: String, pinned: Boolean) =
        database.threadDao().setPinned(threadId, pinned)

    private fun Conversation.withContacts(contactsByNumber: Map<String, CachedContact>): Conversation {
        if (recipients.isEmpty()) return this
        val matches = recipients.map { address ->
            PhoneNumbers.normalizeToE164(address, deviceRegion)?.let(contactsByNumber::get)
        }
        if (matches.none { it != null }) return this
        if (recipients.size == 1) {
            val contact = matches.firstOrNull() ?: return this
            return copy(
                title = contact.displayName,
                avatarText = initialsFor(contact.displayName),
                photoUri = contact.photoUri,
            )
        }
        val names = recipients.mapIndexed { index, address -> matches[index]?.displayName ?: address }
        return copy(
            title = names.joinToString(", "),
            avatarText = names.take(2).joinToString("") { initialsFor(it).take(1) },
        )
    }

    private fun ThreadSummary.toInboxConversation(
        showSenderDeleted: Boolean,
        recipients: List<String>,
        search: String,
    ): Conversation =
        Conversation(
            id = thread.id,
            route = thread.toMessageRoute(),
            title = thread.title,
            avatarText = thread.avatarText,
            source = (thread.networkType ?: thread.source).toDisplaySource(),
            timestamp = formatThreadTimestamp(lastTimestamp ?: thread.updatedAt),
            unreadCount = unreadCount,
            isPinned = thread.isPinned,
            messages = emptyList(),
            recipients = recipients,
            previewText = matchingBody?.let { SearchMatching.messageSnippet(it, search) } ?: if (lastIsDeleted) {
                if (lastDeletedBySender && showSenderDeleted) "Deleted by sender" else ""
            } else {
                displayBody(lastBody, lastContentType, lastSubject)
            },
        )

    private fun ThreadEntity.toConversation(
        messages: List<MessageEntity>,
        attachments: List<AttachmentEntity>,
        recipients: List<String>,
        showSenderDeleted: Boolean,
    ): Conversation {
        val visible = messages.filter { !it.isDeleted || it.deletedBySender && showSenderDeleted }
        val attachmentsByMessage = attachments.groupBy { it.messageId }
        return Conversation(
            id = id,
            route = toMessageRoute(),
            title = title,
            avatarText = avatarText,
            source = (networkType ?: source).toDisplaySource(),
            timestamp = formatThreadTimestamp(visible.lastOrNull()?.timestamp ?: updatedAt),
            unreadCount = messages.count { it.direction == MessageDirection.INCOMING && !it.isRead && !it.isDeleted },
            isPinned = isPinned,
            messages = visible.map { message ->
                val messageAttachments = if (message.isDeleted) emptyList() else attachmentsByMessage[message.id].orEmpty()
                val body = if (message.isDeleted) "Deleted by sender" else displayBody(
                    message.body,
                    message.contentType,
                    message.subject,
                )
                ChatMessage(
                    id = message.id,
                    providerMessageId = message.providerMessageId,
                    replyToProviderMessageId = message.replyToProviderMessageId,
                    text = if (messageAttachments.isEmpty()) body else {
                        val label = if (messageAttachments.size == 1) "Media attachment" else "${messageAttachments.size} media attachments"
                        if (body.isBlank() || body == "Media attachment") label else "$body\n$label"
                    },
                    time = formatMessageTimestamp(message.timestamp, message.deliveryStatus, message.direction),
                    isMine = message.direction == MessageDirection.OUTGOING,
                    attachments = messageAttachments.map { ChatAttachment(it.contentUri.orEmpty(), it.mimeType, it.fileName) },
                    deliveryStatus = message.deliveryStatus,
                )
            },
            recipients = recipients,
        )
    }
}

private fun ThreadEntity.toMessageRoute(): MessageRoute = MessageRoute(
    threadId = id,
    accountId = accountId ?: "$providerId:local",
    providerId = providerId,
    networkType = NetworkType.fromSource(networkType ?: source),
    remoteThreadId = providerThreadId,
)

private fun initialsFor(name: String): String = name.trim()
    .split(Regex("\\s+"))
    .filter(String::isNotBlank)
    .take(2)
    .joinToString("") { it.first().uppercase() }
    .ifBlank { "?" }

private fun String.toDisplaySource(): MessageSource = when (uppercase(Locale.ROOT)) {
    "IMESSAGE" -> MessageSource.IMESSAGE
    "RCS" -> MessageSource.RCS
    "SMS", "MMS" -> MessageSource.SMS
    "MATRIX" -> MessageSource.MATRIX
    "WHATSAPP" -> MessageSource.WHATSAPP
    "TELEGRAM" -> MessageSource.TELEGRAM
    "GOOGLE_MESSAGES" -> MessageSource.GOOGLE_MESSAGES
    else -> MessageSource.OTHER
}

private fun displayBody(body: String?, type: ContentType?, subject: String?): String {
    if (!body.isNullOrBlank()) return body
    if (!subject.isNullOrBlank()) return subject
    return when (type) {
        ContentType.MEDIA -> "Media attachment"
        ContentType.APP_MESSAGE -> "App message"
        ContentType.TEXT, null -> ""
    }
}

private fun formatThreadTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(timestamp).atZone(zone)
    val date = dateTime.toLocalDate()
    val today = LocalDate.now(zone)
    return when {
        date == today -> dateTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        date == today.minusDays(1) -> "Yesterday"
        date.isAfter(today.minusDays(7)) -> dateTime.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
        else -> dateTime.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    }
}

private fun formatMessageTimestamp(
    timestamp: Long,
    status: DeliveryStatus,
    direction: MessageDirection,
): String {
    if (timestamp <= 0L) return ""
    val dateTime = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
    val time = dateTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    val date = dateTime.toLocalDate()
    val today = LocalDate.now(ZoneId.systemDefault())
    val label = when {
        date == today -> time
        date == today.minusDays(1) -> "Yesterday · $time"
        else -> "${dateTime.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))} · $time"
    }
    val delivery = if (direction == MessageDirection.OUTGOING) when (status) {
        DeliveryStatus.PENDING -> "Sending"
        DeliveryStatus.SENT -> "Sent"
        DeliveryStatus.DELIVERED -> "Delivered"
        DeliveryStatus.FAILED -> "Failed"
        else -> null
    } else null
    return if (delivery == null) label else "$label · $delivery"
}

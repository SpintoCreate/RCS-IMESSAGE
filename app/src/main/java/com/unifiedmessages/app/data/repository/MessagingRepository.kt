package com.unifiedmessages.app.data.repository

import com.unifiedmessages.app.data.local.ContentType
import com.unifiedmessages.app.data.local.DeliveryStatus
import com.unifiedmessages.app.data.local.MessageDirection
import com.unifiedmessages.app.data.local.MessageEntity
import com.unifiedmessages.app.data.local.ThreadEntity
import com.unifiedmessages.app.data.local.ThreadSummary
import com.unifiedmessages.app.data.local.UnifiedMessagesDatabase
import com.unifiedmessages.app.model.ChatMessage
import com.unifiedmessages.app.model.Conversation
import com.unifiedmessages.app.model.MessageSource
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

/** Room is the sole source of chat data; no fictional conversations are inserted at runtime. */
@Singleton
class MessagingRepository @Inject constructor(
    private val database: UnifiedMessagesDatabase,
    private val deletedMessagePreferences: DeletedMessagePreferences,
) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeInbox(query: String, source: MessageSource?): Flow<List<Conversation>> =
        deletedMessagePreferences.showSenderDeletedMessages.flatMapLatest { showSenderDeleted ->
            database.threadDao().observeThreads(query.trim(), source?.name, showSenderDeleted)
                .map { rows -> rows.map { it.toInboxConversation(showSenderDeleted) } }
        }

    fun observeConversation(threadId: String, query: String = ""): Flow<Conversation?> = combine(
        database.threadDao().observeThread(threadId),
        database.messageDao().observeMessagesMatching(threadId, query.trim()),
        deletedMessagePreferences.showSenderDeletedMessages,
    ) { thread, messages, showSenderDeleted ->
        thread?.toConversation(messages, showSenderDeleted)
    }

    fun observeUnreadCount(threadId: String): Flow<Int> =
        database.threadDao().observeUnreadCount(threadId)

    suspend fun markRead(threadId: String) = database.threadDao().markRead(threadId)

    suspend fun setPinned(threadId: String, pinned: Boolean) =
        database.threadDao().setPinned(threadId, pinned)

    private fun ThreadSummary.toInboxConversation(showSenderDeleted: Boolean): Conversation =
        Conversation(
            id = thread.id,
            title = thread.title,
            avatarText = thread.avatarText,
            source = thread.source.toDisplaySource(),
            timestamp = formatThreadTimestamp(lastTimestamp ?: thread.updatedAt),
            unreadCount = unreadCount,
            isPinned = thread.isPinned,
            messages = emptyList(),
            previewText = if (lastIsDeleted) {
                if (lastDeletedBySender && showSenderDeleted) "Deleted by sender" else ""
            } else {
                displayBody(lastBody, lastContentType, lastSubject)
            },
        )

    private fun ThreadEntity.toConversation(
        messages: List<MessageEntity>,
        showSenderDeleted: Boolean,
    ): Conversation {
        val visible = messages.filter { !it.isDeleted || it.deletedBySender && showSenderDeleted }
        return Conversation(
            id = id,
            title = title,
            avatarText = avatarText,
            source = source.toDisplaySource(),
            timestamp = formatThreadTimestamp(visible.lastOrNull()?.timestamp ?: updatedAt),
            unreadCount = messages.count { it.direction == MessageDirection.INCOMING && !it.isRead && !it.isDeleted },
            isPinned = isPinned,
            messages = visible.map { message ->
                ChatMessage(
                    id = message.id,
                    text = if (message.isDeleted) "Deleted by sender" else displayBody(
                        message.body,
                        message.contentType,
                        message.subject,
                    ),
                    time = formatMessageTimestamp(message.timestamp, message.deliveryStatus, message.direction),
                    isMine = message.direction == MessageDirection.OUTGOING,
                )
            },
        )
    }
}

private fun String.toDisplaySource(): MessageSource = when (uppercase(Locale.ROOT)) {
    "IMESSAGE" -> MessageSource.IMESSAGE
    "RCS" -> MessageSource.RCS
    "SMS", "MMS" -> MessageSource.SMS
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

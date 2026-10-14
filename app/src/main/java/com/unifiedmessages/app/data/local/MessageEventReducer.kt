package com.unifiedmessages.app.data.local

import androidx.room.withTransaction
import com.unifiedmessages.app.provider.DeliveryUpdated
import com.unifiedmessages.app.provider.MessageDeleted
import com.unifiedmessages.app.provider.MessageEdited
import com.unifiedmessages.app.provider.MessageEvent
import com.unifiedmessages.app.provider.MessageReceived
import com.unifiedmessages.app.provider.MessageSent
import com.unifiedmessages.app.provider.ProviderMessage
import com.unifiedmessages.app.provider.ProviderThread
import com.unifiedmessages.app.provider.ReactionAdded
import com.unifiedmessages.app.provider.ReactionRemoved
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.UUID

/** Provider-local IDs become stable app IDs without collisions across transports or entity kinds. */
object ProviderIds {
    fun thread(providerId: String, providerThreadId: String): String = id("thread", providerId, providerThreadId)
    fun message(providerId: String, providerMessageId: String): String = id("message", providerId, providerMessageId)
    fun participant(providerId: String, providerThreadId: String, address: String): String =
        id("participant", providerId, providerThreadId, address)
    fun attachment(providerId: String, providerMessageId: String, providerAttachmentId: String): String =
        id("attachment", providerId, providerMessageId, providerAttachmentId)
    fun reaction(providerId: String, providerMessageId: String, providerReactionId: String, addEventId: String): String =
        id("reaction", providerId, providerMessageId, providerReactionId, addEventId)
    fun event(providerId: String, providerEventId: String): String = id("event", providerId, providerEventId)

    private fun id(vararg parts: String): String {
        val bytes = ByteArrayOutputStream().also { stream ->
            DataOutputStream(stream).use { data -> parts.forEach(data::writeUTF) }
        }.toByteArray()
        return UUID.nameUUIDFromBytes(bytes).toString()
    }
}

/** A small store seam lets the real reducer be unit tested without an Android runtime. */
interface MessageEventStore {
    suspend fun <T> transaction(block: suspend () -> T): T
    suspend fun hasEvent(id: String): Boolean
    suspend fun recordEvent(event: AppliedProviderEventEntity)
    suspend fun getThread(id: String): ThreadEntity?
    suspend fun insertThread(thread: ThreadEntity)
    suspend fun updateThread(thread: ThreadEntity)
    suspend fun getParticipant(id: String): ParticipantEntity?
    suspend fun insertParticipant(participant: ParticipantEntity)
    suspend fun updateParticipant(participant: ParticipantEntity)
    suspend fun getMessage(id: String): MessageEntity?
    suspend fun insertMessage(message: MessageEntity)
    suspend fun updateMessage(message: MessageEntity)
    suspend fun insertAttachment(attachment: AttachmentEntity)
    suspend fun getLatestReaction(messageId: String, providerReactionId: String): ReactionEntity?
    suspend fun insertReaction(reaction: ReactionEntity)
    suspend fun updateReaction(reaction: ReactionEntity)
    suspend fun insertEdit(edit: MessageEditEntity)
    suspend fun insertDeletion(deletion: MessageDeletionEntity)
}

class RoomMessageEventStore(private val database: UnifiedMessagesDatabase) : MessageEventStore {
    private val threads = database.threadDao()
    private val messages = database.messageDao()
    private val events = database.appliedProviderEventDao()

    override suspend fun <T> transaction(block: suspend () -> T): T = database.withTransaction { block() }
    override suspend fun hasEvent(id: String): Boolean = events.contains(id)
    override suspend fun recordEvent(event: AppliedProviderEventEntity) { events.insert(event) }
    override suspend fun getThread(id: String): ThreadEntity? = threads.getThread(id)
    override suspend fun insertThread(thread: ThreadEntity) { threads.insert(thread) }
    override suspend fun updateThread(thread: ThreadEntity) { threads.update(thread) }
    override suspend fun getParticipant(id: String): ParticipantEntity? = threads.getParticipant(id)
    override suspend fun insertParticipant(participant: ParticipantEntity) { threads.insertParticipant(participant) }
    override suspend fun updateParticipant(participant: ParticipantEntity) { threads.updateParticipant(participant) }
    override suspend fun getMessage(id: String): MessageEntity? = messages.getMessage(id)
    override suspend fun insertMessage(message: MessageEntity) { messages.insert(message) }
    override suspend fun updateMessage(message: MessageEntity) { messages.update(message) }
    override suspend fun insertAttachment(attachment: AttachmentEntity) { messages.insertAttachment(attachment) }
    override suspend fun getLatestReaction(messageId: String, providerReactionId: String): ReactionEntity? =
        messages.getLatestReaction(messageId, providerReactionId)
    override suspend fun insertReaction(reaction: ReactionEntity) { messages.insertReaction(reaction) }
    override suspend fun updateReaction(reaction: ReactionEntity) { messages.updateReaction(reaction) }
    override suspend fun insertEdit(edit: MessageEditEntity) { messages.insertEdit(edit) }
    override suspend fun insertDeletion(deletion: MessageDeletionEntity) { messages.insertDeletion(deletion) }
}

/** Applies each provider event atomically. Unknown targets remain retryable (the event is not acknowledged). */
class MessageEventReducer(private val store: MessageEventStore) {
    constructor(database: UnifiedMessagesDatabase) : this(RoomMessageEventStore(database))

    /** Returns false for a replay or an event whose target has not arrived yet. */
    suspend fun apply(event: MessageEvent): Boolean = store.transaction {
        require(event.providerId.isNotBlank() && event.eventId.isNotBlank()) { "Provider and event IDs must be stable" }
        val eventKey = ProviderIds.event(event.providerId, event.eventId)
        if (store.hasEvent(eventKey)) return@transaction false

        val applied = when (event) {
            is MessageReceived -> upsertMessage(
                event.providerId, event.thread, event.message, MessageDirection.INCOMING,
            )
            is MessageSent -> upsertMessage(
                event.providerId, event.thread, event.message, MessageDirection.OUTGOING,
            )
            is ReactionAdded -> {
                val messageId = ProviderIds.message(event.providerId, event.providerMessageId)
                if (store.getMessage(messageId) == null) false else {
                    val latest = store.getLatestReaction(messageId, event.reactionId)
                    if (latest == null || latest.removedAt != null) {
                        store.insertReaction(
                            ReactionEntity(
                                id = ProviderIds.reaction(
                                    event.providerId, event.providerMessageId, event.reactionId, event.eventId,
                                ),
                                messageId = messageId,
                                providerReactionId = event.reactionId,
                                actorAddress = event.actorAddress,
                                emoji = event.emoji,
                                createdAt = event.occurredAt,
                            ),
                        )
                    }
                    true
                }
            }
            is ReactionRemoved -> {
                val messageId = ProviderIds.message(event.providerId, event.providerMessageId)
                val reaction = store.getLatestReaction(messageId, event.reactionId)
                if (reaction == null) false else {
                    if (reaction.removedAt == null) store.updateReaction(reaction.copy(removedAt = event.occurredAt))
                    true
                }
            }
            is MessageEdited -> {
                val message = store.getMessage(ProviderIds.message(event.providerId, event.providerMessageId))
                if (message == null) false else {
                    store.insertEdit(
                        MessageEditEntity(
                            id = eventKey,
                            messageId = message.id,
                            previousBody = message.body,
                            newBody = event.newBody,
                            editedAt = event.occurredAt,
                        ),
                    )
                    if (message.editedAt == null || event.occurredAt >= message.editedAt) {
                        store.updateMessage(message.copy(body = event.newBody, editedAt = event.occurredAt))
                    }
                    true
                }
            }
            is MessageDeleted -> {
                val message = store.getMessage(ProviderIds.message(event.providerId, event.providerMessageId))
                if (message == null) false else {
                    store.insertDeletion(
                        MessageDeletionEntity(
                            id = eventKey,
                            messageId = message.id,
                            deletedAt = event.occurredAt,
                            bySender = event.bySender,
                        ),
                    )
                    store.updateMessage(
                        message.copy(isDeleted = true, deletedBySender = message.deletedBySender || event.bySender),
                    )
                    true
                }
            }
            is DeliveryUpdated -> {
                val message = store.getMessage(ProviderIds.message(event.providerId, event.providerMessageId))
                if (message == null) false else {
                    store.updateMessage(
                        message.copy(deliveryStatus = message.deliveryStatus.advanceTo(event.status)),
                    )
                    true
                }
            }
        }
        if (applied) {
            store.recordEvent(
                AppliedProviderEventEntity(eventKey, event.providerId, event.eventId, event.occurredAt),
            )
        }
        applied
    }

    private suspend fun upsertMessage(
        providerId: String,
        thread: ProviderThread,
        message: ProviderMessage,
        direction: MessageDirection,
    ): Boolean {
        require(thread.providerThreadId.isNotBlank() && message.providerMessageId.isNotBlank()) {
            "Provider thread and message IDs must be stable"
        }
        val threadId = ProviderIds.thread(providerId, thread.providerThreadId)
        val previousThread = store.getThread(threadId)
        if (previousThread == null) {
            store.insertThread(
                ThreadEntity(
                    id = threadId,
                    providerId = providerId,
                    providerThreadId = thread.providerThreadId,
                    source = thread.source,
                    title = thread.title,
                    avatarText = thread.avatarText,
                    updatedAt = message.timestamp,
                ),
            )
        } else {
            val newest = message.timestamp >= previousThread.updatedAt
            store.updateThread(
                previousThread.copy(
                    source = if (newest) thread.source else previousThread.source,
                    title = if (newest) thread.title else previousThread.title,
                    avatarText = if (newest) thread.avatarText else previousThread.avatarText,
                    updatedAt = maxOf(previousThread.updatedAt, message.timestamp),
                ),
            )
        }

        val participants = thread.participants + listOfNotNull(
            message.senderAddress?.takeIf { it.isNotBlank() && direction == MessageDirection.INCOMING }
                ?.let { com.unifiedmessages.app.provider.ProviderParticipant(address = it) },
        )
        participants.distinctBy { it.address }.forEach { incoming ->
            val id = ProviderIds.participant(providerId, thread.providerThreadId, incoming.address)
            val existing = store.getParticipant(id)
            if (existing == null) {
                store.insertParticipant(
                    ParticipantEntity(id, threadId, incoming.address, incoming.displayName, incoming.isSelf),
                )
            } else if (incoming.displayName != null || incoming.isSelf != existing.isSelf) {
                store.updateParticipant(
                    existing.copy(
                        displayName = incoming.displayName ?: existing.displayName,
                        isSelf = existing.isSelf || incoming.isSelf,
                    ),
                )
            }
        }

        val messageId = ProviderIds.message(providerId, message.providerMessageId)
        val previousMessage = store.getMessage(messageId)
        if (previousMessage == null) {
            store.insertMessage(
                MessageEntity(
                    id = messageId,
                    threadId = threadId,
                    providerMessageId = message.providerMessageId,
                    senderAddress = message.senderAddress,
                    body = message.body,
                    contentType = message.contentType,
                    direction = direction,
                    timestamp = message.timestamp,
                    isRead = direction == MessageDirection.OUTGOING || message.isRead,
                    deliveryStatus = message.deliveryStatus,
                    subscriptionId = message.subscriptionId,
                    systemUri = message.systemUri,
                    subject = message.subject,
                ),
            )
        } else {
            // Replayed imports may carry newer read/delivery state, but cannot erase content/history.
            store.updateMessage(
                previousMessage.copy(
                    body = previousMessage.body ?: message.body,
                    contentType = if (message.attachments.isNotEmpty()) ContentType.MEDIA else previousMessage.contentType,
                    isRead = previousMessage.isRead || message.isRead,
                    deliveryStatus = previousMessage.deliveryStatus.advanceTo(message.deliveryStatus),
                    subscriptionId = previousMessage.subscriptionId ?: message.subscriptionId,
                    systemUri = previousMessage.systemUri ?: message.systemUri,
                    subject = previousMessage.subject ?: message.subject,
                ),
            )
        }
        message.attachments.forEach { attachment ->
            store.insertAttachment(
                AttachmentEntity(
                    id = ProviderIds.attachment(providerId, message.providerMessageId, attachment.providerAttachmentId),
                    messageId = messageId,
                    providerAttachmentId = attachment.providerAttachmentId,
                    contentUri = attachment.contentUri,
                    mimeType = attachment.mimeType,
                    fileName = attachment.fileName,
                    sizeBytes = attachment.sizeBytes,
                ),
            )
        }
        return true
    }
}

/** Late transport callbacks and repeated imports cannot move a delivered message back to sent. */
private fun DeliveryStatus.advanceTo(candidate: DeliveryStatus): DeliveryStatus =
    if (candidate.progressRank() > progressRank()) candidate else this

private fun DeliveryStatus.progressRank(): Int = when (this) {
    DeliveryStatus.UNKNOWN -> 0
    DeliveryStatus.PENDING -> 1
    DeliveryStatus.SENT -> 2
    DeliveryStatus.FAILED, DeliveryStatus.RECEIVED -> 3
    DeliveryStatus.DELIVERED -> 4
}

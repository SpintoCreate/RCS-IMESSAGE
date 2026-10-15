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
import com.unifiedmessages.app.provider.ReadReceiptObserved
import com.unifiedmessages.app.provider.TypingChanged
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64
import java.util.UUID

/** Provider-local IDs become stable app IDs without collisions across transports or entity kinds. */
object ProviderIds {
    /** Existing local SMS IDs stay unchanged; other logins get distinct ID namespaces. */
    fun namespace(providerId: String, accountId: String): String =
        if (accountId == "$providerId:local") providerId else "$providerId@$accountId"

    fun thread(providerId: String, providerThreadId: String): String = id("thread", providerId, providerThreadId)
    fun message(providerId: String, providerMessageId: String): String = id("message", providerId, providerMessageId)
    fun participant(providerId: String, providerThreadId: String, address: String): String =
        id("participant", providerId, providerThreadId, address)
    fun attachment(providerId: String, providerMessageId: String, providerAttachmentId: String): String =
        id("attachment", providerId, providerMessageId, providerAttachmentId)
    fun reaction(providerId: String, providerMessageId: String, providerReactionId: String, addEventId: String): String =
        id("reaction", providerId, providerMessageId, providerReactionId, addEventId)
    fun event(providerId: String, providerEventId: String): String = id("event", providerId, providerEventId)
    fun mapping(accountId: String, entityType: String, remoteId: String): String =
        id("remote-mapping", accountId, entityType, remoteId)

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
    suspend fun pendingForTarget(targetMessageId: String): List<PendingProviderEventEntity>
    suspend fun insertPending(event: PendingProviderEventEntity)
    suspend fun deletePending(id: String)
    suspend fun insertMapping(mapping: RemoteIdMappingEntity)
    suspend fun getMapping(accountId: String, entityType: String, remoteId: String): RemoteIdMappingEntity?
    suspend fun insertReadReceipt(receipt: ReadReceiptEntity)
    suspend fun getTypingState(id: String): TypingStateEntity?
    suspend fun upsertTypingState(state: TypingStateEntity)
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
    override suspend fun pendingForTarget(targetMessageId: String): List<PendingProviderEventEntity> =
        database.providerSyncDao().pendingForTarget(targetMessageId)
    override suspend fun insertPending(event: PendingProviderEventEntity) {
        database.providerSyncDao().insertPending(event)
    }
    override suspend fun deletePending(id: String) { database.providerSyncDao().deletePending(id) }
    override suspend fun insertMapping(mapping: RemoteIdMappingEntity) {
        database.providerSyncDao().insertMapping(mapping)
    }
    override suspend fun getMapping(accountId: String, entityType: String, remoteId: String): RemoteIdMappingEntity? =
        database.providerSyncDao().mapping(accountId, entityType, remoteId)
    override suspend fun insertReadReceipt(receipt: ReadReceiptEntity) {
        messages.insertReadReceipt(receipt)
    }
    override suspend fun getTypingState(id: String): TypingStateEntity? = database.typingStateDao().get(id)
    override suspend fun upsertTypingState(state: TypingStateEntity) {
        database.typingStateDao().upsert(state)
    }
}

/** Applies each provider event atomically. Unknown targets remain retryable (the event is not acknowledged). */
class MessageEventReducer(private val store: MessageEventStore) {
    constructor(database: UnifiedMessagesDatabase) : this(RoomMessageEventStore(database))

    /** Once a queued local echo gets its server event ID, keep its original local row. */
    suspend fun bindRemoteMessageId(accountId: String, providerId: String, remoteId: String, localId: String) {
        store.transaction {
            require(store.getMessage(localId) != null) { "The local echo must exist before binding." }
            val prior = store.getMapping(accountId, "message", remoteId)
            check(prior == null || prior.localId == localId) { "Remote event already belongs to another message." }
            if (prior == null) store.insertMapping(RemoteIdMappingEntity(
                ProviderIds.mapping(accountId, "message", remoteId), accountId, "message", remoteId, localId,
            ))
            replayPending(ProviderIds.message(ProviderIds.namespace(providerId, accountId), remoteId))
        }
    }

    private suspend fun localMessageId(providerId: String, accountId: String, remoteId: String): String =
        store.getMapping(accountId, "message", remoteId)?.localId
            ?: ProviderIds.message(ProviderIds.namespace(providerId, accountId), remoteId)

    /** Returns false for a replay or an event whose target has not arrived yet. */
    suspend fun apply(event: MessageEvent): Boolean = store.transaction {
        require(event.providerId.isNotBlank() && event.eventId.isNotBlank()) { "Provider and event IDs must be stable" }
        val identity = ProviderIds.namespace(event.providerId, event.accountId)
        val eventKey = ProviderIds.event(identity, event.eventId)
        if (store.hasEvent(eventKey)) return@transaction false

        val applied = when (event) {
            is MessageReceived -> upsertMessage(
                event.providerId, event.accountId, event.thread, event.message, MessageDirection.INCOMING,
            )
            is MessageSent -> upsertMessage(
                event.providerId, event.accountId, event.thread, event.message, MessageDirection.OUTGOING,
            )
            is ReactionAdded -> {
                val messageId = localMessageId(event.providerId, event.accountId, event.providerMessageId)
                if (store.getMessage(messageId) == null) false else {
                    val latest = store.getLatestReaction(messageId, event.reactionId)
                    if (latest == null || latest.removedAt != null) {
                        store.insertReaction(
                            ReactionEntity(
                                id = ProviderIds.reaction(
                                    identity, event.providerMessageId, event.reactionId, event.eventId,
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
                val messageId = localMessageId(event.providerId, event.accountId, event.providerMessageId)
                val reaction = store.getLatestReaction(messageId, event.reactionId)
                if (reaction == null) false else {
                    if (reaction.removedAt == null) store.updateReaction(reaction.copy(removedAt = event.occurredAt))
                    true
                }
            }
            is MessageEdited -> {
                val message = store.getMessage(localMessageId(event.providerId, event.accountId, event.providerMessageId))
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
                val message = store.getMessage(localMessageId(event.providerId, event.accountId, event.providerMessageId))
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
                val message = store.getMessage(localMessageId(event.providerId, event.accountId, event.providerMessageId))
                if (message == null) false else {
                    store.updateMessage(
                        message.copy(deliveryStatus = message.deliveryStatus.advanceTo(event.status)),
                    )
                    true
                }
            }
            is ReadReceiptObserved -> {
                val messageId = localMessageId(event.providerId, event.accountId, event.providerMessageId)
                if (store.getMessage(messageId) == null) false else {
                    store.insertReadReceipt(ReadReceiptEntity(eventKey, messageId,
                        event.actorAddress, event.occurredAt))
                    true
                }
            }
            is TypingChanged -> {
                val threadId = ProviderIds.thread(identity, event.providerThreadId)
                val typingId = ProviderIds.mapping(event.accountId, "typing",
                    "${event.providerThreadId}|${event.actorAddress}")
                val previous = store.getTypingState(typingId)
                if (previous == null || event.occurredAt >= previous.updatedAt) {
                    store.upsertTypingState(TypingStateEntity(typingId, threadId,
                        event.actorAddress, event.isTyping, event.occurredAt))
                }
                true
            }
        }
        if (applied) {
            store.recordEvent(
                AppliedProviderEventEntity(eventKey, event.providerId, event.eventId, event.occurredAt),
            )
            if (event is MessageReceived || event is MessageSent || event is ReactionAdded) {
                val targetId = when (event) {
                    is MessageReceived -> localMessageId(event.providerId, event.accountId, event.message.providerMessageId)
                    is MessageSent -> localMessageId(event.providerId, event.accountId, event.message.providerMessageId)
                    is ReactionAdded -> localMessageId(event.providerId, event.accountId, event.providerMessageId)
                    else -> error("unreachable")
                }
                replayPending(targetId)
            }
        } else {
            PendingEventCodec.encode(event)?.let { encoded ->
                store.insertPending(
                    PendingProviderEventEntity(
                        id = eventKey,
                        accountId = event.accountId,
                        providerId = event.providerId,
                        eventId = event.eventId,
                        targetMessageId = localMessageId(event.providerId, event.accountId, encoded.first),
                        remoteMessageId = encoded.first,
                        eventType = encoded.second,
                        payload = encoded.third,
                        occurredAt = event.occurredAt,
                    ),
                )
            }
        }
        applied
    }

    private suspend fun replayPending(targetId: String) {
        // A reaction removal can arrive before both its target message and the reaction add.
        // Repeat until one pass makes no progress, leaving genuinely unresolved events durable.
        while (true) {
            var progressed = false
            store.pendingForTarget(targetId).forEach { pending ->
                if (apply(PendingEventCodec.decode(pending))) {
                    store.deletePending(pending.id)
                    progressed = true
                }
            }
            if (!progressed) return
        }
    }

    private suspend fun upsertMessage(
        providerId: String,
        accountId: String,
        thread: ProviderThread,
        message: ProviderMessage,
        direction: MessageDirection,
    ): Boolean {
        require(thread.providerThreadId.isNotBlank() && message.providerMessageId.isNotBlank()) {
            "Provider thread and message IDs must be stable"
        }
        val identity = ProviderIds.namespace(providerId, accountId)
        val threadId = ProviderIds.thread(identity, thread.providerThreadId)
        store.insertMapping(RemoteIdMappingEntity(
            ProviderIds.mapping(accountId, "thread", thread.providerThreadId),
            accountId, "thread", thread.providerThreadId, threadId,
        ))
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
                    accountId = accountId,
                    networkType = thread.source.uppercase(),
                ),
            )
        } else {
            val newest = message.timestamp >= previousThread.updatedAt
            store.updateThread(
                previousThread.copy(
                    source = if (newest) thread.source else previousThread.source,
                    networkType = if (providerId == "matrix" && previousThread.networkType !in listOf(null, "MATRIX"))
                        previousThread.networkType
                    else if (newest) thread.source.uppercase() else previousThread.networkType,
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
            val id = ProviderIds.participant(identity, thread.providerThreadId, incoming.address)
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

        val messageId = localMessageId(providerId, accountId, message.providerMessageId)
        store.insertMapping(RemoteIdMappingEntity(
            ProviderIds.mapping(accountId, "message", message.providerMessageId),
            accountId, "message", message.providerMessageId, messageId,
        ))
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
                    replyToProviderMessageId = message.replyToProviderMessageId,
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
                    replyToProviderMessageId = previousMessage.replyToProviderMessageId
                        ?: message.replyToProviderMessageId,
                ),
            )
        }
        message.attachments.forEach { attachment ->
            store.insertAttachment(
                AttachmentEntity(
                    id = ProviderIds.attachment(identity, message.providerMessageId, attachment.providerAttachmentId),
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

/** Binary length-prefixed fields avoid delimiter or Unicode corruption in queued relations. */
private object PendingEventCodec {
    fun encode(event: MessageEvent): Triple<String, String, String>? {
        val target = when (event) {
            is ReactionAdded -> event.providerMessageId
            is ReactionRemoved -> event.providerMessageId
            is MessageEdited -> event.providerMessageId
            is MessageDeleted -> event.providerMessageId
            is ReadReceiptObserved -> event.providerMessageId
            else -> return null
        }
        val bytes = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                when (event) {
                    is ReactionAdded -> {
                        out.writeUTF(event.reactionId)
                        out.writeUTF(event.actorAddress)
                        out.writeUTF(event.emoji)
                    }
                    is ReactionRemoved -> out.writeUTF(event.reactionId)
                    is MessageEdited -> {
                        out.writeBoolean(event.newBody != null)
                        if (event.newBody != null) out.writeUTF(event.newBody)
                    }
                    is MessageDeleted -> out.writeBoolean(event.bySender)
                    is ReadReceiptObserved -> out.writeUTF(event.actorAddress)
                    else -> Unit
                }
            }
        }.toByteArray()
        val type = when (event) {
            is ReactionAdded -> "ReactionAdded"
            is ReactionRemoved -> "ReactionRemoved"
            is MessageEdited -> "MessageEdited"
            is MessageDeleted -> "MessageDeleted"
            is ReadReceiptObserved -> "ReadReceiptObserved"
            else -> error("Unsupported pending event")
        }
        return Triple(target, type, Base64.getEncoder().encodeToString(bytes))
    }

    fun decode(pending: PendingProviderEventEntity): MessageEvent {
        val remoteId = pending.remoteMessageId
        val payload = Base64.getDecoder().decode(pending.payload)
        return DataInputStream(ByteArrayInputStream(payload)).use { input ->
            when (pending.eventType) {
                "ReactionAdded" -> com.unifiedmessages.app.provider.ReactionAdded(
                    pending.providerId, pending.eventId, pending.occurredAt,
                    remoteId, input.readUTF(), input.readUTF(), input.readUTF(), pending.accountId,
                )
                "ReactionRemoved" -> com.unifiedmessages.app.provider.ReactionRemoved(
                    pending.providerId, pending.eventId, pending.occurredAt, remoteId, input.readUTF(), pending.accountId,
                )
                "MessageEdited" -> MessageEdited(
                    pending.providerId, pending.eventId, pending.occurredAt, remoteId,
                    if (input.readBoolean()) input.readUTF() else null, pending.accountId,
                )
                "MessageDeleted" -> MessageDeleted(
                    pending.providerId, pending.eventId, pending.occurredAt, remoteId, input.readBoolean(), pending.accountId,
                )
                "ReadReceiptObserved" -> ReadReceiptObserved(
                    pending.providerId, pending.eventId, pending.occurredAt, remoteId,
                    input.readUTF(), pending.accountId,
                )
                else -> error("Unknown pending event type: ${pending.eventType}")
            }
        }
    }
}

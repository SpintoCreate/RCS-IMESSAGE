package com.unifiedmessages.app.provider

import com.unifiedmessages.app.data.local.ContentType
import com.unifiedmessages.app.data.local.DeliveryStatus
import kotlinx.coroutines.flow.Flow

/** A transport reports what it can actually do; the UI must gate actions on these flags. */
data class Capabilities(
    val supportsReactions: Boolean = false,
    val supportsEdit: Boolean = false,
    val supportsUnsend: Boolean = false,
    val supportsReadReceipts: Boolean = false,
    val supportsAppMessages: Boolean = false,
)

interface MessageProvider {
    val id: String
    val capabilities: Capabilities
    val events: Flow<MessageEvent>
}

/** IDs in these payloads belong to the transport. The reducer namespaces them by provider ID. */
data class ProviderThread(
    val providerThreadId: String,
    val source: String,
    val title: String,
    val avatarText: String = title.take(2).uppercase(),
    val participants: List<ProviderParticipant> = emptyList(),
)

data class ProviderParticipant(
    val address: String,
    val displayName: String? = null,
    val isSelf: Boolean = false,
)

data class ProviderAttachment(
    val providerAttachmentId: String,
    val contentUri: String?,
    val mimeType: String,
    val fileName: String? = null,
    val sizeBytes: Long? = null,
)

data class ProviderMessage(
    val providerMessageId: String,
    val body: String? = null,
    val contentType: ContentType = ContentType.TEXT,
    val timestamp: Long,
    val senderAddress: String? = null,
    val isRead: Boolean = false,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.UNKNOWN,
    val attachments: List<ProviderAttachment> = emptyList(),
    val subscriptionId: Int? = null,
    val systemUri: String? = null,
    val subject: String? = null,
    val replyToProviderMessageId: String? = null,
)

sealed interface MessageEvent {
    val providerId: String
    val accountId: String
        get() = "$providerId:local"
    val eventId: String
    val occurredAt: Long
}

data class MessageReceived(
    override val providerId: String,
    override val eventId: String,
    override val occurredAt: Long,
    val thread: ProviderThread,
    val message: ProviderMessage,
    override val accountId: String = "$providerId:local",
) : MessageEvent

data class MessageSent(
    override val providerId: String,
    override val eventId: String,
    override val occurredAt: Long,
    val thread: ProviderThread,
    val message: ProviderMessage,
    override val accountId: String = "$providerId:local",
) : MessageEvent

data class ReactionAdded(
    override val providerId: String,
    override val eventId: String,
    override val occurredAt: Long,
    val providerMessageId: String,
    val reactionId: String,
    val actorAddress: String,
    val emoji: String,
    override val accountId: String = "$providerId:local",
) : MessageEvent

data class ReactionRemoved(
    override val providerId: String,
    override val eventId: String,
    override val occurredAt: Long,
    val providerMessageId: String,
    val reactionId: String,
    override val accountId: String = "$providerId:local",
) : MessageEvent

data class MessageEdited(
    override val providerId: String,
    override val eventId: String,
    override val occurredAt: Long,
    val providerMessageId: String,
    val newBody: String?,
    override val accountId: String = "$providerId:local",
) : MessageEvent

data class MessageDeleted(
    override val providerId: String,
    override val eventId: String,
    override val occurredAt: Long,
    val providerMessageId: String,
    val bySender: Boolean,
    override val accountId: String = "$providerId:local",
) : MessageEvent

data class DeliveryUpdated(
    override val providerId: String,
    override val eventId: String,
    override val occurredAt: Long,
    val providerMessageId: String,
    val status: DeliveryStatus,
    override val accountId: String = "$providerId:local",
) : MessageEvent

data class ReadReceiptObserved(
    override val providerId: String,
    override val eventId: String,
    override val occurredAt: Long,
    val providerMessageId: String,
    val actorAddress: String,
    override val accountId: String = "$providerId:local",
) : MessageEvent

data class TypingChanged(
    override val providerId: String,
    override val eventId: String,
    override val occurredAt: Long,
    val providerThreadId: String,
    val actorAddress: String,
    val isTyping: Boolean,
    override val accountId: String = "$providerId:local",
) : MessageEvent

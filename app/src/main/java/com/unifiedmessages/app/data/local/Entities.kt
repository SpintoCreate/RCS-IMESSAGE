package com.unifiedmessages.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class ContentType { TEXT, MEDIA, APP_MESSAGE }
enum class MessageDirection { INCOMING, OUTGOING }
enum class DeliveryStatus { UNKNOWN, PENDING, SENT, DELIVERED, FAILED, RECEIVED }

class MessageTypeConverters {
    @TypeConverter fun fromContentType(value: ContentType): String = value.name
    @TypeConverter fun toContentType(value: String): ContentType = ContentType.valueOf(value)
    @TypeConverter fun fromDirection(value: MessageDirection): String = value.name
    @TypeConverter fun toDirection(value: String): MessageDirection = MessageDirection.valueOf(value)
    @TypeConverter fun fromDeliveryStatus(value: DeliveryStatus): String = value.name
    @TypeConverter fun toDeliveryStatus(value: String): DeliveryStatus = DeliveryStatus.valueOf(value)
}

/** Provider IDs are kept separately from the app's stable, namespaced primary keys. */
@Entity(
    tableName = "threads",
    indices = [Index(value = ["accountId", "providerId", "providerThreadId"], unique = true)],
)
data class ThreadEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val providerThreadId: String,
    val source: String,
    val title: String,
    val avatarText: String,
    val isPinned: Boolean = false,
    val updatedAt: Long = 0L,
    /** The owning login. Legacy SMS rows are backfilled to sms:local by migration. */
    val accountId: String? = null,
    /** The visible network is independent of the provider transport. */
    val networkType: String? = null,
)

@Entity(tableName = "provider_accounts", indices = [Index("providerId")])
data class ProviderAccountEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val remoteSelfId: String?,
    val connectionState: String,
    /** Android Keystore-backed storage owns the secret; this is only its lookup key. */
    val credentialRef: String?,
    val homeserver: String? = null,
)

@Entity(
    tableName = "remote_id_mappings",
    indices = [Index(value = ["accountId", "entityType", "remoteId"], unique = true), Index("localId")],
)
data class RemoteIdMappingEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val entityType: String,
    val remoteId: String,
    val localId: String,
)

@Entity(tableName = "sync_cursors", indices = [Index("accountId")])
data class SyncCursorEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val remoteRoomId: String?,
    val cursor: String,
    val updatedAt: Long,
)

/** Unresolved relation events wait here until their target message is backfilled. */
@Entity(tableName = "pending_provider_events", indices = [Index("accountId"), Index("targetMessageId")])
data class PendingProviderEventEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val providerId: String,
    val eventId: String,
    val targetMessageId: String,
    val remoteMessageId: String,
    val eventType: String,
    val payload: String,
    val occurredAt: Long,
)

@Entity(
    tableName = "participants",
    foreignKeys = [ForeignKey(entity = ThreadEntity::class, parentColumns = ["id"], childColumns = ["threadId"])],
    indices = [Index("threadId"), Index(value = ["threadId", "address"], unique = true)],
)
data class ParticipantEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val address: String,
    val displayName: String? = null,
    val isSelf: Boolean = false,
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(entity = ThreadEntity::class, parentColumns = ["id"], childColumns = ["threadId"])],
    indices = [Index("threadId"), Index(value = ["threadId", "timestamp"]), Index(value = ["threadId", "providerMessageId"], unique = true)],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val providerMessageId: String,
    val senderAddress: String? = null,
    val body: String? = null,
    val contentType: ContentType = ContentType.TEXT,
    val direction: MessageDirection,
    val timestamp: Long,
    val isRead: Boolean = false,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.UNKNOWN,
    val isDeleted: Boolean = false,
    val deletedBySender: Boolean = false,
    val editedAt: Long? = null,
    val subscriptionId: Int? = null,
    val systemUri: String? = null,
    val subject: String? = null,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(entity = MessageEntity::class, parentColumns = ["id"], childColumns = ["messageId"])],
    indices = [Index("messageId"), Index(value = ["messageId", "providerAttachmentId"], unique = true)],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val providerAttachmentId: String,
    val contentUri: String?,
    val mimeType: String,
    val fileName: String? = null,
    val sizeBytes: Long? = null,
)

/** Removal sets removedAt instead of deleting this row, preserving the reaction history. */
@Entity(
    tableName = "reactions",
    foreignKeys = [ForeignKey(entity = MessageEntity::class, parentColumns = ["id"], childColumns = ["messageId"])],
    indices = [Index("messageId")],
)
data class ReactionEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val providerReactionId: String,
    val actorAddress: String,
    val emoji: String,
    val createdAt: Long,
    val removedAt: Long? = null,
)

@Entity(
    tableName = "message_edits",
    foreignKeys = [ForeignKey(entity = MessageEntity::class, parentColumns = ["id"], childColumns = ["messageId"])],
    indices = [Index("messageId")],
)
data class MessageEditEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val previousBody: String?,
    val newBody: String?,
    val editedAt: Long,
)

@Entity(
    tableName = "message_deletions",
    foreignKeys = [ForeignKey(entity = MessageEntity::class, parentColumns = ["id"], childColumns = ["messageId"])],
    indices = [Index("messageId")],
)
data class MessageDeletionEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val deletedAt: Long,
    val bySender: Boolean,
)

/** This ledger prevents replayed provider events from duplicating history rows. */
@Entity(tableName = "applied_provider_events")
data class AppliedProviderEventEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val providerEventId: String,
    val occurredAt: Long,
)

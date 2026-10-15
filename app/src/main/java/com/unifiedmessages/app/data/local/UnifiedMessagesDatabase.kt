package com.unifiedmessages.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class ThreadSummary(
    @Embedded val thread: ThreadEntity,
    val lastBody: String?,
    val lastTimestamp: Long?,
    val lastIsDeleted: Boolean,
    val lastDeletedBySender: Boolean,
    val lastContentType: ContentType?,
    val lastSubject: String?,
    val unreadCount: Int,
    /** Newest nondeleted body containing the active inbox search text, if any. */
    val matchingBody: String?,
)

@Dao
interface ThreadDao {
    @Query("SELECT * FROM threads WHERE id = :id LIMIT 1")
    suspend fun getThread(id: String): ThreadEntity?

    @Query("SELECT * FROM threads WHERE id = :id LIMIT 1")
    fun observeThread(id: String): Flow<ThreadEntity?>

    @Query(
        """
        WITH search_participants AS (
            SELECT p.threadId, p.address, p.displayName, p.isSelf,
                REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                    p.address, '+', ''), ' ', ''), '-', ''), '(', ''), ')', ''), '.', '')
                    AS numberDigits
            FROM participants p
        )
        SELECT t.*,
            CASE WHEN latest.isDeleted = 1 THEN NULL ELSE latest.body END AS lastBody,
            latest.timestamp AS lastTimestamp,
            COALESCE(latest.isDeleted, 0) AS lastIsDeleted,
            COALESCE(latest.deletedBySender, 0) AS lastDeletedBySender,
            latest.contentType AS lastContentType,
            CASE WHEN latest.isDeleted = 1 THEN NULL ELSE latest.subject END AS lastSubject,
            (SELECT COUNT(*) FROM messages m WHERE m.threadId = t.id
                AND m.direction = 'INCOMING' AND m.isRead = 0 AND m.isDeleted = 0) AS unreadCount,
            (SELECT m.body FROM messages m WHERE m.threadId = t.id AND m.isDeleted = 0
                AND :query != '' AND INSTR(LOWER(COALESCE(m.body, '')), LOWER(:query)) > 0
                ORDER BY m.timestamp DESC, m.id DESC LIMIT 1) AS matchingBody
        FROM threads t
        LEFT JOIN messages latest ON latest.id = (
            SELECT m.id FROM messages m WHERE m.threadId = t.id
                AND (m.isDeleted = 0 OR (:showSenderDeleted = 1 AND m.deletedBySender = 1))
            ORDER BY m.timestamp DESC, m.id DESC LIMIT 1
        )
        WHERE (:source IS NULL OR UPPER(t.source) = :source
            OR (:source = 'SMS' AND UPPER(t.source) = 'MMS'))
          AND (:query = '' OR INSTR(LOWER(t.title), LOWER(:query)) > 0
              OR INSTR(LOWER(t.source), LOWER(:query)) > 0
              OR EXISTS (SELECT 1 FROM search_participants p WHERE p.threadId = t.id AND p.isSelf = 0
                  AND (INSTR(LOWER(p.address), LOWER(:query)) > 0
                       OR INSTR(LOWER(COALESCE(p.displayName, '')), LOWER(:query)) > 0
                       OR (:digits != '' AND (INSTR(p.numberDigits, :digits) > 0
                           OR (:digitsWithoutUsPrefix != ''
                               AND INSTR(p.numberDigits, :digitsWithoutUsPrefix) > 0)))))
              OR t.id IN (:matchingContactThreadIds)
              OR EXISTS (SELECT 1 FROM messages m WHERE m.threadId = t.id AND m.isDeleted = 0
                  AND INSTR(LOWER(COALESCE(m.body, '')), LOWER(:query)) > 0))
        ORDER BY t.isPinned DESC, COALESCE(lastTimestamp, t.updatedAt) DESC, t.id ASC
        """,
    )
    fun observeThreads(
        query: String = "",
        source: String? = null,
        showSenderDeleted: Boolean = true,
        digits: String = "",
        digitsWithoutUsPrefix: String = "",
        matchingContactThreadIds: List<String> = emptyList(),
    ): Flow<List<ThreadSummary>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(thread: ThreadEntity): Long

    @Update
    suspend fun update(thread: ThreadEntity)

    @Query("UPDATE threads SET isPinned = :pinned WHERE id = :threadId")
    suspend fun setPinned(threadId: String, pinned: Boolean)

    @Query("UPDATE messages SET isRead = 1 WHERE threadId = :threadId AND direction = 'INCOMING'")
    suspend fun markRead(threadId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE threadId = :threadId AND direction = 'INCOMING' AND isRead = 0 AND isDeleted = 0")
    fun observeUnreadCount(threadId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertParticipant(participant: ParticipantEntity): Long

    @Update
    suspend fun updateParticipant(participant: ParticipantEntity)

    @Query("SELECT * FROM participants WHERE id = :id LIMIT 1")
    suspend fun getParticipant(id: String): ParticipantEntity?

    @Query("SELECT * FROM participants WHERE threadId = :threadId AND isSelf = 0 ORDER BY address")
    suspend fun getParticipants(threadId: String): List<ParticipantEntity>

    @Query("SELECT * FROM participants WHERE threadId = :threadId AND isSelf = 0 ORDER BY address")
    fun observeParticipants(threadId: String): Flow<List<ParticipantEntity>>

    @Query("SELECT * FROM participants WHERE isSelf = 0 ORDER BY threadId, address")
    fun observeRemoteParticipants(): Flow<List<ParticipantEntity>>
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessage(id: String): MessageEntity?

    @Query("SELECT systemUri FROM messages WHERE threadId = :threadId AND direction = 'INCOMING' AND isRead = 0 AND systemUri IS NOT NULL")
    suspend fun getUnreadSystemUris(threadId: String): List<String>

    @Query("SELECT subscriptionId FROM messages WHERE threadId = :threadId AND subscriptionId IS NOT NULL AND subscriptionId >= 0 ORDER BY timestamp DESC, id DESC LIMIT 1")
    suspend fun getLatestSubscriptionId(threadId: String): Int?

    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY timestamp ASC, id ASC")
    fun observeMessages(threadId: String): Flow<List<MessageEntity>>

    @Query(
        """SELECT * FROM messages WHERE threadId = :threadId
           AND (:query = '' OR (isDeleted = 0 AND LOWER(COALESCE(body, '')) LIKE '%' || LOWER(:query) || '%'))
           ORDER BY timestamp ASC, id ASC""",
    )
    fun observeMessagesMatching(threadId: String, query: String): Flow<List<MessageEntity>>

    @Query(
        """SELECT * FROM messages WHERE threadId = :threadId AND body = :body
           AND contentType = 'TEXT' AND isDeleted = 0 AND timestamp <= :beforeTimestamp
           ORDER BY timestamp DESC, id DESC LIMIT 1""",
    )
    suspend fun findRecentText(threadId: String, body: String, beforeTimestamp: Long): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAttachment(attachment: AttachmentEntity): Long

    @Query("SELECT a.* FROM attachments a JOIN messages m ON m.id = a.messageId WHERE m.threadId = :threadId")
    fun observeAttachments(threadId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE messageId = :messageId ORDER BY id")
    suspend fun getAttachments(messageId: String): List<AttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReaction(reaction: ReactionEntity): Long

    @Query(
        """SELECT * FROM reactions WHERE messageId = :messageId AND providerReactionId = :providerReactionId
           ORDER BY createdAt DESC, id DESC LIMIT 1""",
    )
    suspend fun getLatestReaction(messageId: String, providerReactionId: String): ReactionEntity?

    @Query(
        """SELECT * FROM reactions WHERE messageId = :messageId AND actorAddress = :actorAddress
           AND emoji = :emoji AND removedAt IS NULL ORDER BY createdAt DESC, id DESC LIMIT 1""",
    )
    suspend fun getLatestActiveReaction(messageId: String, actorAddress: String, emoji: String): ReactionEntity?

    @Update
    suspend fun updateReaction(reaction: ReactionEntity)

    @Query("SELECT r.* FROM reactions r JOIN messages m ON m.id = r.messageId WHERE m.threadId = :threadId")
    fun observeReactions(threadId: String): Flow<List<ReactionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEdit(edit: MessageEditEntity): Long

    @Query("SELECT * FROM message_edits WHERE messageId = :messageId ORDER BY editedAt ASC, id ASC")
    fun observeEdits(messageId: String): Flow<List<MessageEditEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDeletion(deletion: MessageDeletionEntity): Long

    @Query("SELECT * FROM message_deletions WHERE messageId = :messageId ORDER BY deletedAt ASC, id ASC")
    fun observeDeletions(messageId: String): Flow<List<MessageDeletionEntity>>
}

@Dao
interface AppliedProviderEventDao {
    @Query("SELECT EXISTS(SELECT 1 FROM applied_provider_events WHERE id = :id)")
    suspend fun contains(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: AppliedProviderEventEntity): Long
}

@Dao
interface ProviderAccountDao {
    @Query("SELECT * FROM provider_accounts WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ProviderAccountEntity?

    @Query("SELECT * FROM provider_accounts WHERE providerId = :providerId ORDER BY id")
    fun observe(providerId: String): Flow<List<ProviderAccountEntity>>

    @Query("SELECT * FROM provider_accounts WHERE providerId = :providerId ORDER BY id")
    suspend fun all(providerId: String): List<ProviderAccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: ProviderAccountEntity)
}

@Dao
interface ProviderSyncDao {
    @Query("SELECT * FROM remote_id_mappings WHERE accountId = :accountId AND entityType = :entityType AND remoteId = :remoteId LIMIT 1")
    suspend fun mapping(accountId: String, entityType: String, remoteId: String): RemoteIdMappingEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMapping(mapping: RemoteIdMappingEntity): Long

    @Query("SELECT * FROM sync_cursors WHERE id = :id LIMIT 1")
    suspend fun cursor(id: String): SyncCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCursor(cursor: SyncCursorEntity)

    @Query("SELECT * FROM pending_provider_events WHERE targetMessageId = :targetMessageId ORDER BY occurredAt, id")
    suspend fun pendingForTarget(targetMessageId: String): List<PendingProviderEventEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPending(event: PendingProviderEventEntity): Long

    @Query("DELETE FROM pending_provider_events WHERE id = :id")
    suspend fun deletePending(id: String)
}

@Database(
    entities = [
        ThreadEntity::class,
        ParticipantEntity::class,
        MessageEntity::class,
        AttachmentEntity::class,
        ReactionEntity::class,
        MessageEditEntity::class,
        MessageDeletionEntity::class,
        AppliedProviderEventEntity::class,
        ContactEntity::class,
        ProviderAccountEntity::class,
        RemoteIdMappingEntity::class,
        SyncCursorEntity::class,
        PendingProviderEventEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(MessageTypeConverters::class)
abstract class UnifiedMessagesDatabase : RoomDatabase() {
    abstract fun threadDao(): ThreadDao
    abstract fun messageDao(): MessageDao
    abstract fun appliedProviderEventDao(): AppliedProviderEventDao
    abstract fun contactsDao(): ContactsDao
    abstract fun providerAccountDao(): ProviderAccountDao
    abstract fun providerSyncDao(): ProviderSyncDao
}

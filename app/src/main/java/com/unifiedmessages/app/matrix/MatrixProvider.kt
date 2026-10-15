package com.unifiedmessages.app.matrix

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.unifiedmessages.app.data.local.ProviderAccountEntity
import com.unifiedmessages.app.data.local.ContentType
import com.unifiedmessages.app.data.local.DeliveryStatus
import com.unifiedmessages.app.data.local.MessageEventReducer
import com.unifiedmessages.app.data.local.ProviderIds
import com.unifiedmessages.app.data.local.SyncCursorEntity
import com.unifiedmessages.app.data.local.UnifiedMessagesDatabase
import com.unifiedmessages.app.provider.Capabilities
import com.unifiedmessages.app.provider.MessageEvent
import com.unifiedmessages.app.provider.MessageDeleted
import com.unifiedmessages.app.provider.MessageEdited
import com.unifiedmessages.app.provider.MessageProvider
import com.unifiedmessages.app.provider.MessageReceived
import com.unifiedmessages.app.provider.MessageSent
import com.unifiedmessages.app.provider.ProviderMessage
import com.unifiedmessages.app.provider.ProviderParticipant
import com.unifiedmessages.app.provider.ProviderThread
import com.unifiedmessages.app.provider.SendCommandProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientBuilder
import org.matrix.rustcomponents.sdk.ClientSessionDelegate
import org.matrix.rustcomponents.sdk.Session
import org.matrix.rustcomponents.sdk.SqliteStoreBuilder
import org.matrix.rustcomponents.sdk.Timeline
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.TimelineDiff
import org.matrix.rustcomponents.sdk.TimelineListener
import org.matrix.rustcomponents.sdk.TimelineItem
import org.matrix.rustcomponents.sdk.EventOrTransactionId
import org.matrix.rustcomponents.sdk.TimelineItemContent
import org.matrix.rustcomponents.sdk.MsgLikeKind
import org.matrix.rustcomponents.sdk.SyncSettingsV2

data class MatrixLoginMethods(val password: Boolean, val sso: Boolean, val oidc: Boolean)

/** One native Matrix client and encrypted SDK store per signed-in account. */
@Singleton
class MatrixProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: UnifiedMessagesDatabase,
) : MessageProvider, SendCommandProvider {
    override val id: String = "matrix"
    // Room-level actions stay hidden until their commands and permission checks are wired.
    override val capabilities = Capabilities()
    private val emitted = MutableSharedFlow<MessageEvent>(extraBufferCapacity = 64)
    override val events: Flow<MessageEvent> = emitted.asSharedFlow()
    private val secrets = MatrixSecretStore(context)
    private val clients = ConcurrentHashMap<String, Client>()
    private val reducer = MessageEventReducer(database)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private data class RoomTimeline(val timeline: Timeline, val listener: TaskHandle)
    private val timelines = ConcurrentHashMap<String, RoomTimeline>()

    fun accounts(): Flow<List<ProviderAccountEntity>> = database.providerAccountDao().observe(id)

    /** Discovery runs against the entered homeserver; matrix.org is the default for the UI. */
    suspend fun offeredLoginMethods(homeserver: String = "matrix.org"): MatrixLoginMethods = withContext(Dispatchers.IO) {
        val client = ClientBuilder().serverNameOrHomeserverUrl(homeserver).inMemoryStore().build()
        try {
            val details = client.homeserverLoginDetails()
            MatrixLoginMethods(details.supportsPasswordLogin(), details.supportsSsoLogin(), details.supportsOauthLogin())
        } finally {
            client.close()
        }
    }

    /** The password is used once; only the refreshable SDK session is retained. */
    suspend fun loginPassword(homeserver: String, username: String, password: String): ProviderAccountEntity =
        withContext(Dispatchers.IO) {
            require(homeserver.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                "Homeserver, username, and password are required."
            }
            val reference = referenceFor(homeserver, username)
            val existing = secrets.read(reference)
            val storeKey = existing?.storePassphrase ?: randomStoreKey()
            val client = buildClient(homeserver, reference, storeKey)
            try {
                val methods = client.homeserverLoginDetails()
                check(methods.supportsPasswordLogin()) { "This homeserver does not offer password sign-in." }
                client.login(username, password, "UnifiedMessages", null)
                val session = client.session()
                secrets.write(reference, MatrixSecretStore.Record(session, storeKey))
                val account = ProviderAccountEntity(
                    id = "matrix:${session.userId}", providerId = id, remoteSelfId = session.userId,
                    connectionState = "CONNECTED", credentialRef = reference, homeserver = session.homeserverUrl,
                )
                database.providerAccountDao().upsert(account)
                clients.put(account.id, client)?.close()
                account
            } catch (error: Exception) {
                client.close()
                throw error
            }
        }

    /** Restores every locally saved login without asking for a password again. */
    suspend fun restoreAccounts(): List<ProviderAccountEntity> = withContext(Dispatchers.IO) {
        database.providerAccountDao().all(id).map { account ->
            if (clients.containsKey(account.id)) return@map account
            val reference = account.credentialRef
            val record = reference?.let(secrets::read)
            if (reference == null || record == null) {
                account.copy(connectionState = "SIGNED_OUT").also { database.providerAccountDao().upsert(it) }
            } else {
                val client = buildClient(record.session.homeserverUrl, reference, record.storePassphrase)
                try {
                    client.restoreSession(record.session)
                    clients[account.id] = client
                    account.copy(connectionState = "CONNECTED").also { database.providerAccountDao().upsert(it) }
                } catch (_: Exception) {
                    client.close()
                    account.copy(connectionState = "DISCONNECTED").also { database.providerAccountDao().upsert(it) }
                }
            }
        }
    }

    suspend fun logout(accountId: String) = withContext(Dispatchers.IO) {
        val account = database.providerAccountDao().get(accountId) ?: return@withContext
        timelines.keys.filter { it.startsWith("$accountId|") }.forEach { key ->
            timelines.remove(key)?.let { it.listener.close(); it.timeline.close() }
        }
        val client = clients.remove(accountId)
        try {
            client?.logout()
        } finally {
            client?.close()
            account.credentialRef?.let(secrets::remove)
            database.providerAccountDao().upsert(account.copy(connectionState = "SIGNED_OUT", credentialRef = null))
        }
    }

    /** SDK sync tokens live in its encrypted store; Room also records each completed batch cursor. */
    suspend fun syncOnce(accountId: String) = withContext(Dispatchers.IO) {
        val client = clients[accountId] ?: error("Sign in to this Matrix account first.")
        val response = client.syncOnceV2(SyncSettingsV2(timeoutMs = 30_000uL))
        response.rooms.joined.forEach { roomId ->
            val room = client.getRoom(roomId) ?: return@forEach
            val key = "$accountId|$roomId"
            if (!timelines.containsKey(key)) {
                val timeline = room.timeline()
                val title = room.displayName() ?: roomId
                val listener = timeline.addListener(object : TimelineListener {
                    override fun onUpdate(diffs: List<TimelineDiff>) {
                        val snapshots = diffs.flatMap { diff ->
                        when (diff) {
                            is TimelineDiff.Append -> diff.values
                            is TimelineDiff.PushBack -> listOf(diff.value)
                            is TimelineDiff.PushFront -> listOf(diff.value)
                            is TimelineDiff.Insert -> listOf(diff.value)
                            is TimelineDiff.Set -> listOf(diff.value)
                            is TimelineDiff.Reset -> diff.values
                            else -> emptyList()
                        }
                        }.mapNotNull { snapshot(it) }
                        scope.launch {
                            snapshots.forEach { ingest(accountId, roomId, title, it) }
                        }
                    }
                })
                val existing = timelines.putIfAbsent(key, RoomTimeline(timeline, listener))
                if (existing != null) { listener.close(); timeline.close() }
            }
        }
        database.providerSyncDao().upsertCursor(
            SyncCursorEntity("$accountId:sync", accountId, null, response.nextBatch, System.currentTimeMillis()),
        )
    }

    /** Paginates an open room; timeline events are deduplicated by account and event ID. */
    suspend fun backfill(accountId: String, remoteRoomId: String): Boolean = withContext(Dispatchers.IO) {
        val timeline = timelines["$accountId|$remoteRoomId"]?.timeline ?: return@withContext false
        timeline.paginateBackwards(50u.toUShort())
    }

    private data class Snapshot(
        val eventId: String, val sender: String, val timestamp: Long,
        val body: String?, val contentType: ContentType, val isOwn: Boolean,
        val edited: Boolean, val redacted: Boolean,
    )

    private fun snapshot(item: TimelineItem): Snapshot? {
        val event = item.asEvent() ?: return null
        val eventId = (event.eventOrTransactionId as? EventOrTransactionId.EventId)?.eventId ?: return null
        val content = event.content as? TimelineItemContent.MsgLike ?: return null
        val kind = content.content.kind
        val message = kind as? MsgLikeKind.Message
        if (message == null && kind !is MsgLikeKind.Redacted) return null
        val body = message?.content?.body
        val type = if (message?.content?.msgType is org.matrix.rustcomponents.sdk.MessageType.Text) {
            ContentType.TEXT
        } else ContentType.MEDIA
        return Snapshot(eventId, event.sender, event.timestamp.toLong(), body, type,
            event.isOwn, message?.content?.isEdited == true, kind is MsgLikeKind.Redacted)
    }

    private suspend fun ingest(accountId: String, roomId: String, title: String, item: Snapshot) {
        val localId = ProviderIds.message(ProviderIds.namespace(id, accountId), item.eventId)
        val existing = database.messageDao().getMessage(localId)
        if (existing == null) {
            val thread = ProviderThread(roomId, "MATRIX", title,
                participants = listOf(ProviderParticipant(item.sender, isSelf = item.isOwn)))
            val message = ProviderMessage(item.eventId, item.body, item.contentType, item.timestamp,
                senderAddress = if (item.isOwn) null else item.sender,
                deliveryStatus = if (item.isOwn) DeliveryStatus.SENT else DeliveryStatus.RECEIVED)
            val base = if (item.isOwn) MessageSent(id, "timeline:$roomId:${item.eventId}", item.timestamp,
                thread, message, accountId)
            else MessageReceived(id, "timeline:$roomId:${item.eventId}", item.timestamp,
                thread, message, accountId)
            if (reducer.apply(base)) emitted.emit(base)
        }
        val event: MessageEvent = when {
            item.redacted -> MessageDeleted(id, "redacted:$roomId:${item.eventId}", item.timestamp,
                item.eventId, bySender = true, accountId = accountId)
            item.edited -> MessageEdited(id, "edit:$roomId:${item.eventId}:${item.body.hashCode()}",
                item.timestamp, item.eventId, item.body, accountId)
            else -> return
        }
        if (item.redacted || existing != null && existing.body != item.body) {
            if (reducer.apply(event)) emitted.emit(event)
        }
    }

    override fun connectionIssue(): String? = "Select a connected Matrix account to send."

    override suspend fun send(threadId: String, body: String, attachments: List<Uri>) {
        error("Matrix sending is not connected to room timelines yet.")
    }

    override suspend fun retry(messageId: String) {
        error("Matrix retry is not available yet.")
    }

    private suspend fun buildClient(homeserver: String, reference: String, storeKey: String): Client {
        val directory = File(context.filesDir, "matrix/$reference").apply { mkdirs() }
        val dataPath = File(directory, "data").absolutePath
        val cachePath = File(directory, "cache").absolutePath
        val delegate = object : ClientSessionDelegate {
            override fun retrieveSessionFromKeychain(userId: String): Session =
                secrets.read(reference)?.session ?: error("Saved Matrix session is unavailable.")

            override fun saveSessionInKeychain(session: Session) {
                secrets.write(reference, MatrixSecretStore.Record(session, storeKey))
            }
        }
        val store = SqliteStoreBuilder(dataPath, cachePath)
            .key(Base64.decode(storeKey, Base64.NO_WRAP))
        return ClientBuilder()
            .serverNameOrHomeserverUrl(homeserver)
            .sqliteStore(store)
            .setSessionDelegate(delegate)
            .build()
    }

    private fun referenceFor(homeserver: String, username: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("${homeserver.trim().lowercase()}|${username.trim().lowercase()}".toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun randomStoreKey(): String = ByteArray(32).also(SecureRandom()::nextBytes)
        .let { Base64.encodeToString(it, Base64.NO_WRAP) }
}

package com.unifiedmessages.app.matrix

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.unifiedmessages.app.BuildConfig
import androidx.core.content.FileProvider
import android.util.Base64
import com.unifiedmessages.app.data.local.ProviderAccountEntity
import com.unifiedmessages.app.data.local.BridgeRoomEntity
import com.unifiedmessages.app.data.local.ThreadEntity
import com.unifiedmessages.app.data.local.RemoteIdMappingEntity
import com.unifiedmessages.app.data.local.ContentType
import com.unifiedmessages.app.data.local.DeliveryStatus
import com.unifiedmessages.app.data.local.MessageEventReducer
import com.unifiedmessages.app.data.local.ProviderIds
import com.unifiedmessages.app.data.local.SyncCursorEntity
import com.unifiedmessages.app.data.local.UnifiedMessagesDatabase
import com.unifiedmessages.app.provider.Capabilities
import com.unifiedmessages.app.provider.DeliveryUpdated
import com.unifiedmessages.app.provider.MessageEvent
import com.unifiedmessages.app.provider.MessageDeleted
import com.unifiedmessages.app.provider.MessageEdited
import com.unifiedmessages.app.provider.MessageProvider
import com.unifiedmessages.app.provider.MessageRoute
import com.unifiedmessages.app.provider.MessageReceived
import com.unifiedmessages.app.provider.MessageSent
import com.unifiedmessages.app.provider.ProviderMessage
import com.unifiedmessages.app.provider.ProviderAttachment
import com.unifiedmessages.app.provider.ProviderParticipant
import com.unifiedmessages.app.provider.ProviderThread
import com.unifiedmessages.app.provider.ReactionAdded
import com.unifiedmessages.app.provider.ReactionRemoved
import com.unifiedmessages.app.provider.ReadReceiptObserved
import com.unifiedmessages.app.provider.TypingChanged
import com.unifiedmessages.app.provider.SendCommandProvider
import com.unifiedmessages.app.provider.ReplyCommandProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID
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
import kotlinx.coroutines.delay
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
import org.matrix.rustcomponents.sdk.EventSendState
import org.matrix.rustcomponents.sdk.SendHandle
import org.matrix.rustcomponents.sdk.messageEventContentFromMarkdown
import org.matrix.rustcomponents.sdk.FileInfo
import org.matrix.rustcomponents.sdk.UploadParameters
import org.matrix.rustcomponents.sdk.UploadSource
import org.matrix.rustcomponents.sdk.SsoHandler
import org.matrix.rustcomponents.sdk.OAuthConfiguration
import org.matrix.rustcomponents.sdk.MessageType
import org.matrix.rustcomponents.sdk.ReceiptType
import org.matrix.rustcomponents.sdk.TypingNotificationsListener
import org.matrix.rustcomponents.sdk.ClientDelegate
import uniffi.matrix_sdk_common.BackgroundTaskFailureReason
import org.matrix.rustcomponents.sdk.MediaSource
import uniffi.ruma_events.MessageLikeEventType
import uniffi.ruma_events.stateEventTypeFromString

data class MatrixLoginMethods(val password: Boolean, val sso: Boolean, val oidc: Boolean)
data class MatrixSecurityStatus(val recovery: String, val verification: String)
data class MatrixMessageActions(val react: Boolean = false, val edit: Boolean = false,
    val unsend: Boolean = false, val reply: Boolean = false) {
    val any: Boolean get() = react || edit || unsend || reply
}

/** One native Matrix client and encrypted SDK store per signed-in account. */
@Singleton
class MatrixProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: UnifiedMessagesDatabase,
) : MessageProvider, ReplyCommandProvider {
    override val id: String = "matrix"
    // Room-level actions stay hidden until their commands and permission checks are wired.
    override val capabilities = Capabilities(
        supportsReactions = true, supportsEdit = true, supportsUnsend = true,
        supportsReadReceipts = true, supportsAppMessages = false,
    )
    private val emitted = MutableSharedFlow<MessageEvent>(extraBufferCapacity = 64)
    override val events: Flow<MessageEvent> = emitted.asSharedFlow()
    private val secrets = MatrixSecretStore(context)
    private val clients = ConcurrentHashMap<String, Client>()
    private val authDelegates = ConcurrentHashMap<String, TaskHandle>()
    private val reducer = MessageEventReducer(database)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private data class RoomPermissions(val send: Boolean, val react: Boolean, val redactOwn: Boolean)
    private data class RoomTimeline(val timeline: Timeline, val listener: TaskHandle,
        val typingListener: TaskHandle,
        val permissions: RoomPermissions)
    private val timelines = ConcurrentHashMap<String, RoomTimeline>()
    private data class MessageFlags(val isOwn: Boolean, val editable: Boolean, val canReply: Boolean)
    private val messageFlags = ConcurrentHashMap<String, MessageFlags>()
    private val sendHandles = ConcurrentHashMap<String, SendHandle>()
    private data class PendingBrowserLogin(
        val client: Client, val handler: SsoHandler?, val reference: String, val storeKey: String,
    )
    @Volatile private var pendingBrowserLogin: PendingBrowserLogin? = null

    fun accounts(): Flow<List<ProviderAccountEntity>> = database.providerAccountDao().observe(id)

    suspend fun setBridgeBot(accountId: String, bridgeBotId: String) = withContext(Dispatchers.IO) {
        val account = database.providerAccountDao().get(accountId)
            ?: error("Matrix account no longer exists.")
        val bot = bridgeBotId.trim().ifBlank { null }
        require(bot == null || (bot.startsWith("@") && ':' in bot)) {
            "Enter a full Matrix bot ID, such as @bridge:example.org."
        }
        if (account.bridgeBotId != bot) {
            database.providerAccountDao().upsert(account.copy(bridgeBotId = bot))
            database.bridgeRoomDao().clearAccount(accountId)
            database.threadDao().resetMatrixNetworks(accountId)
        }
    }

    suspend fun previousSyncAt(accountId: String): Long? =
        database.providerSyncDao().cursor("$accountId:sync")?.updatedAt

    fun securityStatus(accountId: String): MatrixSecurityStatus {
        val client = clients[accountId] ?: return MatrixSecurityStatus("UNKNOWN", "UNKNOWN")
        return client.encryption().use { encryption ->
            MatrixSecurityStatus(encryption.recoveryState().name, encryption.verificationState().name)
        }
    }

    suspend fun recoverKeys(accountId: String, recoveryKey: String) = withContext(Dispatchers.IO) {
        require(recoveryKey.isNotBlank()) { "Enter the recovery key from your other Matrix app." }
        val client = clients[accountId] ?: error("Reconnect this Matrix account first.")
        client.encryption().use { it.recover(recoveryKey.trim()) }
    }

    /** Discovery runs against the entered homeserver; matrix.org is the default for the UI. */
    suspend fun offeredLoginMethods(homeserver: String = "matrix.org"): MatrixLoginMethods = withContext(Dispatchers.IO) {
        MatrixPlatformStartup.requireInitialized()
        val client = ClientBuilder().serverNameOrHomeserverUrl(homeserver).inMemoryStore().build()
        try {
            val details = client.homeserverLoginDetails()
            MatrixLoginMethods(details.supportsPasswordLogin(), details.supportsSsoLogin(), details.supportsOauthLogin())
        } finally {
            client.close()
        }
    }

    /** Debug-only comparison of SDK reqwest/rustls and Android's HTTPS stack on one fixed URL. */
    suspend fun diagnoseNetwork(): String = withContext(Dispatchers.IO) {
        check(BuildConfig.DEBUG) { "Network diagnostics are available only in debug builds." }
        MatrixPlatformStartup.requireInitialized()
        val target = "https://matrix.org/.well-known/matrix/client"
        val sdk = try {
            // An explicit homeserver URL avoids discovery during setup, so getUrl tests the exact target.
            val client = ClientBuilder().homeserverUrl("https://matrix-client.matrix.org")
                .inMemoryStore().build()
            try { "SDK: HTTP GET succeeded (${client.getUrl(target).size} bytes)" }
            finally { client.close() }
        } catch (error: Exception) {
            "SDK: HTTP GET failed\n${MatrixErrorDiagnostics.format(error)}"
        }
        val android = try {
            val connection = (URL(target).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
            }
            try { "Android: HTTP GET returned ${connection.responseCode}" }
            finally { connection.disconnect() }
        } catch (error: Exception) {
            "Android: HTTP GET failed\n${MatrixErrorDiagnostics.format(error)}"
        }
        Log.i("MatrixDebug", sdk)
        Log.i("MatrixDebug", android)
        "$sdk\n$android"
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
                attachAuthDelegate(account.id, client)
                account
            } catch (error: Exception) {
                client.close()
                throw error
            }
        }

    /** Opens the homeserver's SSO page in the system browser; the app receives a deep-link callback. */
    suspend fun beginSso(homeserver: String): String = withContext(Dispatchers.IO) {
        require(homeserver.isNotBlank()) { "Enter a homeserver first." }
        pendingBrowserLogin?.let { it.handler?.close(); it.client.close() }
        pendingBrowserLogin = null
        val reference = "sso-${UUID.randomUUID()}"
        val storeKey = randomStoreKey()
        val client = buildClient(homeserver.trim(), reference, storeKey)
        try {
            check(client.homeserverLoginDetails().supportsSsoLogin()) {
                "This homeserver does not offer SSO sign-in."
            }
            val handler = client.startSsoLogin("unifiedmessages://matrix-auth", null)
            pendingBrowserLogin = PendingBrowserLogin(client, handler, reference, storeKey)
            handler.url()
        } catch (error: Exception) {
            client.close()
            throw error
        }
    }

    /** Uses the SDK's dynamic OIDC client registration and PKCE flow when offered. */
    suspend fun beginOidc(homeserver: String): String = withContext(Dispatchers.IO) {
        require(homeserver.isNotBlank()) { "Enter a homeserver first." }
        pendingBrowserLogin?.let { it.handler?.close(); it.client.close() }
        pendingBrowserLogin = null
        val reference = "oidc-${UUID.randomUUID()}"
        val storeKey = randomStoreKey()
        val client = buildClient(homeserver.trim(), reference, storeKey)
        try {
            check(client.homeserverLoginDetails().supportsOauthLogin()) {
                "This homeserver does not offer OIDC sign-in."
            }
            val config = OAuthConfiguration(
                "UnifiedMessages", "unifiedmessages://matrix-auth",
                "https://github.com/SpintoCreate/RCS-IMESSAGE", null, null, null, emptyMap(),
            )
            val authorization = client.urlForOauth(config, null, null, null, null)
            val url = authorization.loginUrl()
            authorization.close()
            pendingBrowserLogin = PendingBrowserLogin(client, null, reference, storeKey)
            url
        } catch (error: Exception) {
            client.close()
            throw error
        }
    }

    suspend fun finishBrowserLogin(callbackUrl: String): ProviderAccountEntity = withContext(Dispatchers.IO) {
        val pending = pendingBrowserLogin ?: error("Sign-in expired. Start browser sign-in again.")
        pendingBrowserLogin = null
        try {
            if (pending.handler != null) pending.handler.finish(callbackUrl)
            else pending.client.loginWithOauthCallback(callbackUrl)
            val session = pending.client.session()
            secrets.write(pending.reference, MatrixSecretStore.Record(session, pending.storeKey))
            val account = ProviderAccountEntity(
                id = "matrix:${session.userId}", providerId = id, remoteSelfId = session.userId,
                connectionState = "CONNECTED", credentialRef = pending.reference,
                homeserver = session.homeserverUrl,
            )
            database.providerAccountDao().upsert(account)
            clients.put(account.id, pending.client)?.close()
            attachAuthDelegate(account.id, pending.client)
            account
        } catch (error: Exception) {
            pending.client.close()
            throw error
        } finally {
            pending.handler?.close()
        }
    }

    /** Restores every locally saved login without asking for a password again. */
    suspend fun restoreAccounts(): List<ProviderAccountEntity> = withContext(Dispatchers.IO) {
        database.providerAccountDao().all(id).map { account ->
            if (clients.containsKey(account.id)) return@map account
            if (account.connectionState == "AUTH_EXPIRED") return@map account
            val reference = account.credentialRef
            val record = reference?.let(secrets::read)
            if (reference == null || record == null) {
                account.copy(connectionState = "SIGNED_OUT").also { database.providerAccountDao().upsert(it) }
            } else {
                val client = buildClient(record.session.homeserverUrl, reference, record.storePassphrase)
                try {
                    client.restoreSession(record.session)
                    clients[account.id] = client
                    attachAuthDelegate(account.id, client)
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
        authDelegates.remove(accountId)?.close()
        timelines.keys.filter { it.startsWith("$accountId|") }.forEach { key ->
            timelines.remove(key)?.let { it.listener.close(); it.typingListener.close(); it.timeline.close() }
        }
        sendHandles.keys.filter { it.startsWith("$accountId|") }.forEach { key ->
            sendHandles.remove(key)?.close()
        }
        messageFlags.clear()
        val client = clients.remove(accountId)
        try {
            client?.logout()
        } finally {
            client?.close()
            account.credentialRef?.let(secrets::remove)
            database.providerAccountDao().upsert(account.copy(connectionState = "SIGNED_OUT", credentialRef = null))
        }
    }

    private fun attachAuthDelegate(accountId: String, client: Client) {
        val handle = client.setDelegate(object : ClientDelegate {
            override fun didReceiveAuthError(isSoftLogout: Boolean) {
                scope.launch {
                    val account = database.providerAccountDao().get(accountId) ?: return@launch
                    database.providerAccountDao().upsert(account.copy(connectionState = "AUTH_EXPIRED"))
                    timelines.keys.filter { it.startsWith("$accountId|") }.forEach { key ->
                        timelines.remove(key)?.let { it.listener.close(); it.typingListener.close(); it.timeline.close() }
                    }
                    sendHandles.keys.filter { it.startsWith("$accountId|") }.forEach { key ->
                        sendHandles.remove(key)?.close()
                    }
                    authDelegates.remove(accountId)?.close()
                    clients.remove(accountId)?.close()
                }
            }

            override fun onBackgroundTaskErrorReport(
                taskName: String, error: BackgroundTaskFailureReason,
            ) = Unit
        })
        if (handle != null) authDelegates.put(accountId, handle)?.close()
    }

    /** SDK sync tokens live in its encrypted store; Room also records each completed batch cursor. */
    suspend fun syncOnce(accountId: String) = withContext(Dispatchers.IO) {
        val client = clients[accountId] ?: error("Sign in to this Matrix account first.")
        val response = client.syncOnceV2(SyncSettingsV2(timeoutMs = 30_000uL))
        response.rooms.invited.forEach { roomId ->
            val room = client.getRoom(roomId) ?: return@forEach
            val title = room.displayName() ?: roomId
            val threadId = ProviderIds.thread(ProviderIds.namespace(id, accountId), roomId)
            val existing = database.threadDao().getThread(threadId)
            if (existing == null) {
                database.threadDao().insert(ThreadEntity(threadId, id, roomId, "MATRIX", title,
                    title.take(2).uppercase(), updatedAt = System.currentTimeMillis(),
                    accountId = accountId, networkType = "MATRIX", membership = "INVITED"))
                database.providerSyncDao().insertMapping(RemoteIdMappingEntity(
                    ProviderIds.mapping(accountId, "thread", roomId), accountId, "thread", roomId, threadId))
            } else database.threadDao().update(existing.copy(membership = "INVITED"))
        }
        response.rooms.left.forEach { roomId ->
            val threadId = ProviderIds.thread(ProviderIds.namespace(id, accountId), roomId)
            database.threadDao().setMatrixMembership(threadId, "LEFT")
            timelines.remove("$accountId|$roomId")?.let {
                it.listener.close(); it.typingListener.close(); it.timeline.close()
            }
        }
        response.rooms.joined.forEach { roomId ->
            val room = client.getRoom(roomId) ?: return@forEach
            val key = "$accountId|$roomId"
            val title = room.displayName() ?: roomId
            val threadId = ProviderIds.thread(ProviderIds.namespace(id, accountId), roomId)
            if (database.threadDao().getThread(threadId) == null) {
                database.threadDao().insert(ThreadEntity(threadId, id, roomId, "MATRIX", title,
                    title.take(2).uppercase(), updatedAt = System.currentTimeMillis(),
                    accountId = accountId, networkType = "MATRIX"))
                database.providerSyncDao().insertMapping(RemoteIdMappingEntity(
                    ProviderIds.mapping(accountId, "thread", roomId), accountId, "thread", roomId, threadId))
            } else database.threadDao().setMatrixMembership(threadId, "JOINED")
            try { refreshBridgeState(accountId, roomId, room) }
            catch (error: Exception) { Log.w("MatrixProvider", "Could not check bridge room state", error) }
            if (!timelines.containsKey(key)) {
                val timeline = room.timeline()
                val permissions = try {
                    room.getPowerLevels().use { levels ->
                        RoomPermissions(
                            levels.canOwnUserSendMessage(MessageLikeEventType.Message) ||
                                levels.canOwnUserSendMessage(MessageLikeEventType.Encrypted),
                            levels.canOwnUserSendMessage(MessageLikeEventType.Reaction),
                            levels.canOwnUserRedactOwn(),
                        )
                    }
                } catch (_: Exception) { RoomPermissions(false, false, false) }
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
                            snapshots.forEach { item ->
                                try { ingest(accountId, roomId, title, item) }
                                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                catch (_: Exception) {
                                    Log.w("MatrixProvider", "Could not import a timeline event")
                                }
                            }
                        }
                    }
                })
                val priorTyping = AtomicReference(emptySet<String>())
                val typingListener = room.subscribeToTypingNotifications(object : TypingNotificationsListener {
                    override fun call(typingUserIds: List<String>) {
                        val current = typingUserIds.toSet() - accountId.removePrefix("matrix:")
                        val old = priorTyping.getAndSet(current)
                        val now = System.currentTimeMillis()
                        scope.launch {
                            ((current - old).map { it to true } + (old - current).map { it to false })
                                .forEach { (actor, active) ->
                                    val changed = TypingChanged(id,
                                        "typing:$roomId:$actor:$now:$active", now,
                                        roomId, actor, active, accountId)
                                    if (reducer.apply(changed)) emitted.emit(changed)
                                }
                        }
                    }
                })
                val existing = timelines.putIfAbsent(key,
                    RoomTimeline(timeline, listener, typingListener, permissions))
                if (existing != null) { listener.close(); typingListener.close(); timeline.close() }
            }
        }
        database.providerSyncDao().upsertCursor(
            SyncCursorEntity("$accountId:sync", accountId, null, response.nextBatch, System.currentTimeMillis()),
        )
    }

    suspend fun joinRoom(threadId: String) = withContext(Dispatchers.IO) {
        val thread = database.threadDao().getThread(threadId) ?: error("Room no longer exists.")
        check(thread.providerId == id && thread.membership == "INVITED") {
            "This is not an active Matrix invitation."
        }
        val accountId = thread.accountId ?: error("This room has no Matrix account.")
        val room = clients[accountId]?.getRoom(thread.providerThreadId)
            ?: error("Reconnect this Matrix account to join.")
        room.use { it.join() }
        database.threadDao().setMatrixMembership(threadId, "JOINED")
    }

    private suspend fun refreshBridgeState(accountId: String, roomId: String, room: org.matrix.rustcomponents.sdk.Room) {
        val threadId = ProviderIds.thread(ProviderIds.namespace(id, accountId), roomId)
        val bot = database.providerAccountDao().get(accountId)?.bridgeBotId
        if (bot.isNullOrBlank()) return
        val candidates = MatrixBridgeDetector.stateEventTypes.flatMap { type ->
            room.stateEvents(stateEventTypeFromString(type)).mapNotNull { event ->
                val eventId = event.eventId ?: return@mapNotNull null
                MatrixBridgeDetector.detect(type, eventId, event.sender, event.contentJson, bot)
                    ?.let { (event.timestamp?.toLong() ?: 0L) to it }
            }
        }
        val match = candidates.maxByOrNull { it.first }?.second
        if (match == null) {
            if (database.bridgeRoomDao().get(threadId) != null) {
                database.bridgeRoomDao().clearThread(threadId)
                database.threadDao().setMatrixNetwork(threadId, "MATRIX")
            }
            return
        }
        database.bridgeRoomDao().upsert(BridgeRoomEntity(threadId, accountId, match.stateEventId,
            match.bridgeBotId, match.protocolId, match.remoteChannelId,
            match.networkType.name, System.currentTimeMillis()))
        database.threadDao().setMatrixNetwork(threadId, match.networkType.name)
    }

    /** Paginates an open room; timeline events are deduplicated by account and event ID. */
    suspend fun backfill(accountId: String, remoteRoomId: String): Boolean = withContext(Dispatchers.IO) {
        repeat(60) {
            val timeline = timelines["$accountId|$remoteRoomId"]?.timeline
            if (timeline != null) return@withContext timeline.paginateBackwards(50u.toUShort())
            delay(500)
        }
        false
    }

    private data class Snapshot(
        val eventId: String, val sender: String, val timestamp: Long,
        val body: String?, val contentType: ContentType, val isOwn: Boolean,
        val edited: Boolean, val redacted: Boolean,
        val transactionId: String? = null,
        val remoteEventId: String? = null,
        val deliveryStatus: DeliveryStatus? = null,
        val sendHandle: SendHandle? = null,
        val media: MediaSnapshot? = null,
        val reactions: List<MatrixObservedReaction> = emptyList(),
        val editable: Boolean = false,
        val canReply: Boolean = false,
        val replyToEventId: String? = null,
        val readReceipts: List<ReceiptSnapshot> = emptyList(),
    )

    private data class MediaSnapshot(val sourceJson: String, val filename: String, val mimeType: String)
    private data class ReceiptSnapshot(val actor: String, val timestamp: Long)

    private fun snapshot(item: TimelineItem): Snapshot? {
        val event = item.asEvent() ?: return null
        val transactionId = (event.eventOrTransactionId as? EventOrTransactionId.TransactionId)?.transactionId
        val eventId = (event.eventOrTransactionId as? EventOrTransactionId.EventId)?.eventId
            ?: transactionId?.let { "txn:$it" } ?: return null
        val content = event.content as? TimelineItemContent.MsgLike ?: return null
        val kind = content.content.kind
        val message = kind as? MsgLikeKind.Message
        if (message == null && kind !is MsgLikeKind.Redacted) return null
        val body = message?.content?.body
        val media = when (val msgType = message?.content?.msgType) {
            is MessageType.Image -> MediaSnapshot(msgType.content.source.toJson(), msgType.content.filename,
                msgType.content.info?.mimetype ?: "image/*")
            is MessageType.Video -> MediaSnapshot(msgType.content.source.toJson(), msgType.content.filename,
                msgType.content.info?.mimetype ?: "video/*")
            is MessageType.Audio -> MediaSnapshot(msgType.content.source.toJson(), msgType.content.filename,
                msgType.content.info?.mimetype ?: "audio/*")
            is MessageType.File -> MediaSnapshot(msgType.content.source.toJson(), msgType.content.filename,
                msgType.content.info?.mimetype ?: "application/octet-stream")
            else -> null
        }
        val type = if (media == null) ContentType.TEXT else ContentType.MEDIA
        val sendState = event.localSendState
        val deliveryStatus = when (sendState) {
            is EventSendState.NotSentYet -> DeliveryStatus.PENDING
            is EventSendState.SendingFailed -> DeliveryStatus.FAILED
            is EventSendState.Sent -> DeliveryStatus.SENT
            else -> null
        }
        return Snapshot(eventId, event.sender, event.timestamp.toLong(), body, type,
            event.isOwn, message?.content?.isEdited == true, kind is MsgLikeKind.Redacted,
            transactionId, (sendState as? EventSendState.Sent)?.eventId,
            deliveryStatus, if (transactionId != null) event.lazyProvider.getSendHandle() else null,
            media, event.reactions.flatMap { reaction ->
                reaction.senders.map { sender -> MatrixObservedReaction(sender.senderId, reaction.key,
                    sender.timestamp.toLong()) }
            }, event.isEditable, event.canBeRepliedTo, content.content.inReplyTo?.eventId(),
            event.readReceipts.map { (actor, receipt) ->
                ReceiptSnapshot(actor, receipt.timestamp?.toLong() ?: event.timestamp.toLong())
            })
    }

    private suspend fun ingest(accountId: String, roomId: String, title: String, item: Snapshot) {
        val localId = database.providerSyncDao().mapping(accountId, "message", item.eventId)?.localId
            ?: ProviderIds.message(ProviderIds.namespace(id, accountId), item.eventId)
        val existing = database.messageDao().getMessage(localId)
        messageFlags[localId] = MessageFlags(item.isOwn, item.editable, item.canReply)
        if (item.transactionId != null && item.sendHandle != null) {
            sendHandles.put("$accountId|${item.eventId}", item.sendHandle)?.close()
        }
        val thread = ProviderThread(roomId, "MATRIX", title,
            participants = listOf(ProviderParticipant(item.sender, isSelf = item.isOwn)))
        val message = ProviderMessage(item.eventId, item.body, item.contentType, item.timestamp,
            senderAddress = if (item.isOwn) null else item.sender,
            deliveryStatus = item.deliveryStatus ?: if (item.isOwn) DeliveryStatus.SENT else DeliveryStatus.RECEIVED,
            attachments = item.media?.let { media -> listOf(ProviderAttachment(
                "media:0", MatrixMediaRef(accountId, media.sourceJson).encode(),
                media.mimeType, media.filename,
            )) }.orEmpty(), replyToProviderMessageId = item.replyToEventId)
        val base = if (item.isOwn) MessageSent(id, "timeline:$roomId:${item.eventId}", item.timestamp,
            thread, message, accountId)
        else MessageReceived(id, "timeline:$roomId:${item.eventId}", item.timestamp,
            thread, message, accountId)
        if (reducer.apply(base)) emitted.emit(base)
        if (item.remoteEventId != null && item.transactionId != null) {
            reducer.bindRemoteMessageId(accountId, id, item.remoteEventId, localId)
        }
        if (item.deliveryStatus != null && item.transactionId != null) {
            val statusEvent = DeliveryUpdated(id, "state:$roomId:${item.eventId}:${item.deliveryStatus}",
                System.currentTimeMillis(), item.eventId, item.deliveryStatus, accountId)
            if (reducer.apply(statusEvent)) emitted.emit(statusEvent)
        }
        val active = database.messageDao().getActiveReactions(localId)
        matrixReactionChanges(active, item.reactions).forEach { change ->
            when (change) {
            is MatrixReactionChange.Add -> {
                val reaction = change.observed
                val reactionId = "${reaction.actor}:${reaction.emoji}"
                val added = ReactionAdded(id,
                    "reaction:add:$roomId:${item.eventId}:$reactionId:${reaction.timestamp}",
                    reaction.timestamp, item.eventId, reactionId, reaction.actor, reaction.emoji, accountId)
                if (reducer.apply(added)) emitted.emit(added)
            }
            is MatrixReactionChange.Remove -> {
                val old = change.active
                val removed = ReactionRemoved(id, "reaction:remove:$roomId:${old.id}",
                    System.currentTimeMillis(), item.eventId, old.providerReactionId, accountId)
                if (reducer.apply(removed)) emitted.emit(removed)
            }
            }
        }
        item.readReceipts.forEach { receipt ->
            val observed = ReadReceiptObserved(id,
                "receipt:$roomId:${item.eventId}:${receipt.actor}:${receipt.timestamp}",
                receipt.timestamp, item.eventId, receipt.actor, accountId)
            if (reducer.apply(observed)) emitted.emit(observed)
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

    override fun connectionIssue(): String? =
        if (clients.isEmpty()) "Sign in to a Matrix account to send." else null

    override fun connectionIssue(accountId: String): String? = when {
        !clients.containsKey(accountId) -> "Reconnect this Matrix account to send."
        timelines.keys.none { it.startsWith("$accountId|") } -> "Wait for this Matrix account to sync."
        else -> null
    }

    override fun connectionIssue(route: MessageRoute): String? {
        if (route.membership != "JOINED") return if (route.membership == "INVITED")
            "Join this Matrix room before sending." else "You are not in this Matrix room."
        connectionIssue(route.accountId)?.let { return it }
        val room = route.remoteThreadId?.let { timelines["${route.accountId}|$it"] }
            ?: return "Wait for this Matrix room to sync before sending."
        return if (room.permissions.send) null else "You cannot send messages in this Matrix room."
    }

    suspend fun availableActions(messageId: String): MatrixMessageActions {
        val message = database.messageDao().getMessage(messageId) ?: return MatrixMessageActions()
        val thread = database.threadDao().getThread(message.threadId) ?: return MatrixMessageActions()
        if (thread.providerId != id || thread.networkType != "MATRIX" ||
            database.bridgeRoomDao().get(thread.id) != null || message.isDeleted) {
            return MatrixMessageActions()
        }
        val accountId = thread.accountId ?: return MatrixMessageActions()
        val permissions = timelines["$accountId|${thread.providerThreadId}"]?.permissions
            ?: return MatrixMessageActions()
        val flags = messageFlags[messageId] ?: return MatrixMessageActions()
        val remoteId = remoteEventId(accountId, messageId, message.providerMessageId)
        if (remoteId == null) return MatrixMessageActions()
        return MatrixMessageActions(
            react = permissions.react,
            edit = flags.isOwn && flags.editable && permissions.send,
            unsend = flags.isOwn && permissions.redactOwn,
            reply = flags.canReply && permissions.send,
        )
    }

    private suspend fun remoteEventId(accountId: String, localId: String, providerMessageId: String): String? =
        providerMessageId.takeIf { it.startsWith("$") }
            ?: database.providerSyncDao().remoteEventIdForMessage(accountId, localId)

    private suspend fun actionTarget(messageId: String): Triple<String, String, String> {
        val message = database.messageDao().getMessage(messageId) ?: error("Message no longer exists.")
        val thread = database.threadDao().getThread(message.threadId) ?: error("Conversation no longer exists.")
        val accountId = thread.accountId ?: error("This Matrix conversation has no account.")
        val eventId = remoteEventId(accountId, messageId, message.providerMessageId)
            ?: error("Wait for this Matrix message to finish sending.")
        return Triple(accountId, thread.providerThreadId, eventId)
    }

    suspend fun toggleReaction(messageId: String, emoji: String) {
        require(emoji.isNotBlank()) { "Choose a reaction first." }
        check(availableActions(messageId).react) { "Reactions are unavailable in this room." }
        val (accountId, roomId, eventId) = actionTarget(messageId)
        val timeline = timelines["$accountId|$roomId"]?.timeline ?: error("Open this Matrix room first.")
        timeline.toggleReaction(EventOrTransactionId.EventId(eventId), emoji)
    }

    suspend fun editMessage(messageId: String, newBody: String) {
        require(newBody.isNotBlank()) { "An edit cannot be empty." }
        check(availableActions(messageId).edit) { "Editing is unavailable for this message." }
        val (accountId, roomId, eventId) = actionTarget(messageId)
        val room = clients[accountId]?.getRoom(roomId) ?: error("Reconnect this Matrix account first.")
        room.use { it.edit(eventId, messageEventContentFromMarkdown(newBody.trim())) }
    }

    suspend fun unsendMessage(messageId: String) {
        check(availableActions(messageId).unsend) { "Unsend is unavailable for this message." }
        val (accountId, roomId, eventId) = actionTarget(messageId)
        val room = clients[accountId]?.getRoom(roomId) ?: error("Reconnect this Matrix account first.")
        room.use { it.redact(eventId, null) }
    }

    override suspend fun reply(threadId: String, repliedToMessageId: String, body: String) {
        require(body.isNotBlank()) { "Write a reply first." }
        check(availableActions(repliedToMessageId).reply) { "Replies are unavailable for this message." }
        val thread = database.threadDao().getThread(threadId) ?: error("Conversation no longer exists.")
        val (accountId, remoteRoomId, eventId) = actionTarget(repliedToMessageId)
        check(thread.accountId == accountId && thread.providerThreadId == remoteRoomId) {
            "The replied-to message is not in this conversation."
        }
        val timeline = timelines["$accountId|$remoteRoomId"]?.timeline
            ?: error("Open this Matrix room first.")
        timeline.sendReply(messageEventContentFromMarkdown(body.trim()), eventId).close()
    }

    suspend fun markRead(threadId: String) {
        val thread = database.threadDao().getThread(threadId) ?: return
        val accountId = thread.accountId ?: return
        timelines["$accountId|${thread.providerThreadId}"]?.timeline?.markAsRead(ReceiptType.READ)
    }

    suspend fun setTyping(threadId: String, typing: Boolean) {
        val thread = database.threadDao().getThread(threadId) ?: return
        val accountId = thread.accountId ?: return
        val room = clients[accountId]?.getRoom(thread.providerThreadId) ?: return
        room.use { it.typingNotice(typing) }
    }

    override suspend fun send(threadId: String, body: String, attachments: List<Uri>) {
        require(body.isNotBlank() || attachments.isNotEmpty()) { "Write a message or attach a file." }
        val thread = database.threadDao().getThread(threadId) ?: error("Conversation no longer exists.")
        val accountId = thread.accountId ?: error("This Matrix conversation has no account.")
        val timeline = timelines["$accountId|${thread.providerThreadId}"]?.timeline
            ?: error("Wait for this Matrix room to sync before sending.")
        if (attachments.isEmpty()) {
            timeline.send(messageEventContentFromMarkdown(body)).close()
        } else {
            attachments.forEachIndexed { index, uri ->
                val filename = attachmentName(uri)
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val bytes = readAttachment(uri)
                val parameters = UploadParameters(
                    UploadSource.Data(bytes, filename),
                    body.takeIf { index == 0 && it.isNotBlank() }, null, null, null,
                )
                timeline.sendFile(parameters, FileInfo(mimeType, bytes.size.toULong(), null, null)).use {
                    it.join()
                }
            }
        }
    }

    private fun attachmentName(uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.takeIf(String::isNotBlank) else null
            } ?: "attachment"

    private fun readAttachment(uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open the selected file.")
        return input.use { source ->
            val output = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val count = source.read(chunk)
                if (count < 0) break
                check(output.size() + count <= 25 * 1024 * 1024) {
                    "This file is larger than the 25 MB upload limit."
                }
                output.write(chunk, 0, count)
            }
            output.toByteArray()
        }
    }

    /** Downloads and decrypts through the SDK, then gives an Android viewer a temporary local URI. */
    suspend fun openMedia(reference: MatrixMediaRef, filename: String?, mimeType: String): Uri =
        withContext(Dispatchers.IO) {
            val client = clients[reference.accountId] ?: error("Reconnect this Matrix account to open media.")
            val source = MediaSource.fromJson(reference.sourceJson)
            try {
                val directory = File(context.cacheDir, "matrix_media").apply { mkdirs() }
                val target = File(directory, "${UUID.randomUUID()}-${filename.orEmpty().takeLast(80).replace('/', '_')}")
                client.getMediaFile(source, filename, mimeType, true, directory.absolutePath).use { handle ->
                    if (!handle.persist(target.absolutePath)) {
                        File(handle.path()).copyTo(target, overwrite = true)
                    }
                }
                FileProvider.getUriForFile(context, "${context.packageName}.media", target)
            } finally {
                source.close()
            }
        }

    override suspend fun retry(messageId: String) {
        val message = database.messageDao().getMessage(messageId) ?: error("Message no longer exists.")
        require(message.deliveryStatus == DeliveryStatus.FAILED && message.providerMessageId.startsWith("txn:")) {
            "Only a failed Matrix message can be retried."
        }
        val thread = database.threadDao().getThread(message.threadId) ?: error("Conversation no longer exists.")
        val accountId = thread.accountId ?: error("This Matrix conversation has no account.")
        val handle = sendHandles["$accountId|${message.providerMessageId}"]
            ?: error("Reopen this room and let it sync before retrying.")
        database.messageDao().update(message.copy(deliveryStatus = DeliveryStatus.PENDING))
        try { handle.tryResend() }
        catch (error: Exception) {
            database.messageDao().update(message)
            throw error
        }
    }

    private suspend fun buildClient(homeserver: String, reference: String, storeKey: String): Client {
        MatrixPlatformStartup.requireInitialized()
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

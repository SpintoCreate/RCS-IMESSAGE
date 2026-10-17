package com.unifiedmessages.app.provider

import android.net.Uri
import com.unifiedmessages.app.data.local.UnifiedMessagesDatabase
import com.unifiedmessages.app.sms.PhoneNumbers
import com.unifiedmessages.app.sms.SmsProvider
import com.unifiedmessages.app.matrix.MatrixProvider
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Transport and visible network are separate even when one provider currently supplies both. */
enum class NetworkType(val label: String) {
    SMS("SMS"), MMS("MMS"), IMESSAGE("iMessage"), RCS("RCS"), MATRIX("Matrix"),
    WHATSAPP("WhatsApp"), TELEGRAM("Telegram"), GOOGLE_MESSAGES("Google Messages"), UNKNOWN("Unknown");

    companion object {
        fun fromSource(source: String): NetworkType = entries.firstOrNull {
            it.name.equals(source, ignoreCase = true)
        } ?: UNKNOWN
    }
}

data class MessageRoute(
    val threadId: String,
    val accountId: String,
    val providerId: String,
    val networkType: NetworkType,
    val remoteThreadId: String? = null,
    val membership: String = "JOINED",
) {
    val label: String get() = networkType.label
}

data class SendAvailability(val routeLabel: String, val disabledReason: String? = null) {
    val canSend: Boolean get() = disabledReason == null
}

/** Step 2 will persist account/network IDs. Existing threads currently derive a stable local account. */
interface SendRouteLookup {
    suspend fun forThread(threadId: String): MessageRoute?
    suspend fun forMessage(messageId: String): MessageRoute?
}

private class RoomSendRouteLookup(private val database: UnifiedMessagesDatabase) : SendRouteLookup {
    override suspend fun forThread(threadId: String): MessageRoute? =
        database.threadDao().getThread(threadId)?.let { thread ->
            MessageRoute(
                thread.id,
                thread.accountId ?: "${thread.providerId}:local",
                thread.providerId,
                NetworkType.fromSource(thread.networkType ?: thread.source),
                thread.providerThreadId,
                thread.membership,
            )
        }

    override suspend fun forMessage(messageId: String): MessageRoute? =
        database.messageDao().getMessage(messageId)?.threadId?.let { forThread(it) }
}

/** Sending is a provider command, separate from the provider's incoming event stream. */
interface SendCommandProvider {
    val id: String
    fun connectionIssue(): String?
    fun connectionIssue(accountId: String): String? = connectionIssue()
    fun connectionIssue(route: MessageRoute): String? = connectionIssue(route.accountId)
    suspend fun send(threadId: String, body: String, attachments: List<Uri>)
    suspend fun retry(messageId: String)
}

interface ReplyCommandProvider : SendCommandProvider {
    suspend fun reply(threadId: String, repliedToMessageId: String, body: String)
}

/** Routes by the thread's account and network, and rejects overlapping sends to one thread. */
@Singleton
class ProviderSendRouter private constructor(
    private val routes: SendRouteLookup,
    providers: List<SendCommandProvider>,
) {
    @Inject constructor(database: UnifiedMessagesDatabase, smsProvider: SmsProvider, matrixProvider: MatrixProvider) :
        this(RoomSendRouteLookup(database), listOf(smsProvider, matrixProvider))

    private val providers = providers.associateBy { it.id }
    private val sendingThreads = ConcurrentHashMap.newKeySet<String>()

    fun availability(route: MessageRoute, recipients: List<String>, region: String): SendAvailability {
        if (route.providerId == "matrix" && route.membership != "JOINED") {
            return SendAvailability(route.label,
                if (route.membership == "INVITED") "Join this Matrix room before sending."
                else "You are not in this Matrix room.")
        }
        val provider = providerFor(route)
            ?: return SendAvailability(route.label, "Messaging is not connected for this conversation.")
        val issue = provider.connectionIssue(route)
        if (issue != null) return SendAvailability(route.label, issue)
        if (route.providerId == "sms" && (recipients.isEmpty() ||
                recipients.any { !PhoneNumbers.isReplyableAddress(it, region) })) {
            return SendAvailability(route.label, "This sender can't receive replies.")
        }
        return SendAvailability(route.label)
    }

    suspend fun send(threadId: String, body: String, attachments: List<Uri>) {
        val text = body.trim()
        require(text.isNotEmpty() || attachments.isNotEmpty()) { "Write a message or add an attachment." }
        check(sendingThreads.add(threadId)) { "A message is already sending in this conversation." }
        try {
            val route = routes.forThread(threadId) ?: error("Conversation no longer exists.")
            check(route.providerId != "matrix" || route.membership == "JOINED") {
                "Join this Matrix room before sending."
            }
            val provider = providerFor(route) ?: error("Messaging is not connected for this conversation.")
            provider.connectionIssue(route)?.let(::error)
            provider.send(threadId, text, attachments)
        } finally {
            sendingThreads.remove(threadId)
        }
    }

    suspend fun retry(messageId: String) {
        val route = routes.forMessage(messageId) ?: error("Message no longer exists.")
        check(route.providerId != "matrix" || route.membership == "JOINED") {
            "Join this Matrix room before sending."
        }
        check(sendingThreads.add(route.threadId)) { "A message is already sending in this conversation." }
        try {
            val provider = providerFor(route) ?: error("Messaging is not connected for this conversation.")
            provider.connectionIssue(route)?.let(::error)
            provider.retry(messageId)
        } finally {
            sendingThreads.remove(route.threadId)
        }
    }

    suspend fun reply(threadId: String, repliedToMessageId: String, body: String) {
        val text = body.trim()
        require(text.isNotEmpty()) { "Write a reply first." }
        check(sendingThreads.add(threadId)) { "A message is already sending in this conversation." }
        try {
            val route = routes.forThread(threadId) ?: error("Conversation no longer exists.")
            check(route.providerId != "matrix" || route.membership == "JOINED") {
                "Join this Matrix room before sending."
            }
            check(routes.forMessage(repliedToMessageId)?.threadId == threadId) {
                "The replied-to message is not in this conversation."
            }
            val provider = providerFor(route) as? ReplyCommandProvider
                ?: error("Replies are unavailable for this conversation.")
            provider.connectionIssue(route)?.let(::error)
            provider.reply(threadId, repliedToMessageId, text)
        } finally {
            sendingThreads.remove(threadId)
        }
    }

    private fun providerFor(route: MessageRoute): SendCommandProvider? = when {
        route.providerId == "sms" && route.accountId == "sms:local" &&
            route.networkType in setOf(NetworkType.SMS, NetworkType.MMS) -> providers["sms"]
        route.providerId == "matrix" && route.accountId.startsWith("matrix:") &&
            route.networkType in setOf(NetworkType.MATRIX, NetworkType.WHATSAPP,
                NetworkType.TELEGRAM, NetworkType.GOOGLE_MESSAGES) -> providers["matrix"]
        else -> null
    }

    companion object {
        /** Pure constructor for send-flow tests with a fake provider and fake route lookup. */
        fun forTesting(routes: SendRouteLookup, providers: List<SendCommandProvider>): ProviderSendRouter =
            ProviderSendRouter(routes, providers)
    }
}

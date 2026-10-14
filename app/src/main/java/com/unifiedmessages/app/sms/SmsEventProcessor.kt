package com.unifiedmessages.app.sms

import com.unifiedmessages.app.data.local.MessageEventReducer
import com.unifiedmessages.app.data.local.ProviderIds
import com.unifiedmessages.app.data.local.UnifiedMessagesDatabase
import com.unifiedmessages.app.provider.MessageEvent
import com.unifiedmessages.app.provider.MessageReceived
import com.unifiedmessages.app.provider.ReactionAdded
import com.unifiedmessages.app.provider.ReactionRemoved
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Persists transport events before notifying observers, including SMS reaction fallback texts. */
@Singleton
class SmsEventProcessor @Inject constructor(
    private val database: UnifiedMessagesDatabase,
) {
    private val reducer = MessageEventReducer(database)
    private val emittedEvents = MutableSharedFlow<MessageEvent>(extraBufferCapacity = 64)
    val events: Flow<MessageEvent> = emittedEvents.asSharedFlow()

    suspend fun ingest(event: MessageEvent): Boolean {
        val applied = reducer.apply(event)
        if (!applied) return false
        emittedEvents.emit(event)
        if (event is MessageReceived && event.providerId == "sms" && event.message.providerMessageId.startsWith("sms:")) {
            reconcileTextReaction(event)
        }
        return true
    }

    private suspend fun reconcileTextReaction(event: MessageReceived) {
        val fallback = SmsTextReactionParser.parse(event.message.body ?: return) ?: return
        val actor = event.message.senderAddress ?: return
        val threadId = ProviderIds.thread(event.providerId, event.thread.providerThreadId)
        val target = database.messageDao().findRecentText(
            threadId = threadId,
            body = fallback.targetText,
            beforeTimestamp = event.message.timestamp,
        ) ?: return
        // Retain the fallback SMS as a message too; it is a real carrier record.
        if (fallback.isRemoval) {
            val active = database.messageDao().getLatestActiveReaction(target.id, actor, fallback.emoji) ?: return
            ingest(
                ReactionRemoved(
                    providerId = event.providerId,
                    eventId = "sms-fallback-remove:${event.message.providerMessageId}",
                    occurredAt = event.occurredAt,
                    providerMessageId = target.providerMessageId,
                    reactionId = active.providerReactionId,
                ),
            )
        } else {
            ingest(
                ReactionAdded(
                    providerId = event.providerId,
                    eventId = "sms-fallback-add:${event.message.providerMessageId}",
                    occurredAt = event.occurredAt,
                    providerMessageId = target.providerMessageId,
                    reactionId = "sms-fallback:${event.message.providerMessageId}",
                    actorAddress = actor,
                    emoji = fallback.emoji,
                ),
            )
        }
    }
}

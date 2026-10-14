package com.unifiedmessages.app.data.local

import com.unifiedmessages.app.provider.DeliveryUpdated
import com.unifiedmessages.app.provider.MessageDeleted
import com.unifiedmessages.app.provider.MessageEdited
import com.unifiedmessages.app.provider.MessageReceived
import com.unifiedmessages.app.provider.MessageSent
import com.unifiedmessages.app.provider.ProviderAttachment
import com.unifiedmessages.app.provider.ProviderMessage
import com.unifiedmessages.app.provider.ProviderParticipant
import com.unifiedmessages.app.provider.ProviderThread
import com.unifiedmessages.app.provider.ReactionAdded
import com.unifiedmessages.app.provider.ReactionRemoved
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageEventReducerTest {
    private val providerId = "sms"
    private val thread = ProviderThread(
        providerThreadId = "17",
        source = "SMS",
        title = "Alex",
        participants = listOf(ProviderParticipant("+15551234567", "Alex")),
    )
    private val message = ProviderMessage("sms:42", "Hello", timestamp = 100L, senderAddress = "+15551234567")

    @Test fun incomingEventCreatesThreadParticipantMessageAndReplayDoesNotDuplicate() = runBlocking {
        val store = InMemoryMessageEventStore()
        val reducer = MessageEventReducer(store)
        val event = MessageReceived(providerId, "receive:sms:42", 100L, thread, message)

        assertTrue(reducer.apply(event))
        assertFalse(reducer.apply(event))
        assertEquals(1, store.threads.size)
        assertEquals(1, store.messages.size)
        assertEquals(1, store.participants.size)
        assertEquals(1, store.events.size)
        val saved = store.messages.values.single()
        assertEquals(ContentType.TEXT, saved.contentType)
        assertEquals(MessageDirection.INCOMING, saved.direction)
        assertFalse(saved.isRead)
        assertNotEquals(ProviderIds.message("sms", "sms:42"), ProviderIds.message("rcs", "sms:42"))
    }

    @Test fun editsAndSenderDeletionRetainOriginalHistoryAndMessageRow() = runBlocking {
        val store = InMemoryMessageEventStore()
        val reducer = MessageEventReducer(store)
        reducer.apply(MessageReceived(providerId, "receive", 100L, thread, message))

        assertTrue(reducer.apply(MessageEdited(providerId, "edit-1", 110L, "sms:42", "Hello there")))
        assertTrue(reducer.apply(MessageEdited(providerId, "edit-2", 120L, "sms:42", "Hello again")))
        assertFalse(reducer.apply(MessageEdited(providerId, "edit-2", 120L, "sms:42", "Hello again")))
        assertTrue(reducer.apply(MessageDeleted(providerId, "delete-1", 130L, "sms:42", bySender = true)))

        val saved = store.messages.values.single()
        assertEquals("Hello again", saved.body)
        assertTrue(saved.isDeleted)
        assertTrue(saved.deletedBySender)
        assertEquals(listOf("Hello", "Hello there"), store.edits.map { it.previousBody })
        assertEquals(listOf("Hello there", "Hello again"), store.edits.map { it.newBody })
        assertEquals(1, store.deletions.size)
        assertTrue(store.deletions.single().bySender)
    }

    @Test fun reactionsAreSoftRemovedAndReplayDoesNotCreateNewRows() = runBlocking {
        val store = InMemoryMessageEventStore()
        val reducer = MessageEventReducer(store)
        reducer.apply(MessageReceived(providerId, "receive", 100L, thread, message))
        val add = ReactionAdded(providerId, "reaction-add", 110L, "sms:42", "sms:43", "+15551234567", "👍")
        val remove = ReactionRemoved(providerId, "reaction-remove", 120L, "sms:42", "sms:43")

        assertTrue(reducer.apply(add))
        assertTrue(reducer.apply(remove))
        assertFalse(reducer.apply(add))
        assertEquals(1, store.reactions.size)
        assertEquals("👍", store.reactions.values.single().emoji)
        assertEquals(120L, store.reactions.values.single().removedAt)
    }

    @Test fun readdingAReactionRetainsItsEarlierRemoval() = runBlocking {
        val store = InMemoryMessageEventStore()
        val reducer = MessageEventReducer(store)
        reducer.apply(MessageReceived(providerId, "receive", 100L, thread, message))

        assertTrue(reducer.apply(ReactionAdded(providerId, "add-1", 110L, "sms:42", "tapback", "Alex", "👍")))
        assertTrue(reducer.apply(ReactionRemoved(providerId, "remove-1", 120L, "sms:42", "tapback")))
        assertTrue(reducer.apply(ReactionAdded(providerId, "add-2", 130L, "sms:42", "tapback", "Alex", "👍")))

        assertEquals(2, store.reactions.size)
        assertEquals(listOf(120L, null), store.reactions.values.map { it.removedAt })
        assertNotEquals(store.reactions.values.first().id, store.reactions.values.last().id)
    }

    @Test fun sameReactionIdOnDifferentMessagesDoesNotCollide() = runBlocking {
        val store = InMemoryMessageEventStore()
        val reducer = MessageEventReducer(store)
        reducer.apply(MessageReceived(providerId, "receive-1", 100L, thread, message))
        reducer.apply(MessageReceived(providerId, "receive-2", 101L, thread, message.copy(providerMessageId = "sms:43")))

        assertTrue(reducer.apply(ReactionAdded(providerId, "add-1", 110L, "sms:42", "tapback", "Alex", "👍")))
        assertTrue(reducer.apply(ReactionAdded(providerId, "add-2", 111L, "sms:43", "tapback", "Alex", "👍")))
        assertTrue(reducer.apply(ReactionRemoved(providerId, "remove-1", 120L, "sms:42", "tapback")))

        assertEquals(2, store.reactions.size)
        assertEquals(120L, store.reactions.values.single { it.messageId == ProviderIds.message(providerId, "sms:42") }.removedAt)
        assertEquals(null, store.reactions.values.single { it.messageId == ProviderIds.message(providerId, "sms:43") }.removedAt)
    }

    @Test fun eventWithMissingTargetCanBeRetriedAfterMessageArrives() = runBlocking {
        val store = InMemoryMessageEventStore()
        val reducer = MessageEventReducer(store)
        val edit = MessageEdited(providerId, "edit-early", 90L, "sms:42", "Changed")

        assertFalse(reducer.apply(edit))
        assertTrue(store.events.isEmpty())
        reducer.apply(MessageReceived(providerId, "receive", 100L, thread, message))
        assertTrue(reducer.apply(edit))
        assertEquals("Changed", store.messages.values.single().body)
    }

    @Test fun outgoingMediaRetainsAttachmentsAndDeliveryUpdates() = runBlocking {
        val store = InMemoryMessageEventStore()
        val reducer = MessageEventReducer(store)
        val media = ProviderMessage(
            providerMessageId = "mms:51",
            contentType = ContentType.MEDIA,
            timestamp = 200L,
            deliveryStatus = DeliveryStatus.PENDING,
            attachments = listOf(ProviderAttachment("part:1", "content://mms/part/1", "image/jpeg")),
        )
        reducer.apply(MessageSent(providerId, "send", 200L, thread.copy(source = "MMS"), media))
        reducer.apply(DeliveryUpdated(providerId, "delivered", 250L, "mms:51", DeliveryStatus.DELIVERED))

        val saved = store.messages.values.single()
        assertEquals(MessageDirection.OUTGOING, saved.direction)
        assertTrue(saved.isRead)
        assertEquals(ContentType.MEDIA, saved.contentType)
        assertEquals(DeliveryStatus.DELIVERED, saved.deliveryStatus)
        assertEquals(1, store.attachments.size)
    }

    @Test fun lateCallbacksAndImportsCannotRegressDeliveryStatus() = runBlocking {
        val store = InMemoryMessageEventStore()
        val reducer = MessageEventReducer(store)
        val outgoing = message.copy(deliveryStatus = DeliveryStatus.PENDING)
        reducer.apply(MessageSent(providerId, "send", 100L, thread, outgoing))

        reducer.apply(DeliveryUpdated(providerId, "delivered", 120L, "sms:42", DeliveryStatus.DELIVERED))
        reducer.apply(DeliveryUpdated(providerId, "late-sent", 110L, "sms:42", DeliveryStatus.SENT))
        reducer.apply(MessageSent(providerId, "import", 100L, thread, outgoing.copy(deliveryStatus = DeliveryStatus.SENT)))

        assertEquals(DeliveryStatus.DELIVERED, store.messages.values.single().deliveryStatus)
        assertEquals(4, store.events.size)
    }

    @Test fun outgoingSendMovesFromSendingToSentAndDelivered() = runBlocking {
        val store = InMemoryMessageEventStore()
        val reducer = MessageEventReducer(store)
        reducer.apply(MessageSent(providerId, "queued", 100L, thread,
            message.copy(deliveryStatus = DeliveryStatus.PENDING)))
        assertEquals(DeliveryStatus.PENDING, store.messages.values.single().deliveryStatus)

        reducer.apply(DeliveryUpdated(providerId, "sent", 110L, "sms:42", DeliveryStatus.SENT))
        assertEquals(DeliveryStatus.SENT, store.messages.values.single().deliveryStatus)
        reducer.apply(DeliveryUpdated(providerId, "delivered", 120L, "sms:42", DeliveryStatus.DELIVERED))
        assertEquals(DeliveryStatus.DELIVERED, store.messages.values.single().deliveryStatus)
    }

    @Test fun failedCarrierCallbackKeepsOriginalMessageForRetry() = runBlocking {
        val store = InMemoryMessageEventStore()
        val reducer = MessageEventReducer(store)
        reducer.apply(MessageSent(providerId, "queued", 100L, thread,
            message.copy(deliveryStatus = DeliveryStatus.PENDING)))
        reducer.apply(DeliveryUpdated(providerId, "failed", 110L, "sms:42", DeliveryStatus.FAILED))

        val failed = store.messages.values.single()
        assertEquals(DeliveryStatus.FAILED, failed.deliveryStatus)
        assertEquals("Hello", failed.body)
        assertFalse(failed.isDeleted)
    }

    @Test fun olderMmsImportCannotReplaceNewerSmsThreadMetadata() = runBlocking {
        val store = InMemoryMessageEventStore()
        val reducer = MessageEventReducer(store)
        reducer.apply(MessageReceived(providerId, "new-sms", 200L, thread, message.copy(timestamp = 200L)))
        reducer.apply(
            MessageReceived(
                providerId,
                "old-mms",
                100L,
                thread.copy(source = "MMS", title = "Old group"),
                message.copy(providerMessageId = "mms:1", timestamp = 100L),
            ),
        )

        assertEquals("SMS", store.threads.values.single().source)
        assertEquals("Alex", store.threads.values.single().title)
        assertEquals(200L, store.threads.values.single().updatedAt)
    }

    @Test fun laterMmsPartsFillMissingContentWithoutLosingMessageIdentity() = runBlocking {
        val store = InMemoryMessageEventStore()
        val reducer = MessageEventReducer(store)
        val early = ProviderMessage("mms:7", timestamp = 100L, contentType = ContentType.TEXT)
        val complete = early.copy(
            body = "Picture attached",
            contentType = ContentType.MEDIA,
            attachments = listOf(ProviderAttachment("part:7", "content://mms/part/7", "image/jpeg")),
        )

        assertTrue(reducer.apply(MessageReceived(providerId, "mms:early", 100L, thread, early)))
        assertTrue(reducer.apply(MessageReceived(providerId, "mms:complete", 110L, thread, complete)))

        assertEquals(1, store.messages.size)
        assertEquals("Picture attached", store.messages.values.single().body)
        assertEquals(ContentType.MEDIA, store.messages.values.single().contentType)
        assertEquals(1, store.attachments.size)
    }
}

private class InMemoryMessageEventStore : MessageEventStore {
    val events = linkedMapOf<String, AppliedProviderEventEntity>()
    val threads = linkedMapOf<String, ThreadEntity>()
    val participants = linkedMapOf<String, ParticipantEntity>()
    val messages = linkedMapOf<String, MessageEntity>()
    val attachments = linkedMapOf<String, AttachmentEntity>()
    val reactions = linkedMapOf<String, ReactionEntity>()
    val edits = mutableListOf<MessageEditEntity>()
    val deletions = mutableListOf<MessageDeletionEntity>()

    override suspend fun <T> transaction(block: suspend () -> T): T = block()
    override suspend fun hasEvent(id: String): Boolean = events.containsKey(id)
    override suspend fun recordEvent(event: AppliedProviderEventEntity) { events.putIfAbsent(event.id, event) }
    override suspend fun getThread(id: String): ThreadEntity? = threads[id]
    override suspend fun insertThread(thread: ThreadEntity) { threads.putIfAbsent(thread.id, thread) }
    override suspend fun updateThread(thread: ThreadEntity) { threads[thread.id] = thread }
    override suspend fun getParticipant(id: String): ParticipantEntity? = participants[id]
    override suspend fun insertParticipant(participant: ParticipantEntity) { participants.putIfAbsent(participant.id, participant) }
    override suspend fun updateParticipant(participant: ParticipantEntity) { participants[participant.id] = participant }
    override suspend fun getMessage(id: String): MessageEntity? = messages[id]
    override suspend fun insertMessage(message: MessageEntity) { messages.putIfAbsent(message.id, message) }
    override suspend fun updateMessage(message: MessageEntity) { messages[message.id] = message }
    override suspend fun insertAttachment(attachment: AttachmentEntity) { attachments.putIfAbsent(attachment.id, attachment) }
    override suspend fun getLatestReaction(messageId: String, providerReactionId: String): ReactionEntity? =
        reactions.values.lastOrNull { it.messageId == messageId && it.providerReactionId == providerReactionId }
    override suspend fun insertReaction(reaction: ReactionEntity) { reactions.putIfAbsent(reaction.id, reaction) }
    override suspend fun updateReaction(reaction: ReactionEntity) { reactions[reaction.id] = reaction }
    override suspend fun insertEdit(edit: MessageEditEntity) { edits.add(edit) }
    override suspend fun insertDeletion(deletion: MessageDeletionEntity) { deletions.add(deletion) }
}

package com.unifiedmessages.app.data

import com.unifiedmessages.app.model.ChatMessage
import com.unifiedmessages.app.model.Conversation
import com.unifiedmessages.app.model.MessageSource

/** Fictional, local-only content. No device message or contact access is requested. */
object SampleConversationRepository {
    val conversations = listOf(
        Conversation(
            id = "sample-imessage-alex",
            title = "Alex Rivera",
            avatarText = "AR",
            source = MessageSource.IMESSAGE,
            timestamp = "9:41 AM",
            unreadCount = 2,
            isPinned = true,
            messages = listOf(
                ChatMessage("a1", "Are we still on for the concert?", "9:30 AM", false),
                ChatMessage("a2", "Absolutely. I'll meet you at the entrance.", "9:34 AM", true),
                ChatMessage("a3", "Perfect. Doors open at eight!", "9:41 AM", false),
            ),
        ),
        Conversation(
            id = "sample-rcs-maya",
            title = "Maya Chen",
            avatarText = "MC",
            source = MessageSource.RCS,
            timestamp = "8:22 AM",
            unreadCount = 1,
            isPinned = true,
            messages = listOf(
                ChatMessage("m1", "Those photos turned out so well.", "8:18 AM", true),
                ChatMessage("m2", "I'll send the rest this evening.", "8:22 AM", false),
            ),
        ),
        Conversation(
            id = "sample-rcs-family",
            title = "Family Group",
            avatarText = "FG",
            source = MessageSource.RCS,
            timestamp = "Yesterday",
            unreadCount = 0,
            messages = listOf(
                ChatMessage("f1", "Sunday dinner is at seven. Bring your appetite!", "Yesterday · 6:12 PM", false),
                ChatMessage("f2", "Sounds good to me.", "Yesterday · 6:19 PM", true),
            ),
        ),
        Conversation(
            id = "sample-sms-delivery",
            title = "Pickup Reminder",
            avatarText = "PR",
            source = MessageSource.SMS,
            timestamp = "Yesterday",
            unreadCount = 0,
            messages = listOf(
                ChatMessage("d1", "Your order is ready for pickup. This is a sample notification.", "Yesterday · 3:05 PM", false),
            ),
        ),
        Conversation(
            id = "sample-imessage-jordan",
            title = "Jordan Lee",
            avatarText = "JL",
            source = MessageSource.IMESSAGE,
            timestamp = "Mon",
            unreadCount = 0,
            messages = listOf(
                ChatMessage("j1", "Could you look over the draft tomorrow?", "Mon · 1:17 PM", false),
                ChatMessage("j2", "Sure, I'll take a look after lunch.", "Mon · 1:24 PM", true),
            ),
        ),
        Conversation(
            id = "sample-sms-sam",
            title = "Sam Patel",
            avatarText = "SP",
            source = MessageSource.SMS,
            timestamp = "Sun",
            unreadCount = 0,
            messages = listOf(
                ChatMessage("s1", "See you at the trailhead at nine.", "Sun · 5:46 PM", false),
                ChatMessage("s2", "I'll bring coffee.", "Sun · 5:49 PM", true),
            ),
        ),
        Conversation(
            id = "sample-rcs-alex",
            title = "Alex Rivera",
            avatarText = "AR",
            source = MessageSource.RCS,
            timestamp = "Sat",
            unreadCount = 0,
            messages = listOf(
                ChatMessage("r1", "Can you share the café address?", "Sat · 11:02 AM", false),
                ChatMessage("r2", "It's on Oak Street, beside the bookstore.", "Sat · 11:04 AM", true),
            ),
        ),
    )

    fun findById(id: String): Conversation? = conversations.firstOrNull { it.id == id }
}

object InboxSearch {
    fun filter(
        conversations: List<Conversation>,
        query: String,
        source: MessageSource?,
    ): List<Conversation> {
        val normalizedQuery = query.trim()
        return conversations.filter { conversation ->
            (source == null || conversation.source == source) &&
                (normalizedQuery.isEmpty() ||
                    conversation.title.contains(normalizedQuery, ignoreCase = true) ||
                    conversation.messages.any { it.text.contains(normalizedQuery, ignoreCase = true) } ||
                    conversation.source.label.contains(normalizedQuery, ignoreCase = true))
        }
    }
}

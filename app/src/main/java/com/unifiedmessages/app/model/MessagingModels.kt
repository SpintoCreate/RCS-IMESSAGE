package com.unifiedmessages.app.model

/** The source shown in the existing chat badges. Provider integrations map to this display value. */
enum class MessageSource(val label: String) {
    IMESSAGE("iMessage"),
    RCS("RCS"),
    SMS("SMS"),
    OTHER("Other"),
}

data class ChatMessage(
    val id: String,
    val text: String,
    val time: String,
    val isMine: Boolean,
)

data class Conversation(
    val id: String,
    val title: String,
    val avatarText: String,
    val source: MessageSource,
    val timestamp: String,
    val unreadCount: Int,
    val isPinned: Boolean = false,
    val messages: List<ChatMessage>,
    /** Inbox summaries avoid loading every message merely to show the latest preview. */
    val previewText: String? = null,
) {
    val preview: String get() = previewText ?: messages.lastOrNull()?.text.orEmpty()
}

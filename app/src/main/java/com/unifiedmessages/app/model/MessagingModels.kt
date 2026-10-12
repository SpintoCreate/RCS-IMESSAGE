package com.unifiedmessages.app.model

/** The transport for a conversation. This is display data until a real connector exists. */
enum class MessageSource(val label: String) {
    IMESSAGE("iMessage"),
    RCS("RCS"),
    SMS("SMS"),
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
    val messages: List<ChatMessage>,
) {
    val preview: String get() = messages.lastOrNull()?.text.orEmpty()
}

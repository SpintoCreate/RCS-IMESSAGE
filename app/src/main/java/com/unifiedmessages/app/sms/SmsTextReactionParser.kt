package com.unifiedmessages.app.sms

/** Parses the small, unambiguous subset of SMS reaction fallbacks used by Messages. */
data class SmsTextReaction(
    val targetText: String,
    val emoji: String,
    val isRemoval: Boolean = false,
)

object SmsTextReactionParser {
    private val additions = linkedMapOf(
        "Liked " to "👍",
        "Loved " to "❤️",
        "Disliked " to "👎",
        "Laughed at " to "😂",
        "Emphasized " to "‼️",
        "Questioned " to "❓",
    )

    private val removals = linkedMapOf(
        "Removed a like from " to "👍",
        "Removed a heart from " to "❤️",
        "Removed a dislike from " to "👎",
        "Removed a laugh from " to "😂",
        "Removed an emphasis from " to "‼️",
        "Removed a question mark from " to "❓",
    )

    fun parse(body: String): SmsTextReaction? {
        val text = body.trim()
        for ((prefix, emoji) in additions) {
            if (text.startsWith(prefix, ignoreCase = true)) {
                val target = quotedTarget(text.substring(prefix.length)) ?: return null
                return SmsTextReaction(target, emoji)
            }
        }
        for ((prefix, emoji) in removals) {
            if (text.startsWith(prefix, ignoreCase = true)) {
                val target = quotedTarget(text.substring(prefix.length)) ?: return null
                return SmsTextReaction(target, emoji, isRemoval = true)
            }
        }
        return null
    }

    private fun quotedTarget(text: String): String? {
        if (text.length < 3 || text.length > 4_098) return null
        val closing = when (text.first()) {
            '"' -> '"'
            '\'' -> '\''
            '“' -> '”'
            '‘' -> '’'
            else -> return null
        }
        if (text.last() != closing) return null
        return text.substring(1, text.lastIndex)
            .takeIf { it.isNotBlank() && '\n' !in it && '\r' !in it }
    }
}

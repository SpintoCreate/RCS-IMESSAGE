package com.unifiedmessages.app.sms

import android.content.ContentUris
import android.content.Context
import android.provider.Telephony
import com.unifiedmessages.app.data.local.MessageDirection
import com.unifiedmessages.app.data.local.ThreadEntity
import com.unifiedmessages.app.data.local.UnifiedMessagesDatabase
import kotlinx.coroutines.flow.first

/** A Room participant list can be incomplete for an imported MMS if Android cannot identify our SIM number. */
internal object CarrierThreadVerifier {
    suspend fun requireSafeRecipients(
        context: Context,
        database: UnifiedMessagesDatabase,
        thread: ThreadEntity,
        normalizedRecipients: Set<String>,
    ) {
        val resolvedSystemId = Telephony.Threads.getOrCreateThreadId(context, normalizedRecipients).toString()
        val hasIncomingMms = database.messageDao().observeMessages(thread.id).first().any { message ->
            message.direction == MessageDirection.INCOMING && message.providerMessageId.startsWith("mms:")
        }
        val canonicalCount = if (hasIncomingMms) canonicalRecipientCount(context, thread.providerThreadId) else null
        check(ThreadRecipients.canUseExistingThread(
            thread.providerThreadId,
            resolvedSystemId,
            normalizedRecipients.size,
            hasIncomingMms,
            canonicalCount,
        )) {
            "Cannot verify every recipient in this conversation. Start a New Message and add all recipients."
        }
    }

    private fun canonicalRecipientCount(context: Context, systemThreadId: String): Int? {
        val id = systemThreadId.toLongOrNull() ?: return null
        return try {
            context.contentResolver.query(
                ContentUris.withAppendedId(Telephony.Threads.CONTENT_URI, id),
                arrayOf(Telephony.Threads.RECIPIENT_IDS),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getString(0)?.split(Regex("[,\\s]+"))
                    ?.filter(String::isNotBlank)?.distinct()?.size?.takeIf { it > 0 }
            }
        } catch (_: Exception) {
            // A missing provider count must never make an incompletely imported group sendable.
            null
        }
    }
}

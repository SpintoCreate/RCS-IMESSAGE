package com.unifiedmessages.app.data.repository

import com.unifiedmessages.app.data.local.ParticipantEntity
import com.unifiedmessages.app.sms.PhoneNumbers

/** Terms passed to the Room inbox query, which compares numbers after removing punctuation. */
internal data class SearchNumberTerms(
    val digits: String,
    val digitsWithoutUsPrefix: String,
)

internal object SearchMatching {
    /** Resolve cached names through E.164, including national-format participants outside the US. */
    fun matchingContactThreadIds(
        query: String,
        region: String,
        participants: List<ParticipantEntity>,
        contacts: List<CachedContact>,
    ): List<String> {
        val search = query.trim()
        if (search.isEmpty()) return emptyList()
        val matchingNumbers = contacts.asSequence()
            .filter { it.displayName.contains(search, ignoreCase = true) }
            .map { it.normalizedNumber }
            .toSet()
        if (matchingNumbers.isEmpty()) return emptyList()
        return participants.asSequence()
            .filterNot { it.isSelf }
            .filter { PhoneNumbers.normalizeToE164(it.address, region) in matchingNumbers }
            .map { it.threadId }
            .distinct()
            .toList()
    }

    fun numberTerms(query: String): SearchNumberTerms {
        val trimmed = query.trim()
        if (trimmed.any(Char::isLetter)) return SearchNumberTerms("", "")
        val digits = trimmed.filter(Char::isDigit)
        val withoutPrefix = if (
            digits.startsWith('1') && (trimmed.startsWith("+1") || digits.length == 11)
        ) digits.drop(1) else ""
        return SearchNumberTerms(digits, withoutPrefix)
    }

    /** Keep the matching phrase near the start of the one-line inbox preview. */
    fun messageSnippet(body: String, query: String): String? {
        val needle = query.trim()
        if (needle.isEmpty()) return null
        val flattened = body.replace(Regex("[\r\n\t]+"), " ").trim()
        val matchStart = flattened.indexOf(needle, ignoreCase = true)
        if (matchStart < 0) return null
        val start = (matchStart - 18).coerceAtLeast(0)
        val end = (matchStart + needle.length + 48).coerceAtMost(flattened.length)
        return buildString {
            if (start > 0) append('…')
            append(flattened, start, end)
            if (end < flattened.length) append('…')
        }
    }
}

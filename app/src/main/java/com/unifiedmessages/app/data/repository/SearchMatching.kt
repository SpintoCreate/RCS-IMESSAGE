package com.unifiedmessages.app.data.repository

/** Terms passed to the Room inbox query, which compares numbers after removing punctuation. */
internal data class SearchNumberTerms(
    val digits: String,
    val digitsWithoutUsPrefix: String,
)

internal object SearchMatching {
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

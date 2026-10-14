package com.unifiedmessages.app.sms

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

/** Parsing is region-aware, while stored recipient identities use one international format. */
object PhoneNumbers {
    private val util = PhoneNumberUtil.getInstance()

    /** Returns null for service codes, sender IDs, and numbers too short to receive SMS replies. */
    fun normalizeToE164(raw: String, region: String): String? {
        val input = raw.trim()
        if (input.isEmpty() || input.any(Char::isLetter) || input.any { it == '*' || it == '#' }) {
            return null
        }
        return try {
            val number = util.parse(input, region.uppercase(Locale.ROOT).ifBlank { "ZZ" })
            if (!util.isPossibleNumber(number)) null
            else util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164)
        } catch (_: NumberParseException) {
            null
        }
    }

    fun isReplyableAddress(raw: String, region: String): Boolean =
        normalizeToE164(raw, region) != null

    fun formatForDisplay(raw: String, region: String): String? {
        val normalized = normalizeToE164(raw, region) ?: return null
        return try {
            val parsed = util.parse(normalized, "ZZ")
            util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
        } catch (_: NumberParseException) {
            null
        }
    }
}

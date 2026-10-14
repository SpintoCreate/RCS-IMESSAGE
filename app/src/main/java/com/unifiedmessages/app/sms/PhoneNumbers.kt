package com.unifiedmessages.app.sms

import android.content.Context
import android.telephony.TelephonyManager
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

/** Parsing is region-aware, while stored recipient identities use one international format. */
object PhoneNumbers {
    private val util = PhoneNumberUtil.getInstance()

    /** Network, SIM, then device locale provide a region without requesting phone permissions. */
    fun deviceRegion(context: Context): String {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val network = try { telephony?.networkCountryIso } catch (_: SecurityException) { null }
        val sim = try { telephony?.simCountryIso } catch (_: SecurityException) { null }
        return sequenceOf(network, sim, Locale.getDefault().country)
            .filterNotNull()
            .firstOrNull { it.length == 2 && it.all(Char::isLetter) }
            ?.uppercase(Locale.ROOT) ?: "US"
    }

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
            util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
        } catch (_: NumberParseException) {
            null
        }
    }
}

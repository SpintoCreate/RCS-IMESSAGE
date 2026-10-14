package com.unifiedmessages.app.sms

/** Phone identities are compared as sets, so formatting and chip order cannot split threads. */
object ThreadRecipients {
    data class Resolution(val normalizedRecipients: Set<String>, val existingThreadId: String?)

    fun normalize(recipients: Collection<String>, region: String): Set<String>? {
        if (recipients.isEmpty()) return null
        val normalized = recipients.map { PhoneNumbers.normalizeToE164(it, region) ?: return null }
        return normalized.toSet()
    }

    fun requireNormalized(recipients: Collection<String>, region: String): Set<String> =
        normalize(recipients, region)
            ?: throw IllegalArgumentException("Enter a valid phone number for every recipient.")

    fun sameRecipients(
        requestedNormalized: Set<String>,
        existing: Collection<String>,
        region: String,
    ): Boolean = normalize(existing, region) == requestedNormalized

    fun findExactThreadId(
        requested: Collection<String>,
        region: String,
        threads: List<Candidate>,
    ): String? {
        val requestedNormalized = normalize(requested, region) ?: return null
        return threads.firstOrNull { sameRecipients(requestedNormalized, it.recipients, region) }?.threadId
    }

    fun resolve(requested: Collection<String>, region: String, threads: List<Candidate>): Resolution {
        val normalized = requireNormalized(requested, region)
        val existing = threads.firstOrNull { sameRecipients(normalized, it.recipients, region) }
        return Resolution(normalized, existing?.threadId)
    }

    data class Candidate(val threadId: String, val recipients: List<String>)
}

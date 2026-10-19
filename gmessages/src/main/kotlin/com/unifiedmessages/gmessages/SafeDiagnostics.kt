package com.unifiedmessages.gmessages

/** Sanitizes the complete Java/Go error chain before either UI display or Logcat. */
internal object SafeDiagnostics {
    private val phone = Regex("(?<![A-Za-z0-9])\\+?\\d[\\d ()-]{6,}\\d")
    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val urlQuery = Regex("(https?://[^\\s?]+)\\?[^\\s]+")
    private val cookieField = Regex("(?i)(SID|HSID|OSID|SSID|APISID|SAPISID|__Secure-1PSIDTS)=([^;\\s]+)")
    private val longOpaque = Regex("(?<![A-Za-z0-9])[A-Za-z0-9_+/-]{40,}(?![A-Za-z0-9])")

    fun describe(error: Throwable, secrets: Collection<String> = emptyList()): String {
        val chain = generateSequence(error) { it.cause }.take(12)
            .joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" }
        var text = chain
        for (secret in secrets) if (secret.isNotBlank()) text = text.replace(secret, "[redacted]")
        return text.replace(cookieField, "$1=[redacted]")
            .replace(urlQuery, "$1?[redacted]")
            .replace(email, "[account]")
            .replace(phone, "[number]")
            .replace(longOpaque, "[credential]")
    }
}

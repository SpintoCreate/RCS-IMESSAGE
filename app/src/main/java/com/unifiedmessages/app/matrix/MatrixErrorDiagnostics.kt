package com.unifiedmessages.app.matrix

/** Formats every Java-visible cause without printing sign-in secrets to Logcat or the screen. */
object MatrixErrorDiagnostics {
    private val url = Regex("""https?://[^\s)]+""", RegexOption.IGNORE_CASE)
    private val credential = Regex("""(?i)\b(access[_-]?token|refresh[_-]?token|id[_-]?token|login[_-]?token|token|password|secret|recovery[_-]?key|authorization)\b\s*[:=]\s*(?:Bearer\s+)?[^\s,;)}]+""")
    private val bearer = Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/-]+""")

    fun format(error: Throwable, secrets: List<String> = emptyList()): String {
        val visited = HashSet<Throwable>()
        val lines = mutableListOf<String>()
        var current: Throwable? = error
        while (current != null && visited.add(current)) {
            val detail = current.message?.takeIf(String::isNotBlank) ?: "(no message)"
            lines += "${current.javaClass.simpleName}: ${redact(detail, secrets)}"
            current = current.cause
        }
        return lines.joinToString("\nCaused by: ")
    }

    fun explanation(error: Throwable): String {
        val text = generateSequence(error) { it.cause }
            .take(16).joinToString(" ") { "${it.javaClass.simpleName} ${it.message.orEmpty()}" }
            .lowercase()
        return when {
            "certificate" in text || "ssl" in text || "tls" in text || "rustls" in text ->
                "Secure connection failed. Check the certificate details below."
            "unknownhost" in text || "dns" in text || "resolve" in text ->
                "The homeserver name could not be resolved."
            "timeout" in text || "timed out" in text ->
                "The homeserver did not respond in time."
            "unreachable" in text || "no route" in text || "network is down" in text ->
                "No network route to the homeserver is available."
            "connectexception" in text || "connection refused" in text ->
                "The homeserver refused the connection."
            else -> "Matrix connection failed. The details below may identify the cause."
        }
    }

    private fun redact(value: String, secrets: List<String>): String {
        var safe = url.replace(value) { match ->
            match.value.substringBefore('?').substringBefore('#') +
                if ('?' in match.value || '#' in match.value) "?[redacted]" else ""
        }
        safe = credential.replace(safe) { "${it.groupValues[1]}=[redacted]" }
        safe = bearer.replace(safe, "Bearer [redacted]")
        secrets.filter(String::isNotEmpty).forEach { safe = safe.replace(it, "[redacted]") }
        return safe
    }
}

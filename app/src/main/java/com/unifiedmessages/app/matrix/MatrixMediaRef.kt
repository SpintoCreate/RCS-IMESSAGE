package com.unifiedmessages.app.matrix

import java.nio.charset.StandardCharsets
import java.util.Base64

/** App-private pointer to a Matrix media source; the SDK performs encrypted downloads. */
data class MatrixMediaRef(val accountId: String, val sourceJson: String) {
    fun encode(): String = "matrix-media:${part(accountId)}:${part(sourceJson)}"

    companion object {
        fun parse(value: String): MatrixMediaRef? {
            if (!value.startsWith("matrix-media:")) return null
            val parts = value.removePrefix("matrix-media:").split(':')
            if (parts.size != 2) return null
            return runCatching { MatrixMediaRef(unpart(parts[0]), unpart(parts[1])) }.getOrNull()
        }

        private fun part(value: String): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

        private fun unpart(value: String): String = String(
            Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8,
        )
    }
}

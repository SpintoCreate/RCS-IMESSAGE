package com.unifiedmessages.app.matrix

import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatrixErrorDiagnosticsTest {
    @Test fun includesEveryCauseAndIdentifiesCertificateFailures() {
        val root = SSLHandshakeException("certificate verify failed")
        val middle = IllegalStateException("TLS connection failed", root)
        val failure = IllegalStateException("error sending request", middle)

        val details = MatrixErrorDiagnostics.format(failure)
        assertTrue(details.contains("IllegalStateException: error sending request"))
        assertTrue(details.contains("Caused by: IllegalStateException: TLS connection failed"))
        assertTrue(details.contains("Caused by: SSLHandshakeException: certificate verify failed"))
        assertTrue(MatrixErrorDiagnostics.explanation(failure).startsWith("Secure connection failed"))
    }

    @Test fun redactsPasswordsTokensAndUrlQueries() {
        val password = "private-password-123"
        val failure = IllegalStateException(
            "GET https://matrix.org/login?access_token=abc123 failed; password=$password Authorization: Bearer secret-token",
        )
        val details = MatrixErrorDiagnostics.format(failure, listOf(password))
        assertFalse(details.contains(password))
        assertFalse(details.contains("abc123"))
        assertFalse(details.contains("secret-token"))
        assertTrue(details.contains("https://matrix.org/login?[redacted]"))
    }

    @Test fun identifiesTimeouts() {
        assertEquals("The homeserver did not respond in time.",
            MatrixErrorDiagnostics.explanation(SocketTimeoutException("timed out")))
    }
}

package com.unifiedmessages.gmessages

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeDiagnosticsTest {
    @Test fun redactsNestedFailuresBeforeLogcat() {
        val error = IllegalStateException("request failed", IllegalArgumentException(
            "SID=sample_value; user@example.com; +1 555 555 0123; text=private sample",
        ))
        val output = SafeDiagnostics.describe(error, listOf("private sample"))
        assertTrue(output.contains("IllegalStateException"))
        assertTrue(output.contains("IllegalArgumentException"))
        assertFalse(output.contains("sample_value"))
        assertFalse(output.contains("user@example.com"))
        assertFalse(output.contains("555 555 0123"))
        assertFalse(output.contains("private sample"))
    }
}

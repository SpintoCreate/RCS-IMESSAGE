package com.unifiedmessages.app.matrix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatrixStartupPolicyTest {
    @Test fun failedRestoreStopsFurtherAutomaticStarts() {
        val failures = MatrixStartupPolicy.afterFailure(0)
        assertEquals(1, failures)
        assertTrue(MatrixStartupPolicy.shouldSkip("NEEDS_ATTENTION", failures))
        assertFalse(MatrixStartupPolicy.shouldSkip("CONNECTED", 0))
    }

    @Test fun twoFailedStartsBlockEvenAnOldConnectedAccountRow() {
        val failures = MatrixStartupPolicy.afterFailure(MatrixStartupPolicy.afterFailure(0))
        assertEquals(2, failures)
        assertEquals(2, MatrixStartupPolicy.afterFailure(failures))
        assertTrue(MatrixStartupPolicy.shouldSkip("CONNECTED", failures))
    }
}

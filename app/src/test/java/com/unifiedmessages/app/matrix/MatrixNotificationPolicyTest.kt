package com.unifiedmessages.app.matrix

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatrixNotificationPolicyTest {
    @Test fun initialHistoryImportNeverNotifies() {
        assertFalse(shouldNotifyMatrixMessage(null, 200_000))
    }

    @Test fun olderBackfillDoesNotNotify() {
        assertFalse(shouldNotifyMatrixMessage(200_000, 50_000))
    }

    @Test fun newEventsAfterPreviousSyncNotify() {
        assertTrue(shouldNotifyMatrixMessage(200_000, 201_000))
    }
}

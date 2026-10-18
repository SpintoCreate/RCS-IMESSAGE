package com.unifiedmessages.app.matrix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatrixSyncProgressTest {
    @Test fun syncingBecomesSyncedWithRooms() {
        assertEquals("Syncing...", MatrixSyncProgress.syncing().message)
        val done = MatrixSyncProgress.synced(2)
        assertEquals(MatrixSyncPhase.SYNCED_WITH_ROOMS, done.phase)
        assertEquals("Synced 2 rooms.", done.message)
    }

    @Test fun successfulSyncWithNoRoomsShowsInvitationHint() {
        val done = MatrixSyncProgress.synced(0)
        assertEquals(MatrixSyncPhase.SYNCED_EMPTY, done.phase)
        assertTrue(done.message.contains("Invite this account"))
    }

    @Test fun failedSyncShowsActionableReason() {
        val failed = MatrixSyncProgress.failed("Timed out after 60 seconds. Tap Retry.")
        assertEquals(MatrixSyncPhase.FAILED, failed.phase)
        assertTrue(failed.message.contains("Retry"))
    }
}

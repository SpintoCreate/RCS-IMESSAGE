package com.unifiedmessages.app.matrix

enum class MatrixSyncPhase { SYNCING, SYNCED_WITH_ROOMS, SYNCED_EMPTY, FAILED }

data class MatrixSyncProgress(
    val phase: MatrixSyncPhase,
    val roomCount: Int = 0,
    val reason: String? = null,
) {
    val message: String get() = when (phase) {
        MatrixSyncPhase.SYNCING -> "Syncing..."
        MatrixSyncPhase.SYNCED_WITH_ROOMS -> "Synced $roomCount room${if (roomCount == 1) "" else "s"}."
        MatrixSyncPhase.SYNCED_EMPTY -> "No rooms yet. Invite this account from another Matrix client."
        MatrixSyncPhase.FAILED -> "Sync failed: ${reason ?: "Unknown error"}"
    }

    companion object {
        fun syncing() = MatrixSyncProgress(MatrixSyncPhase.SYNCING)
        fun synced(roomCount: Int): MatrixSyncProgress {
            require(roomCount >= 0)
            return MatrixSyncProgress(if (roomCount == 0) MatrixSyncPhase.SYNCED_EMPTY
                else MatrixSyncPhase.SYNCED_WITH_ROOMS, roomCount)
        }
        fun failed(reason: String) = MatrixSyncProgress(MatrixSyncPhase.FAILED,
            reason = reason.ifBlank { "The homeserver did not respond. Try again." })
    }
}

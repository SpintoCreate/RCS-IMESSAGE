package com.unifiedmessages.app.matrix

internal object MatrixRoomSelection {
    /** Incremental sync replies omit unchanged rooms; the SDK cache completes the room list. */
    fun active(updated: List<String>, cached: List<String>, left: Set<String>): List<String> =
        (updated + cached).distinct().filterNot { it in left }
}

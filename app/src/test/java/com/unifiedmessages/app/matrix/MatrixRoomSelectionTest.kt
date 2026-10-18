package com.unifiedmessages.app.matrix

import org.junit.Assert.assertEquals
import org.junit.Test

class MatrixRoomSelectionTest {
    @Test fun unchangedCachedRoomsAreImportedWithoutDuplicates() {
        assertEquals(listOf("!changed", "!cached"), MatrixRoomSelection.active(
            listOf("!changed"), listOf("!cached", "!changed"), emptySet()))
    }

    @Test fun roomsLeftInThisBatchAreNotReimported() {
        assertEquals(listOf("!joined"), MatrixRoomSelection.active(
            listOf("!joined"), listOf("!left"), setOf("!left")))
    }
}

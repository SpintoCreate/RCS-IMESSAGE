package com.unifiedmessages.app.matrix

import com.unifiedmessages.app.data.local.ReactionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MatrixReactionMapperTest {
    private val old = ReactionEntity("reaction-1", "message-1", "alice:👍", "@alice:example.org",
        "👍", 100L)

    @Test fun unchangedSnapshotDoesNotDuplicateReaction() {
        assertEquals(emptyList<MatrixReactionChange>(), matrixReactionChanges(listOf(old),
            listOf(MatrixObservedReaction("@alice:example.org", "👍", 100L))))
    }

    @Test fun missingReactionRemovesOnlyThatActorsRow() {
        val bob = old.copy(id = "reaction-2", actorAddress = "@bob:example.org")
        assertEquals(listOf(MatrixReactionChange.Remove(old)), matrixReactionChanges(listOf(old, bob),
            listOf(MatrixObservedReaction("@bob:example.org", "👍", 110L))))
    }

    @Test fun restoredReactionCanBeAddedAgainAfterRemoval() {
        assertEquals(listOf(MatrixReactionChange.Add(MatrixObservedReaction("@alice:example.org", "👍", 200L))),
            matrixReactionChanges(emptyList(), listOf(MatrixObservedReaction("@alice:example.org", "👍", 200L))))
    }
}

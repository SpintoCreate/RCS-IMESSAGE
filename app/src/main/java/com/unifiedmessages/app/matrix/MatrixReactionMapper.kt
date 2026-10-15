package com.unifiedmessages.app.matrix

import com.unifiedmessages.app.data.local.ReactionEntity

internal data class MatrixObservedReaction(val actor: String, val emoji: String, val timestamp: Long)

internal sealed interface MatrixReactionChange {
    data class Add(val observed: MatrixObservedReaction) : MatrixReactionChange
    data class Remove(val active: ReactionEntity) : MatrixReactionChange
}

/** Timeline reactions are aggregate snapshots, so compare them with active local rows. */
internal fun matrixReactionChanges(
    active: List<ReactionEntity>, observed: List<MatrixObservedReaction>,
): List<MatrixReactionChange> = buildList {
    observed.distinctBy { it.actor to it.emoji }.forEach { current ->
        if (active.none { it.actorAddress == current.actor && it.emoji == current.emoji }) {
            add(MatrixReactionChange.Add(current))
        }
    }
    active.forEach { old ->
        if (observed.none { it.actor == old.actorAddress && it.emoji == old.emoji }) {
            add(MatrixReactionChange.Remove(old))
        }
    }
}

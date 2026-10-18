package com.unifiedmessages.app.matrix

import android.content.Context

/** A failed Matrix start must not make the rest of the messaging app unlaunchable. */
internal object MatrixStartupPolicy {
    fun shouldSkip(connectionState: String, failedStarts: Int): Boolean =
        connectionState == "NEEDS_ATTENTION" || failedStarts >= 2

    fun afterFailure(failedStarts: Int): Int = (failedStarts + 1).coerceAtMost(2)
}

internal class MatrixStartupGuard(context: Context) {
    private val preferences = context.getSharedPreferences("matrix_startup_v1", Context.MODE_PRIVATE)

    @Synchronized fun failures(accountId: String): Int = preferences.getInt(accountId, 0)

    @Synchronized fun recordFailure(accountId: String): Int {
        val count = MatrixStartupPolicy.afterFailure(failures(accountId))
        check(preferences.edit().putInt(accountId, count).commit()) {
            "Could not save Matrix startup failure state."
        }
        return count
    }

    @Synchronized fun clear(accountId: String) {
        check(preferences.edit().remove(accountId).commit()) {
            "Could not clear Matrix startup failure state."
        }
    }
}

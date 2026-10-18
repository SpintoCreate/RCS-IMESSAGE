package com.unifiedmessages.app.matrix

/** WorkManager skips catch-up while the visible app owns foreground syncing. */
object MatrixAppVisibility {
    @Volatile var isForeground: Boolean = false
}

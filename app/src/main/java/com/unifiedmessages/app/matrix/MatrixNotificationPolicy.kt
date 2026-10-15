package com.unifiedmessages.app.matrix

/** Initial history import is quiet; later syncs notify only events newer than the prior cursor. */
internal fun shouldNotifyMatrixMessage(previousSyncAt: Long?, occurredAt: Long): Boolean =
    previousSyncAt != null && occurredAt >= previousSyncAt - 60_000L

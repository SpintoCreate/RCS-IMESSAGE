package com.unifiedmessages.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A phone-number row from the device contacts provider, cached for offline chat labels. */
@Entity(
    tableName = "cached_contacts",
    indices = [Index("normalizedNumber")],
)
data class ContactEntity(
    @PrimaryKey val id: Long,
    val displayName: String,
    val normalizedNumber: String,
    val numberDigits: String,
    val photoUri: String?,
)

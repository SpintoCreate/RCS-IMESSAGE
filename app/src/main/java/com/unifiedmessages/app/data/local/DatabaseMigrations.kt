package com.unifiedmessages.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds the opt-in contacts cache without touching existing messages. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `cached_contacts` (
                `id` INTEGER NOT NULL,
                `displayName` TEXT NOT NULL,
                `normalizedNumber` TEXT NOT NULL,
                `numberDigits` TEXT NOT NULL,
                `photoUri` TEXT,
                PRIMARY KEY(`id`)
            )""",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cached_contacts_normalizedNumber` ON `cached_contacts` (`normalizedNumber`)",
        )
    }
}

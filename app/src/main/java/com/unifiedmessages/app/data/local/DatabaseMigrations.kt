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

/** Keeps all existing local IDs while adding account-scoped Matrix sync metadata. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `threads` ADD COLUMN `accountId` TEXT")
        db.execSQL("ALTER TABLE `threads` ADD COLUMN `networkType` TEXT")
        db.execSQL("UPDATE `threads` SET `accountId` = `providerId` || ':local', `networkType` = UPPER(`source`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `provider_accounts` (
            `id` TEXT NOT NULL, `providerId` TEXT NOT NULL, `remoteSelfId` TEXT,
            `connectionState` TEXT NOT NULL, `credentialRef` TEXT, `homeserver` TEXT,
            PRIMARY KEY(`id`))""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_provider_accounts_providerId` ON `provider_accounts` (`providerId`)")
        db.execSQL("""INSERT INTO `provider_accounts` (`id`, `providerId`, `remoteSelfId`, `connectionState`, `credentialRef`, `homeserver`)
            SELECT DISTINCT `providerId` || ':local', `providerId`, NULL,
                CASE WHEN `providerId` = 'sms' THEN 'DISCONNECTED' ELSE 'UNAVAILABLE' END,
                NULL, NULL FROM `threads`""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `remote_id_mappings` (
            `id` TEXT NOT NULL, `accountId` TEXT NOT NULL, `entityType` TEXT NOT NULL,
            `remoteId` TEXT NOT NULL, `localId` TEXT NOT NULL, PRIMARY KEY(`id`))""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_remote_id_mappings_accountId_entityType_remoteId` ON `remote_id_mappings` (`accountId`, `entityType`, `remoteId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_remote_id_mappings_localId` ON `remote_id_mappings` (`localId`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `sync_cursors` (
            `id` TEXT NOT NULL, `accountId` TEXT NOT NULL, `remoteRoomId` TEXT,
            `cursor` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_cursors_accountId` ON `sync_cursors` (`accountId`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `pending_provider_events` (
            `id` TEXT NOT NULL, `accountId` TEXT NOT NULL, `providerId` TEXT NOT NULL,
            `eventId` TEXT NOT NULL, `targetMessageId` TEXT NOT NULL, `remoteMessageId` TEXT NOT NULL,
            `eventType` TEXT NOT NULL, `payload` TEXT NOT NULL,
            `occurredAt` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_provider_events_accountId` ON `pending_provider_events` (`accountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_provider_events_targetMessageId` ON `pending_provider_events` (`targetMessageId`)")
    }
}

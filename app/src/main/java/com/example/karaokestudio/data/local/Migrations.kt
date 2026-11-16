package com.example.karaokestudio.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE songs ADD COLUMN slug TEXT")
        db.execSQL("ALTER TABLE songs ADD COLUMN remoteUpdatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE songs SET slug = substr(audioSource, 6) WHERE isDemo = 1 AND audioSource LIKE 'demo:%'")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_songs_slug ON songs (slug)")

        db.execSQL("ALTER TABLE performances ADD COLUMN clientId TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE performances SET clientId = lower(hex(randomblob(16)))")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_performances_clientId ON performances (clientId)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS sync_outbox (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "type TEXT NOT NULL, " +
                "entityKey TEXT NOT NULL, " +
                "op TEXT NOT NULL, " +
                "changedAt INTEGER NOT NULL, " +
                "attempts INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_outbox_type_entityKey ON sync_outbox (type, entityKey)")
    }
}

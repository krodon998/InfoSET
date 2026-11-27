package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration_38_39 = object : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE memory_atom ADD COLUMN surface_count INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE memory_atom ADD COLUMN last_surfaced_at INTEGER")
        db.execSQL(
            "ALTER TABLE memory_recall_trace ADD COLUMN mode TEXT NOT NULL DEFAULT 'LEGACY_RECALL'"
        )
    }
}

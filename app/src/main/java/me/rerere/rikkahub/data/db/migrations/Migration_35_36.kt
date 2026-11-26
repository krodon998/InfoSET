package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 扩展用量流水，为真实 API 成本实验保留缓存写入、推理、耗时和费用。 */
val Migration_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE usage_record ADD COLUMN cache_write_tokens INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE usage_record ADD COLUMN reasoning_tokens INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE usage_record ADD COLUMN duration_ms INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE usage_record ADD COLUMN estimated_cost_micros INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE usage_record ADD COLUMN reasoning_level TEXT NOT NULL DEFAULT ''")
    }
}

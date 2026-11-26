package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 建立 InfoSET 信箱；手写迁移避免后续版本依赖未入库的 35.json。 */
val Migration_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS mailbox_letters (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                summary TEXT,
                salutation TEXT,
                body TEXT NOT NULL,
                signature TEXT,
                unlock_at INTEGER NOT NULL,
                written_at INTEGER NOT NULL,
                opened_at INTEGER,
                source TEXT NOT NULL,
                source_ref TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_mailbox_letters_unlock_at ON mailbox_letters(unlock_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_mailbox_letters_opened_at ON mailbox_letters(opened_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_mailbox_letters_source_source_ref ON mailbox_letters(source, source_ref)")
    }
}

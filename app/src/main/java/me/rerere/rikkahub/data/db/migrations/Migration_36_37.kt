package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds canonical sparse memory atoms plus auditable recall traces. Legacy Markdown stays in memory_file. */
val Migration_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `memory_atom` (
                `id` TEXT NOT NULL,
                `memory_id` TEXT NOT NULL,
                `statement` TEXT NOT NULL,
                `occurred_at` INTEGER,
                `imported_at` INTEGER,
                `people_json` TEXT,
                `place` TEXT,
                `topic` TEXT,
                `event` TEXT,
                `change` TEXT,
                `reflection` TEXT,
                `source_kind` TEXT,
                `source_ref` TEXT,
                `source_excerpt` TEXT,
                `importance` REAL,
                `stability` REAL,
                `confidence` REAL,
                `tags_json` TEXT,
                `relations_json` TEXT,
                `recall_count` INTEGER NOT NULL DEFAULT 0,
                `last_recalled_at` INTEGER,
                `activation` REAL NOT NULL DEFAULT 1.0,
                `positive_feedback_count` INTEGER NOT NULL DEFAULT 0,
                `negative_feedback_count` INTEGER NOT NULL DEFAULT 0,
                `state` TEXT NOT NULL DEFAULT 'ACTIVE',
                `pinned` INTEGER NOT NULL DEFAULT 0,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_atom_memory_id` ON `memory_atom` (`memory_id`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_memory_atom_memory_id_state` " +
                "ON `memory_atom` (`memory_id`, `state`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_atom_occurred_at` ON `memory_atom` (`occurred_at`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_memory_atom_memory_id_source_kind_source_ref` " +
                "ON `memory_atom` (`memory_id`, `source_kind`, `source_ref`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `memory_recall_trace` (
                `id` TEXT NOT NULL,
                `memory_id` TEXT NOT NULL,
                `conversation_id` TEXT,
                `query` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `token_budget` INTEGER NOT NULL,
                `injected_content` TEXT NOT NULL,
                `injected_tokens` INTEGER NOT NULL,
                `destination` TEXT NOT NULL,
                `feedback` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_memory_recall_trace_memory_id_created_at` " +
                "ON `memory_recall_trace` (`memory_id`, `created_at`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_memory_recall_trace_conversation_id` " +
                "ON `memory_recall_trace` (`conversation_id`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `memory_recall_candidate` (
                `trace_id` TEXT NOT NULL,
                `atom_id` TEXT NOT NULL,
                `rank` INTEGER NOT NULL,
                `statement` TEXT NOT NULL,
                `source_summary` TEXT,
                `total_score` REAL NOT NULL,
                `lexical_score` REAL NOT NULL,
                `activation_score` REAL NOT NULL,
                `importance_score` REAL NOT NULL,
                `filtered_reason` TEXT,
                `injected` INTEGER NOT NULL,
                `token_cost` INTEGER NOT NULL,
                PRIMARY KEY(`trace_id`, `atom_id`),
                FOREIGN KEY(`trace_id`) REFERENCES `memory_recall_trace`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_memory_recall_candidate_trace_id` " +
                "ON `memory_recall_candidate` (`trace_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_memory_recall_candidate_atom_id` " +
                "ON `memory_recall_candidate` (`atom_id`)"
        )
    }
}

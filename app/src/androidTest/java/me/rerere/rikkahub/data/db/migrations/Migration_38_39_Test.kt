package me.rerere.rikkahub.data.db.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import me.rerere.rikkahub.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration_38_39_Test {
    private val testDb = "migration-38-39-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        RequerySQLiteOpenHelperFactory(),
    )

    @Test
    fun preservesLegacyRecallAndStartsSurfacingCountersEmpty() {
        helper.createDatabase(testDb, 38).use { db ->
            db.execSQL(
                "INSERT INTO memory_atom (id, memory_id, statement, recall_count, last_recalled_at, created_at, updated_at) " +
                    "VALUES ('atom', 'memory', 'legacy', 3, 123, 1, 1)"
            )
            db.execSQL(
                "INSERT INTO memory_recall_trace " +
                    "(id, memory_id, query, created_at, token_budget, injected_content, injected_tokens, destination) " +
                    "VALUES ('trace', 'memory', 'legacy', 123, 100, 'legacy', 5, 'OPTIONAL_RECALL')"
            )
        }

        val db = helper.runMigrationsAndValidate(testDb, 39, true, Migration_38_39)
        db.query(
            "SELECT recall_count, last_recalled_at, surface_count, last_surfaced_at FROM memory_atom WHERE id = 'atom'"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(3, cursor.getInt(0))
            assertEquals(123L, cursor.getLong(1))
            assertEquals(0, cursor.getInt(2))
            assertNull(if (cursor.isNull(3)) null else cursor.getLong(3))
        }
        db.query("SELECT mode FROM memory_recall_trace WHERE id = 'trace'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("LEGACY_RECALL", cursor.getString(0))
        }
        db.close()
    }
}

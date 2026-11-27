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
class Migration_34_37_Test {
    private val testDb = "migration-34-37-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        RequerySQLiteOpenHelperFactory(),
    )

    @Test
    fun addsSparseAtomsWithoutBackfillingUnknownEventTime() {
        helper.createDatabase(testDb, 34).close()
        val db = helper.runMigrationsAndValidate(
            testDb,
            37,
            true,
            Migration_34_35,
            Migration_35_36,
            Migration_36_37,
        )
        db.execSQL(
            "INSERT INTO memory_atom (id, memory_id, statement, imported_at, created_at, updated_at) " +
                "VALUES ('stable-id', 'memory', 'Imported without an event time', 200, 100, 100)"
        )
        db.query("SELECT id, statement, occurred_at, imported_at FROM memory_atom").use { cursor ->
            cursor.moveToFirst()
            assertEquals("stable-id", cursor.getString(0))
            assertEquals("Imported without an event time", cursor.getString(1))
            assertNull(if (cursor.isNull(2)) null else cursor.getLong(2))
            assertEquals(200, cursor.getLong(3))
        }
        db.close()
    }
}

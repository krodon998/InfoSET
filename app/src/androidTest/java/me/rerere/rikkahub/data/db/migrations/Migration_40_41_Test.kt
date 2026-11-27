package me.rerere.rikkahub.data.db.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseFactory
import me.rerere.rikkahub.data.db.entity.LifeSurfaceItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration_40_41_Test {
    private val testDb = "migration-40-41-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        RequerySQLiteOpenHelperFactory(),
    )

    @Test
    fun autoMigrationAddsLifeSurfaceTablesAndForeignKeys() = runBlocking {
        helper.createDatabase(testDb, 40).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val room = AppDatabaseFactory.create(context, testDb)
        try {
            room.openHelper.writableDatabase.query("PRAGMA user_version").use { cursor ->
                cursor.moveToFirst()
                assertEquals(41, cursor.getInt(0))
            }
            for (table in listOf("life_surface_item", "life_surface_object", "life_surface_interaction")) {
                room.openHelper.writableDatabase.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                    arrayOf(table),
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(table, cursor.getString(0))
                }
            }

            val item = LifeSurfaceItemEntity(
                id = "migration-life",
                surface = "MOMENT",
                authorId = "user:self",
                authorLabel = "隐",
                body = "迁移后的此刻",
                publishAt = 100,
                sourceKind = "TEST",
                createdAt = 100,
                updatedAt = 100,
            )
            room.lifeSurfaceDao().insertItem(item)
            assertEquals(item.id, room.lifeSurfaceDao().getItem(item.id)?.id)
        } finally {
            room.close()
        }
    }
}

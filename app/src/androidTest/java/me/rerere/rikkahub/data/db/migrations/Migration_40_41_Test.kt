package me.rerere.rikkahub.data.db.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseFactory
import me.rerere.rikkahub.data.db.entity.DynamicPostEntity
import me.rerere.rikkahub.data.db.entity.DynamicPostStatuses
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
    fun autoMigrationAddsDynamicPostTables() = runBlocking {
        helper.createDatabase(testDb, 40).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val room = AppDatabaseFactory.create(context, testDb)
        try {
            room.openHelper.writableDatabase.query("PRAGMA user_version").use { cursor ->
                cursor.moveToFirst()
                assertEquals(41, cursor.getInt(0))
            }
            for (table in listOf("dynamic_post", "dynamic_post_object", "dynamic_post_interaction")) {
                room.openHelper.writableDatabase.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                    arrayOf(table),
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(table, cursor.getString(0))
                }
            }

            val post = DynamicPostEntity(
                id = "migration-post",
                authorId = "assistant:aire",
                authorLabel = "Aire",
                body = "迁移后的动态",
                status = DynamicPostStatuses.PUBLISHED,
                publishedAt = 100,
                sourceKind = "TEST",
                createdAt = 100,
                updatedAt = 100,
            )
            room.dynamicPostDao().insertPost(post)
            assertEquals(post.id, room.dynamicPostDao().getPost(post.id)?.id)
        } finally {
            room.close()
        }
    }
}

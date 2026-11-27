package me.rerere.rikkahub.data.db.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseFactory
import me.rerere.rikkahub.data.db.entity.MoodEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration_39_40_Test {
    private val testDb = "migration-39-40-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        RequerySQLiteOpenHelperFactory(),
    )

    @Test
    fun autoMigrationAddsMoodHistoryWithoutTouchingExistingSchema() = runBlocking {
        helper.createDatabase(testDb, 39).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val room = AppDatabaseFactory.create(context, testDb)
        try {
            room.openHelper.writableDatabase.query("PRAGMA user_version").use { cursor ->
                cursor.moveToFirst()
                assertEquals(40, cursor.getInt(0))
            }
            room.openHelper.writableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='mood_event'"
            ).use { cursor ->
                assertNotNull(cursor.takeIf { it.moveToFirst() }?.getString(0))
            }

            val event = MoodEventEntity(
                id = "migration-mood",
                actorId = "assistant:aire",
                actorLabel = "Aire",
                type = "calm",
                startedAt = 100,
                source = "TEST",
                createdAt = 100,
                updatedAt = 100,
            )
            room.moodDao().insert(event)
            assertEquals(event.id, room.moodDao().getActiveMood(event.actorId, 101)?.id)
        } finally {
            room.close()
        }
    }
}

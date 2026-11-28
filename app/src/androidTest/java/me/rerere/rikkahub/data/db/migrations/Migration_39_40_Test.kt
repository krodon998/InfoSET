package me.rerere.rikkahub.data.db.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseFactory
import me.rerere.rikkahub.data.db.entity.SharedStateEventEntity
import me.rerere.rikkahub.data.db.entity.SharedStateKinds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    fun autoMigrationAddsSharedStateHistoryWithoutTouchingExistingSchema() = runBlocking {
        helper.createDatabase(testDb, 39).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val room = AppDatabaseFactory.create(context, testDb)
        try {
            room.openHelper.writableDatabase.query("PRAGMA user_version").use { cursor ->
                cursor.moveToFirst()
                assertTrue(cursor.getInt(0) >= 40)
            }
            room.openHelper.writableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='shared_state_event'"
            ).use { cursor ->
                assertNotNull(cursor.takeIf { it.moveToFirst() }?.getString(0))
            }

            val dao = room.sharedStateDao()
            val mood = SharedStateEventEntity(
                id = "migration-mood",
                actorId = "assistant:aire",
                actorLabel = "Aire",
                kind = SharedStateKinds.MOOD,
                value = "calm",
                startedAt = 100,
                durationMs = 1_000,
                expectedEndAt = 1_100,
                source = "TEST",
                createdAt = 100,
                updatedAt = 100,
            )
            val moment = SharedStateEventEntity(
                id = "migration-moment",
                actorId = "assistant:aire",
                actorLabel = "Aire",
                kind = SharedStateKinds.MOMENT,
                value = "reading",
                startedAt = 100,
                durationMs = 1_000,
                expectedEndAt = 1_100,
                source = "TEST",
                createdAt = 100,
                updatedAt = 100,
            )
            dao.insert(mood)
            dao.insert(moment)

            assertEquals(mood.id, dao.getActive(mood.actorId, SharedStateKinds.MOOD, 101)?.id)
            assertEquals(moment.id, dao.getActive(moment.actorId, SharedStateKinds.MOMENT, 101)?.id)
        } finally {
            room.close()
        }
    }
}

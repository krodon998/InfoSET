package me.rerere.rikkahub.data.db.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.entity.SharedStateKinds
import me.rerere.rikkahub.data.repository.INFOSET_USER_ACTOR_ID
import me.rerere.rikkahub.data.repository.SharedStateRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedStateDAOTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: SharedStateDAO
    private lateinit var repository: SharedStateRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.sharedStateDao()
        repository = SharedStateRepository(dao)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun moodAndMomentStayIndependentButShareOneDuration() = runBlocking {
        val initial = repository.update(
            actorId = INFOSET_USER_ACTOR_ID,
            actorLabel = "隐",
            mood = "困困",
            moodPresetKey = "sleepy",
            moodIntensity = 0.7,
            moment = "窝在床上",
            durationMs = 60_000,
            source = "TEST",
            now = 1_000,
        )
        assertEquals("困困", initial.mood?.value)
        assertEquals("窝在床上", initial.moment?.value)
        assertEquals(61_000L, initial.mood?.expectedEndAt)
        assertEquals(61_000L, initial.moment?.expectedEndAt)

        val originalMomentId = initial.moment!!.id

        val changedMood = repository.update(
            actorId = INFOSET_USER_ACTOR_ID,
            actorLabel = "隐",
            mood = "开心",
            durationMs = 120_000,
            source = "TEST",
            now = 10_000,
        )
        assertEquals("开心", changedMood.mood?.value)
        assertEquals("窝在床上", changedMood.moment?.value)
        assertEquals(originalMomentId, changedMood.moment?.id)
        assertEquals(130_000L, changedMood.mood?.expectedEndAt)
        assertEquals(130_000L, changedMood.moment?.expectedEndAt)

        val history = dao.observeHistory(INFOSET_USER_ACTOR_ID).first()
        assertEquals(3, history.size)
        assertEquals(2, history.count { it.kind == SharedStateKinds.MOOD })
        assertEquals(1, history.count { it.kind == SharedStateKinds.MOMENT })
        assertEquals(10_000L, history.single { it.value == "困困" }.endedAt)
    }

    @Test
    fun previousDurationBecomesNextDefaultAndExpiryClearsCurrentView() = runBlocking {
        repository.update(
            actorId = "assistant:aire",
            actorLabel = "Aire",
            moment = "在整理房间",
            durationMs = 30_000,
            source = "TEST",
            now = 1_000,
        )

        val updated = repository.update(
            actorId = "assistant:aire",
            actorLabel = "Aire",
            mood = "专注",
            source = "TEST",
            now = 5_000,
        )
        assertEquals(30_000L, updated.defaultDurationMs)
        assertEquals(35_000L, updated.mood?.expectedEndAt)
        assertEquals(35_000L, updated.moment?.expectedEndAt)

        assertNull(repository.snapshot("assistant:aire", now = 35_001).mood)
        assertNull(repository.snapshot("assistant:aire", now = 35_001).moment)

        val history = dao.observeHistory("assistant:aire").first()
        assertTrue(history.all { it.expectedEndAt == 35_000L || it.endedAt == 5_000L })
    }
}

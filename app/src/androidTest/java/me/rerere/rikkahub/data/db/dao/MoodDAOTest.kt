package me.rerere.rikkahub.data.db.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.repository.INFOSET_USER_ACTOR_ID
import me.rerere.rikkahub.data.repository.MoodRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoodDAOTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: MoodDAO
    private lateinit var repository: MoodRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.moodDao()
        repository = MoodRepository(dao)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun replacementVisibilityExpiryAndManualEndKeepMoodStateSeparateFromMemory() = runBlocking {
        repository.setMood(
            id = "user-first",
            actorId = INFOSET_USER_ACTOR_ID,
            actorLabel = "隐",
            type = "tired",
            startedAt = 100,
            visibleToPartner = true,
            source = "TEST",
        )
        repository.setMood(
            id = "user-hidden",
            actorId = INFOSET_USER_ACTOR_ID,
            actorLabel = "隐",
            type = "quiet",
            startedAt = 200,
            visibleToPartner = false,
            affectsContext = true,
            source = "TEST",
        )
        val userHistory = dao.observeHistory(INFOSET_USER_ACTOR_ID).first()
        assertEquals(2, userHistory.size)
        assertEquals(200L, userHistory.single { it.id == "user-first" }.endedAt)
        assertEquals("user-hidden", repository.currentMood(INFOSET_USER_ACTOR_ID, 250)?.id)

        repository.setMood(
            id = "assistant-private",
            actorId = "assistant:aire",
            actorLabel = "Aire",
            type = "focused",
            intensity = 0.8,
            startedAt = 300,
            visibleToPartner = false,
            affectsContext = true,
            source = "TEST",
        )
        val hiddenContext = repository.contextForAssistant("assistant:aire", now = 350)
        assertFalse(hiddenContext.contains("隐"))
        assertTrue(hiddenContext.contains("Aire"))
        assertTrue(hiddenContext.contains("focused"))

        repository.setMood(
            id = "user-visible",
            actorId = INFOSET_USER_ACTOR_ID,
            actorLabel = "隐",
            type = "sleepy",
            startedAt = 400,
            expectedEndAt = 500,
            visibleToPartner = true,
            affectsContext = true,
            source = "TEST",
        )
        val sharedContext = repository.contextForAssistant("assistant:aire", now = 450)
        assertTrue(sharedContext.contains("隐"))
        assertTrue(sharedContext.contains("sleepy"))
        assertTrue(sharedContext.contains("Aire"))

        assertNull(repository.currentMood(INFOSET_USER_ACTOR_ID, 501))
        assertFalse(repository.contextForAssistant("assistant:aire", now = 501).contains("sleepy"))

        assertEquals(1, repository.endMood("assistant:aire", endedAt = 450))
        assertNull(repository.currentMood("assistant:aire", 451))
    }
}

package me.rerere.rikkahub.data.db.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.entity.MemoryAtomEntity
import me.rerere.rikkahub.data.db.entity.MemoryRecallCandidateEntity
import me.rerere.rikkahub.data.db.entity.MemoryRecallTraceEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoryAtomDAOTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: MemoryAtomDAO

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.memoryAtomDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun sparseAtomAndRecallAuditRoundTrip() = runBlocking {
        val atom = MemoryAtomEntity(
            id = "atom-1",
            memoryId = "memory",
            statement = "A stable sentence is enough",
            createdAt = 10,
            updatedAt = 10,
        )
        dao.upsertAtom(atom)
        assertNull(dao.getAtom(atom.id)!!.occurredAt)

        val trace = MemoryRecallTraceEntity(
            id = "trace-1",
            memoryId = "memory",
            query = "stable sentence",
            createdAt = 20,
            tokenBudget = 200,
            injectedContent = atom.statement,
            injectedTokens = 8,
            destination = "OPTIONAL_RECALL",
        )
        val candidate = MemoryRecallCandidateEntity(
            traceId = trace.id,
            atomId = atom.id,
            rank = 1,
            statement = atom.statement,
            totalScore = 0.9,
            lexicalScore = 0.8,
            activationScore = 1.0,
            importanceScore = 0.5,
            injected = true,
            tokenCost = 8,
        )
        dao.recordRecall(trace, listOf(candidate), listOf(atom.id))
        dao.recordFeedback(trace.id, "HELPFUL")

        val recalled = dao.getAtom(atom.id)!!
        assertEquals(1, recalled.recallCount)
        assertEquals(20, recalled.lastRecalledAt)
        assertEquals(1, recalled.positiveFeedbackCount)
        assertEquals("HELPFUL", dao.observeRecentTraces("memory").first().single().feedback)
        assertEquals(candidate.statement, dao.getCandidates(trace.id).single().statement)
    }
}

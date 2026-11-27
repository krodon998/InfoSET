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
import me.rerere.rikkahub.data.model.MemoryAccessMode
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

        val passiveTrace = MemoryRecallTraceEntity(
            id = "trace-passive",
            memoryId = "memory",
            query = "stable sentence",
            createdAt = 20,
            tokenBudget = 200,
            injectedContent = atom.statement,
            injectedTokens = 8,
            destination = "PASSIVE_SURFACE",
            mode = MemoryAccessMode.PASSIVE_SURFACE.name,
        )
        val passiveCandidate = MemoryRecallCandidateEntity(
            traceId = passiveTrace.id,
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
        dao.recordAccess(passiveTrace, listOf(passiveCandidate), listOf(atom.id))

        val surfaced = dao.getAtom(atom.id)!!
        assertEquals(0, surfaced.recallCount)
        assertEquals(1, surfaced.surfaceCount)
        assertEquals(20L, surfaced.lastSurfacedAt)

        val activeTrace = passiveTrace.copy(
            id = "trace-active",
            createdAt = 30,
            destination = "ACTIVE_RECALL",
            mode = MemoryAccessMode.ACTIVE_RECALL.name,
        )
        val activeCandidate = passiveCandidate.copy(traceId = activeTrace.id)
        dao.recordAccess(activeTrace, listOf(activeCandidate), listOf(atom.id))
        dao.recordFeedback(activeTrace.id, "HELPFUL")
        dao.recordFeedback(activeTrace.id, "HELPFUL")

        val recalled = dao.getAtom(atom.id)!!
        assertEquals(1, recalled.recallCount)
        assertEquals(30L, recalled.lastRecalledAt)
        assertEquals(1, recalled.surfaceCount)
        assertEquals(1, recalled.positiveFeedbackCount)
        assertEquals("HELPFUL", dao.getTrace(activeTrace.id)!!.feedback)
        assertEquals(MemoryAccessMode.PASSIVE_SURFACE.name, dao.getTrace(passiveTrace.id)!!.mode)
        assertEquals(activeCandidate.statement, dao.getCandidates(activeTrace.id).single().statement)
    }
}

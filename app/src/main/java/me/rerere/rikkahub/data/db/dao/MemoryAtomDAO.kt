package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.MemoryAtomEntity
import me.rerere.rikkahub.data.db.entity.MemoryRecallCandidateEntity
import me.rerere.rikkahub.data.db.entity.MemoryRecallTraceEntity

@Dao
interface MemoryAtomDAO {
    @Query("SELECT * FROM memory_atom WHERE memory_id = :memoryId ORDER BY pinned DESC, updated_at DESC, id")
    fun observeAtoms(memoryId: String): Flow<List<MemoryAtomEntity>>

    @Query("SELECT * FROM memory_atom WHERE memory_id = :memoryId ORDER BY pinned DESC, updated_at DESC, id")
    suspend fun getAtoms(memoryId: String): List<MemoryAtomEntity>

    @Query("SELECT * FROM memory_atom WHERE id = :id")
    suspend fun getAtom(id: String): MemoryAtomEntity?

    @Query(
        "SELECT * FROM memory_atom WHERE memory_id = :memoryId " +
            "AND source_kind = :sourceKind AND source_ref = :sourceRef ORDER BY id"
    )
    suspend fun getAtomsBySource(memoryId: String, sourceKind: String, sourceRef: String): List<MemoryAtomEntity>

    @Upsert
    suspend fun upsertAtom(atom: MemoryAtomEntity)

    @Upsert
    suspend fun upsertAtoms(atoms: List<MemoryAtomEntity>)

    @Query("DELETE FROM memory_atom WHERE id = :id")
    suspend fun deleteAtom(id: String)

    @Query("DELETE FROM memory_atom WHERE memory_id = :memoryId")
    suspend fun deleteAtoms(memoryId: String)

    @Query(
        "DELETE FROM memory_atom WHERE memory_id = :memoryId " +
            "AND source_kind = :sourceKind AND source_ref = :sourceRef"
    )
    suspend fun deleteAtomsBySource(memoryId: String, sourceKind: String, sourceRef: String)

    @Transaction
    suspend fun replaceSource(
        memoryId: String,
        sourceKind: String,
        sourceRef: String,
        atoms: List<MemoryAtomEntity>,
    ) {
        deleteAtomsBySource(memoryId, sourceKind, sourceRef)
        if (atoms.isNotEmpty()) upsertAtoms(atoms)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrace(trace: MemoryRecallTraceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCandidates(candidates: List<MemoryRecallCandidateEntity>)

    @Query(
        "UPDATE memory_atom SET recall_count = recall_count + 1, last_recalled_at = :recalledAt, " +
            "activation = MIN(1.0, activation + 0.05) WHERE id IN (:atomIds)"
    )
    suspend fun markRecalled(atomIds: List<String>, recalledAt: Long)

    @Transaction
    suspend fun recordRecall(
        trace: MemoryRecallTraceEntity,
        candidates: List<MemoryRecallCandidateEntity>,
        injectedAtomIds: List<String>,
    ): Boolean {
        if (insertTrace(trace) == -1L) return false
        if (candidates.isNotEmpty()) insertCandidates(candidates)
        if (injectedAtomIds.isNotEmpty()) markRecalled(injectedAtomIds, trace.createdAt)
        return true
    }

    @Query("SELECT * FROM memory_recall_trace WHERE memory_id = :memoryId ORDER BY created_at DESC LIMIT :limit")
    fun observeRecentTraces(memoryId: String, limit: Int = 20): Flow<List<MemoryRecallTraceEntity>>

    @Query("SELECT * FROM memory_recall_trace WHERE id = :traceId")
    suspend fun getTrace(traceId: String): MemoryRecallTraceEntity?

    @Query("SELECT * FROM memory_recall_candidate WHERE trace_id = :traceId ORDER BY rank, atom_id")
    suspend fun getCandidates(traceId: String): List<MemoryRecallCandidateEntity>

    @Query("SELECT atom_id FROM memory_recall_candidate WHERE trace_id = :traceId AND injected = 1")
    suspend fun getInjectedAtomIds(traceId: String): List<String>

    @Query("UPDATE memory_recall_trace SET feedback = :feedback WHERE id = :traceId")
    suspend fun updateTraceFeedback(traceId: String, feedback: String)

    @Query("UPDATE memory_atom SET positive_feedback_count = positive_feedback_count + 1 WHERE id IN (:atomIds)")
    suspend fun incrementPositiveFeedback(atomIds: List<String>)

    @Query("UPDATE memory_atom SET negative_feedback_count = negative_feedback_count + 1 WHERE id IN (:atomIds)")
    suspend fun incrementNegativeFeedback(atomIds: List<String>)

    @Transaction
    suspend fun recordFeedback(traceId: String, feedback: String) {
        updateTraceFeedback(traceId, feedback)
        val atomIds = getInjectedAtomIds(traceId)
        if (atomIds.isEmpty()) return
        if (feedback == "HELPFUL") incrementPositiveFeedback(atomIds) else incrementNegativeFeedback(atomIds)
    }
}

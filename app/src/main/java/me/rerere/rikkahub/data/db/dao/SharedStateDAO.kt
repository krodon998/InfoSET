package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.SharedStateEventEntity

@Dao
interface SharedStateDAO {
    @Insert
    suspend fun insert(event: SharedStateEventEntity)

    @Query(
        "SELECT * FROM shared_state_event WHERE actor_id = :actorId AND kind = :kind " +
            "AND started_at <= :now AND ended_at IS NULL AND expected_end_at > :now " +
            "ORDER BY started_at DESC LIMIT 1"
    )
    suspend fun getActive(actorId: String, kind: String, now: Long): SharedStateEventEntity?

    @Query(
        "SELECT * FROM shared_state_event WHERE actor_id IN (:actorIds) " +
            "AND started_at <= :now AND ended_at IS NULL AND expected_end_at > :now " +
            "AND affects_context = 1 ORDER BY actor_id, kind, started_at DESC"
    )
    suspend fun getContextStates(actorIds: List<String>, now: Long): List<SharedStateEventEntity>

    @Query(
        "SELECT * FROM shared_state_event WHERE actor_id = :actorId " +
            "ORDER BY started_at DESC, updated_at DESC LIMIT :limit"
    )
    fun observeHistory(actorId: String, limit: Int = 100): Flow<List<SharedStateEventEntity>>

    @Query(
        "SELECT duration_ms FROM shared_state_event WHERE actor_id = :actorId " +
            "ORDER BY updated_at DESC LIMIT 1"
    )
    suspend fun getLastDurationMs(actorId: String): Long?

    @Query(
        "UPDATE shared_state_event SET ended_at = expected_end_at, updated_at = :now " +
            "WHERE actor_id = :actorId AND ended_at IS NULL AND expected_end_at <= :now"
    )
    suspend fun expireDue(actorId: String, now: Long): Int

    @Query(
        "UPDATE shared_state_event SET expected_end_at = :expectedEndAt, duration_ms = :durationMs, " +
            "updated_at = :updatedAt WHERE actor_id = :actorId AND ended_at IS NULL " +
            "AND expected_end_at > :updatedAt"
    )
    suspend fun retimeActive(
        actorId: String,
        expectedEndAt: Long,
        durationMs: Long,
        updatedAt: Long,
    ): Int

    @Query(
        "UPDATE shared_state_event SET ended_at = :endedAt, updated_at = :updatedAt " +
            "WHERE actor_id = :actorId AND kind = :kind AND ended_at IS NULL AND expected_end_at > :endedAt"
    )
    suspend fun endActiveKind(
        actorId: String,
        kind: String,
        endedAt: Long,
        updatedAt: Long,
    ): Int

    @Query(
        "UPDATE shared_state_event SET ended_at = :endedAt, updated_at = :updatedAt " +
            "WHERE actor_id = :actorId AND ended_at IS NULL AND expected_end_at > :endedAt"
    )
    suspend fun endAllActive(actorId: String, endedAt: Long, updatedAt: Long): Int

    /**
     * One edit chooses one duration for both current state kinds.
     * The unchanged kind is only retimed; the changed kind receives a new history record.
     */
    @Transaction
    suspend fun applyEdit(event: SharedStateEventEntity) {
        expireDue(event.actorId, event.startedAt)
        retimeActive(
            actorId = event.actorId,
            expectedEndAt = event.expectedEndAt,
            durationMs = event.durationMs,
            updatedAt = event.startedAt,
        )
        endActiveKind(
            actorId = event.actorId,
            kind = event.kind,
            endedAt = event.startedAt,
            updatedAt = event.updatedAt,
        )
        insert(event)
    }
}

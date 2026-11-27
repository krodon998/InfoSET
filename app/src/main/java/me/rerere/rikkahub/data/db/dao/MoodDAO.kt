package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.MoodEventEntity

@Dao
interface MoodDAO {
    @Insert
    suspend fun insert(event: MoodEventEntity)

    @Query(
        "SELECT * FROM mood_event WHERE actor_id = :actorId " +
            "AND started_at <= :now AND ended_at IS NULL " +
            "AND (expected_end_at IS NULL OR expected_end_at > :now) " +
            "ORDER BY started_at DESC LIMIT 1"
    )
    suspend fun getActiveMood(actorId: String, now: Long): MoodEventEntity?

    @Query(
        "SELECT * FROM mood_event WHERE actor_id IN (:actorIds) " +
            "AND started_at <= :now AND ended_at IS NULL " +
            "AND (expected_end_at IS NULL OR expected_end_at > :now) " +
            "AND affects_context = 1 ORDER BY actor_id, started_at DESC"
    )
    suspend fun getContextMoods(actorIds: List<String>, now: Long): List<MoodEventEntity>

    @Query(
        "SELECT * FROM mood_event WHERE actor_id = :actorId " +
            "ORDER BY started_at DESC LIMIT :limit"
    )
    fun observeHistory(actorId: String, limit: Int = 100): Flow<List<MoodEventEntity>>

    @Query(
        "UPDATE mood_event SET ended_at = " +
            "CASE WHEN expected_end_at IS NOT NULL AND expected_end_at < :endedAt " +
            "THEN expected_end_at ELSE :endedAt END, updated_at = :updatedAt " +
            "WHERE actor_id = :actorId AND ended_at IS NULL"
    )
    suspend fun endOpenMoods(actorId: String, endedAt: Long, updatedAt: Long): Int

    @Query(
        "UPDATE mood_event SET ended_at = " +
            "CASE WHEN expected_end_at IS NOT NULL AND expected_end_at < :endedAt " +
            "THEN expected_end_at ELSE :endedAt END, updated_at = :updatedAt " +
            "WHERE id = :id AND actor_id = :actorId AND ended_at IS NULL"
    )
    suspend fun endMood(id: String, actorId: String, endedAt: Long, updatedAt: Long): Int

    @Transaction
    suspend fun replaceActiveMood(event: MoodEventEntity) {
        endOpenMoods(
            actorId = event.actorId,
            endedAt = event.startedAt,
            updatedAt = event.updatedAt,
        )
        insert(event)
    }
}

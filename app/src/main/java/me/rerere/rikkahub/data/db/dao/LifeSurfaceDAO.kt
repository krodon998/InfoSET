package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.LifeSurfaceInteractionEntity
import me.rerere.rikkahub.data.db.entity.LifeSurfaceItemEntity
import me.rerere.rikkahub.data.db.entity.LifeSurfaceObjectEntity

@Dao
interface LifeSurfaceDAO {
    @Insert
    suspend fun insertItem(item: LifeSurfaceItemEntity)

    @Insert
    suspend fun insertObjects(objects: List<LifeSurfaceObjectEntity>)

    @Insert
    suspend fun insertInteraction(interaction: LifeSurfaceInteractionEntity)

    @Query("SELECT * FROM life_surface_item WHERE id = :id LIMIT 1")
    suspend fun getItem(id: String): LifeSurfaceItemEntity?

    @Query(
        "SELECT * FROM life_surface_item WHERE surface = 'MOMENT' " +
            "AND publish_at <= :now AND (expires_at IS NULL OR expires_at > :now) " +
            "ORDER BY publish_at DESC, created_at DESC LIMIT :limit"
    )
    fun observeMoments(now: Long, limit: Int = 100): Flow<List<LifeSurfaceItemEntity>>

    @Query(
        "SELECT * FROM life_surface_item WHERE surface = 'BOARD' " +
            "AND publish_at <= :now AND (expires_at IS NULL OR expires_at > :now) " +
            "AND (:targetActorId IS NULL OR target_actor_id IS NULL OR target_actor_id = :targetActorId) " +
            "ORDER BY CASE WHEN pinned_at IS NULL THEN 1 ELSE 0 END, pinned_at DESC, publish_at DESC " +
            "LIMIT :limit"
    )
    fun observeBoard(
        targetActorId: String?,
        now: Long,
        limit: Int = 100,
    ): Flow<List<LifeSurfaceItemEntity>>

    @Query("SELECT * FROM life_surface_object WHERE item_id = :itemId ORDER BY position, created_at, id")
    fun observeObjects(itemId: String): Flow<List<LifeSurfaceObjectEntity>>

    @Query(
        "SELECT * FROM life_surface_interaction WHERE item_id = :itemId " +
            "ORDER BY created_at, id"
    )
    fun observeInteractions(itemId: String): Flow<List<LifeSurfaceInteractionEntity>>

    @Query("SELECT * FROM life_surface_interaction WHERE id = :id LIMIT 1")
    suspend fun getInteraction(id: String): LifeSurfaceInteractionEntity?

    @Query(
        "DELETE FROM life_surface_interaction WHERE item_id = :itemId " +
            "AND actor_id = :actorId AND type = :type"
    )
    suspend fun deleteReaction(itemId: String, actorId: String, type: String): Int

    @Query(
        "SELECT EXISTS(SELECT 1 FROM life_surface_interaction WHERE item_id = :itemId " +
            "AND actor_id = :actorId AND type = :type LIMIT 1)"
    )
    suspend fun hasReaction(itemId: String, actorId: String, type: String): Boolean

    @Query("UPDATE life_surface_item SET pinned_at = :pinnedAt, updated_at = :updatedAt WHERE id = :id AND surface = 'BOARD'")
    suspend fun setPinnedAt(id: String, pinnedAt: Long?, updatedAt: Long): Int

    @Query(
        "UPDATE life_surface_item SET read_at = COALESCE(read_at, :readAt), updated_at = :updatedAt " +
            "WHERE id = :id AND surface = 'BOARD'"
    )
    suspend fun markRead(id: String, readAt: Long, updatedAt: Long): Int

    @Query(
        "UPDATE life_surface_item SET responded_at = COALESCE(responded_at, :respondedAt), updated_at = :updatedAt " +
            "WHERE id = :id AND surface = 'BOARD'"
    )
    suspend fun markResponded(id: String, respondedAt: Long, updatedAt: Long): Int

    @Transaction
    suspend fun insertItemWithObjects(
        item: LifeSurfaceItemEntity,
        objects: List<LifeSurfaceObjectEntity>,
    ) {
        insertItem(item)
        if (objects.isNotEmpty()) insertObjects(objects)
    }

    @Transaction
    suspend fun replaceReaction(
        interaction: LifeSurfaceInteractionEntity?,
        itemId: String,
        actorId: String,
        type: String,
    ) {
        deleteReaction(itemId, actorId, type)
        interaction?.let { insertInteraction(it) }
    }
}

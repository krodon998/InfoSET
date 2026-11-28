package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.DynamicPostEntity
import me.rerere.rikkahub.data.db.entity.DynamicPostInteractionEntity
import me.rerere.rikkahub.data.db.entity.DynamicPostObjectEntity

@Dao
interface DynamicPostDAO {
    @Insert
    suspend fun insertPost(post: DynamicPostEntity)

    @Insert
    suspend fun insertObjects(objects: List<DynamicPostObjectEntity>)

    @Insert
    suspend fun insertInteraction(interaction: DynamicPostInteractionEntity)

    @Query("SELECT * FROM dynamic_post WHERE id = :id LIMIT 1")
    suspend fun getPost(id: String): DynamicPostEntity?

    @Query(
        "SELECT * FROM dynamic_post WHERE status = 'PUBLISHED' AND deleted_at IS NULL " +
            "ORDER BY published_at DESC, created_at DESC LIMIT :limit"
    )
    fun observeTimeline(limit: Int = 100): Flow<List<DynamicPostEntity>>

    @Query(
        "SELECT * FROM dynamic_post WHERE status = 'DRAFT' AND author_id = :authorId " +
            "ORDER BY updated_at DESC LIMIT :limit"
    )
    fun observeDrafts(authorId: String, limit: Int = 100): Flow<List<DynamicPostEntity>>

    @Query(
        "SELECT * FROM dynamic_post WHERE status = 'SCHEDULED' AND author_id = :authorId " +
            "ORDER BY scheduled_at, created_at LIMIT :limit"
    )
    fun observeScheduled(authorId: String, limit: Int = 100): Flow<List<DynamicPostEntity>>

    @Query(
        "SELECT * FROM dynamic_post WHERE status = 'ARCHIVED' AND author_id = :authorId " +
            "ORDER BY archived_at DESC, published_at DESC LIMIT :limit"
    )
    fun observeArchived(authorId: String, limit: Int = 100): Flow<List<DynamicPostEntity>>

    @Query("SELECT * FROM dynamic_post_object WHERE post_id = :postId ORDER BY position, created_at, id")
    fun observeObjects(postId: String): Flow<List<DynamicPostObjectEntity>>

    @Query(
        "SELECT * FROM dynamic_post_interaction WHERE post_id = :postId " +
            "ORDER BY created_at, id"
    )
    fun observeInteractions(postId: String): Flow<List<DynamicPostInteractionEntity>>

    @Query("SELECT * FROM dynamic_post_interaction WHERE id = :id LIMIT 1")
    suspend fun getInteraction(id: String): DynamicPostInteractionEntity?

    @Query(
        "SELECT * FROM dynamic_post_interaction WHERE post_id = :postId AND actor_id = :actorId " +
            "AND type = 'REACTION' LIMIT 1"
    )
    suspend fun getReaction(postId: String, actorId: String): DynamicPostInteractionEntity?

    @Query(
        "SELECT EXISTS(SELECT 1 FROM dynamic_post_interaction WHERE post_id = :postId " +
            "AND actor_id = :actorId AND type = 'FAVORITE' LIMIT 1)"
    )
    suspend fun hasFavorite(postId: String, actorId: String): Boolean

    @Query("DELETE FROM dynamic_post_object WHERE post_id = :postId")
    suspend fun deleteObjects(postId: String): Int

    @Query(
        "DELETE FROM dynamic_post_interaction WHERE post_id = :postId " +
            "AND actor_id = :actorId AND type = :type"
    )
    suspend fun deleteActorInteraction(postId: String, actorId: String, type: String): Int

    @Query("DELETE FROM dynamic_post_interaction WHERE post_id = :postId")
    suspend fun deleteAllInteractions(postId: String): Int

    @Query(
        "UPDATE dynamic_post SET body = :body, status = :status, scheduled_at = :scheduledAt, " +
            "updated_at = :updatedAt WHERE id = :id AND author_id = :authorId " +
            "AND status IN ('DRAFT','SCHEDULED')"
    )
    suspend fun updatePending(
        id: String,
        authorId: String,
        body: String?,
        status: String,
        scheduledAt: Long?,
        updatedAt: Long,
    ): Int

    @Query(
        "UPDATE dynamic_post SET status = 'PUBLISHED', published_at = :publishedAt, " +
            "updated_at = :updatedAt WHERE id = :id AND author_id = :authorId " +
            "AND status IN ('DRAFT','SCHEDULED') AND deleted_at IS NULL"
    )
    suspend fun publish(
        id: String,
        authorId: String,
        publishedAt: Long,
        updatedAt: Long,
    ): Int

    @Query(
        "UPDATE dynamic_post SET status = 'PUBLISHED', published_at = :publishedAt, " +
            "updated_at = :updatedAt WHERE id = :id AND status = 'SCHEDULED' " +
            "AND scheduled_at IS NOT NULL AND scheduled_at <= :publishedAt " +
            "AND published_at IS NULL AND deleted_at IS NULL"
    )
    suspend fun publishDue(
        id: String,
        publishedAt: Long,
        updatedAt: Long,
    ): Int

    @Query(
        "UPDATE dynamic_post SET body = :body, updated_at = :updatedAt " +
            "WHERE id = :id AND author_id = :authorId AND status = 'PUBLISHED' AND deleted_at IS NULL"
    )
    suspend fun editPublishedBody(id: String, authorId: String, body: String?, updatedAt: Long): Int

    @Query(
        "UPDATE dynamic_post SET status = 'ARCHIVED', archived_at = :archivedAt, updated_at = :updatedAt " +
            "WHERE id = :id AND author_id = :authorId AND status = 'PUBLISHED' AND deleted_at IS NULL"
    )
    suspend fun archive(id: String, authorId: String, archivedAt: Long, updatedAt: Long): Int

    @Query(
        "UPDATE dynamic_post SET status = 'PUBLISHED', archived_at = NULL, updated_at = :updatedAt " +
            "WHERE id = :id AND author_id = :authorId AND status = 'ARCHIVED' AND deleted_at IS NULL"
    )
    suspend fun restoreArchive(id: String, authorId: String, updatedAt: Long): Int

    @Query(
        "UPDATE dynamic_post SET status = 'DELETED', body = NULL, deleted_at = :deletedAt, " +
            "updated_at = :updatedAt WHERE id = :id AND author_id = :authorId " +
            "AND status != 'DELETED'"
    )
    suspend fun tombstonePost(id: String, authorId: String, deletedAt: Long, updatedAt: Long): Int

    @Query(
        "UPDATE dynamic_post_interaction SET value = NULL, deleted_at = :deletedAt, updated_at = :updatedAt " +
            "WHERE id = :id AND actor_id = :actorId AND type IN ('COMMENT','REPLY') AND deleted_at IS NULL"
    )
    suspend fun tombstoneComment(id: String, actorId: String, deletedAt: Long, updatedAt: Long): Int

    @Transaction
    suspend fun insertPostWithObjects(
        post: DynamicPostEntity,
        objects: List<DynamicPostObjectEntity>,
    ) {
        insertPost(post)
        if (objects.isNotEmpty()) insertObjects(objects)
    }

    @Transaction
    suspend fun replacePendingObjects(
        postId: String,
        objects: List<DynamicPostObjectEntity>,
    ) {
        deleteObjects(postId)
        if (objects.isNotEmpty()) insertObjects(objects)
    }

    @Transaction
    suspend fun replaceActorInteraction(
        interaction: DynamicPostInteractionEntity?,
        postId: String,
        actorId: String,
        type: String,
    ) {
        deleteActorInteraction(postId, actorId, type)
        interaction?.let { insertInteraction(it) }
    }

    @Transaction
    suspend fun deletePostContent(
        postId: String,
        authorId: String,
        deletedAt: Long,
    ): Boolean {
        val changed = tombstonePost(postId, authorId, deletedAt, deletedAt) == 1
        if (!changed) return false
        deleteObjects(postId)
        deleteAllInteractions(postId)
        return true
    }
}

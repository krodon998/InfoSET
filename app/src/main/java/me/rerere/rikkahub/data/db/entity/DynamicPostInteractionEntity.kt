package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object DynamicPostInteractionTypes {
    const val REACTION = "REACTION"
    const val FAVORITE = "FAVORITE"
    const val COMMENT = "COMMENT"
    const val REPLY = "REPLY"
}

/**
 * 动态互动。
 *
 * Reaction/Favorite 保存当前状态；Comment/Reply 发出后正文固定，删除时只清正文并留下
 * deletedAt tombstone，保证后代回复仍有稳定 parentInteractionId。
 */
@Entity(
    tableName = "dynamic_post_interaction",
    foreignKeys = [
        ForeignKey(
            entity = DynamicPostEntity::class,
            parentColumns = ["id"],
            childColumns = ["post_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["post_id", "created_at"]),
        Index(value = ["parent_interaction_id"]),
        Index(value = ["post_id", "actor_id", "type"]),
        Index(value = ["source", "source_ref"]),
    ],
)
data class DynamicPostInteractionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("post_id") val postId: String,
    @ColumnInfo("parent_interaction_id") val parentInteractionId: String? = null,
    @ColumnInfo("actor_id") val actorId: String,
    @ColumnInfo("actor_label") val actorLabel: String,
    val type: String,
    val value: String? = null,
    val source: String,
    @ColumnInfo("source_ref") val sourceRef: String? = null,
    @ColumnInfo("deleted_at") val deletedAt: Long? = null,
    @ColumnInfo("created_at") val createdAt: Long,
    @ColumnInfo("updated_at") val updatedAt: Long,
)

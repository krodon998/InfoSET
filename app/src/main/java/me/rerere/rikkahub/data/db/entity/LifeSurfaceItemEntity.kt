package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * “此刻 / 留言板”共享的原始条目。
 *
 * source/original_* 永远描述最初来源；后续点赞、评论、回复写到 interaction 表，不覆盖原始条目。
 */
@Entity(
    tableName = "life_surface_item",
    indices = [
        Index(value = ["surface", "publish_at"]),
        Index(value = ["target_actor_id", "publish_at"]),
        Index(value = ["source_kind", "source_ref"]),
        Index(value = ["expires_at"]),
        Index(value = ["pinned_at"]),
    ],
)
data class LifeSurfaceItemEntity(
    @PrimaryKey val id: String,
    val surface: String,
    @ColumnInfo("author_id") val authorId: String,
    @ColumnInfo("author_label") val authorLabel: String,
    @ColumnInfo("target_actor_id") val targetActorId: String? = null,
    @ColumnInfo("target_actor_label") val targetActorLabel: String? = null,
    val body: String? = null,
    @ColumnInfo("publish_at") val publishAt: Long,
    @ColumnInfo("expires_at") val expiresAt: Long? = null,
    @ColumnInfo("pinned_at") val pinnedAt: Long? = null,
    @ColumnInfo("read_at") val readAt: Long? = null,
    @ColumnInfo("responded_at") val respondedAt: Long? = null,
    @ColumnInfo("source_kind") val sourceKind: String,
    @ColumnInfo("source_ref") val sourceRef: String? = null,
    @ColumnInfo("original_text") val originalText: String? = null,
    @ColumnInfo("original_created_at") val originalCreatedAt: Long? = null,
    @ColumnInfo("created_at") val createdAt: Long,
    @ColumnInfo("updated_at") val updatedAt: Long,
)

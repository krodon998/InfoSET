package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object DynamicPostStatuses {
    const val DRAFT = "DRAFT"
    const val SCHEDULED = "SCHEDULED"
    const val PUBLISHED = "PUBLISHED"
    const val ARCHIVED = "ARCHIVED"
    const val DELETED = "DELETED"

    val ALL = setOf(DRAFT, SCHEDULED, PUBLISHED, ARCHIVED, DELETED)
}

/**
 * 「此刻」动态的唯一帖子正本。
 *
 * Mood / Moment 属于 shared_state_event，不存进这里。
 * scheduledAt 保存原计划时间；publishedAt 永远记录真正发布时间。
 */
@Entity(
    tableName = "dynamic_post",
    indices = [
        Index(value = ["status", "published_at"]),
        Index(value = ["status", "scheduled_at"]),
        Index(value = ["author_id", "created_at"]),
        Index(value = ["source_kind", "source_ref"]),
    ],
)
data class DynamicPostEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("author_id") val authorId: String,
    @ColumnInfo("author_label") val authorLabel: String,
    val body: String? = null,
    val status: String,
    @ColumnInfo("scheduled_at") val scheduledAt: Long? = null,
    @ColumnInfo("original_time_zone_id") val originalTimeZoneId: String? = null,
    @ColumnInfo("published_at") val publishedAt: Long? = null,
    @ColumnInfo("archived_at") val archivedAt: Long? = null,
    @ColumnInfo("deleted_at") val deletedAt: Long? = null,
    @ColumnInfo("source_kind") val sourceKind: String,
    @ColumnInfo("source_ref") val sourceRef: String? = null,
    @ColumnInfo("original_text") val originalText: String? = null,
    @ColumnInfo("original_created_at") val originalCreatedAt: Long? = null,
    @ColumnInfo("created_at") val createdAt: Long,
    @ColumnInfo("updated_at") val updatedAt: Long,
)

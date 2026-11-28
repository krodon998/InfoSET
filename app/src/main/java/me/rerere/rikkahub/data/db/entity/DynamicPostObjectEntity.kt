package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 动态附着的媒体或跨 App 对象引用。
 *
 * 只保存稳定引用，不复制来源 App 的业务正本。帖子正式发布后此列表冻结。
 */
@Entity(
    tableName = "dynamic_post_object",
    foreignKeys = [
        ForeignKey(
            entity = DynamicPostEntity::class,
            parentColumns = ["id"],
            childColumns = ["post_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["post_id", "position"]),
        Index(value = ["kind", "ref"]),
    ],
)
data class DynamicPostObjectEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("post_id") val postId: String,
    val kind: String,
    val ref: String,
    val label: String? = null,
    @ColumnInfo("payload_json") val payloadJson: String? = null,
    val position: Int = 0,
    @ColumnInfo("created_at") val createdAt: Long,
)

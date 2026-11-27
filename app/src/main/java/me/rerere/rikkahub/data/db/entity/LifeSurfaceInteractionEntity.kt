package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "life_surface_interaction",
    foreignKeys = [
        ForeignKey(
            entity = LifeSurfaceItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["item_id", "created_at"]),
        Index(value = ["parent_interaction_id"]),
        Index(value = ["item_id", "actor_id", "type"]),
        Index(value = ["source", "source_ref"]),
    ],
)
data class LifeSurfaceInteractionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("item_id") val itemId: String,
    @ColumnInfo("parent_interaction_id") val parentInteractionId: String? = null,
    @ColumnInfo("actor_id") val actorId: String,
    @ColumnInfo("actor_label") val actorLabel: String,
    val type: String,
    val body: String? = null,
    val source: String,
    @ColumnInfo("source_ref") val sourceRef: String? = null,
    @ColumnInfo("created_at") val createdAt: Long,
    @ColumnInfo("updated_at") val updatedAt: Long,
)

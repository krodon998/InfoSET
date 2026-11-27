package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "life_surface_object",
    foreignKeys = [
        ForeignKey(
            entity = LifeSurfaceItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["item_id", "position"]),
        Index(value = ["kind", "ref"]),
    ],
)
data class LifeSurfaceObjectEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("item_id") val itemId: String,
    val kind: String,
    val ref: String,
    val label: String? = null,
    @ColumnInfo("payload_json") val payloadJson: String? = null,
    val position: Int = 0,
    @ColumnInfo("created_at") val createdAt: Long,
)

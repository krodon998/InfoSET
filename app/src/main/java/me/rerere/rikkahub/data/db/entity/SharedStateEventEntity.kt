package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One time-bounded shared status record.
 *
 * MOOD and MOMENT are independent values with independent history. They share the duration selected
 * by one edit operation; refreshing only one value retimes the other active value without cloning it.
 * Shared state is dynamic context, not long-term memory.
 */
@Entity(
    tableName = "shared_state_event",
    indices = [
        Index(value = ["actor_id", "kind", "started_at"]),
        Index(value = ["actor_id", "kind", "ended_at"]),
        Index(value = ["actor_id", "updated_at"]),
        Index(value = ["affects_context", "ended_at"]),
        Index(value = ["source", "source_ref"]),
    ],
)
data class SharedStateEventEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("actor_id") val actorId: String,
    @ColumnInfo("actor_label") val actorLabel: String,
    /** MOOD or MOMENT. Kept as a string so future state kinds can migrate explicitly. */
    val kind: String,
    /** Mood text/preset label or Moment text snapshot. */
    val value: String,
    /** Optional stable mood preset key; null means free-form mood text. */
    @ColumnInfo("preset_key") val presetKey: String? = null,
    val intensity: Double? = null,
    val emoji: String? = null,
    @ColumnInfo("started_at") val startedAt: Long,
    /** Duration selected for this edit, also used as the next edit default. */
    @ColumnInfo("duration_ms") val durationMs: Long,
    @ColumnInfo("expected_end_at") val expectedEndAt: Long,
    @ColumnInfo("ended_at") val endedAt: Long? = null,
    @ColumnInfo("visible_to_partner") val visibleToPartner: Boolean = true,
    @ColumnInfo("affects_context") val affectsContext: Boolean = true,
    val source: String,
    @ColumnInfo("source_ref") val sourceRef: String? = null,
    @ColumnInfo("created_at") val createdAt: Long,
    @ColumnInfo("updated_at") val updatedAt: Long,
)

object SharedStateKinds {
    const val MOOD = "MOOD"
    const val MOMENT = "MOMENT"
    val ALL = setOf(MOOD, MOMENT)
}

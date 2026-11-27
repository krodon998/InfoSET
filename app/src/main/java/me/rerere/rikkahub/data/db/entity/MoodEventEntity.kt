package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一段可持续、可结束的心情状态，同时也是历史记录。
 *
 * 当前状态由 ended_at 为空且未超过 expected_end_at 的最新记录决定。
 * 心情与长期记忆分离；需要影响聊天上下文时由 affects_context 显式控制。
 */
@Entity(
    tableName = "mood_event",
    indices = [
        Index(value = ["actor_id", "started_at"]),
        Index(value = ["actor_id", "ended_at"]),
        Index(value = ["affects_context", "ended_at"]),
        Index(value = ["source", "source_ref"]),
    ],
)
data class MoodEventEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("actor_id") val actorId: String,
    @ColumnInfo("actor_label") val actorLabel: String,
    val type: String,
    val intensity: Double? = null,
    @ColumnInfo("started_at") val startedAt: Long,
    @ColumnInfo("expected_end_at") val expectedEndAt: Long? = null,
    @ColumnInfo("ended_at") val endedAt: Long? = null,
    val emoji: String? = null,
    val note: String? = null,
    @ColumnInfo("visible_to_partner") val visibleToPartner: Boolean = true,
    @ColumnInfo("affects_context") val affectsContext: Boolean = true,
    val source: String,
    @ColumnInfo("source_ref") val sourceRef: String? = null,
    @ColumnInfo("created_at") val createdAt: Long,
    @ColumnInfo("updated_at") val updatedAt: Long,
)

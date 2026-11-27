package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "memory_atom",
    primaryKeys = ["id"],
    indices = [
        Index(value = ["memory_id"]),
        Index(value = ["memory_id", "state"]),
        Index(value = ["occurred_at"]),
        Index(value = ["memory_id", "source_kind", "source_ref"]),
    ],
)
data class MemoryAtomEntity(
    val id: String,
    @ColumnInfo("memory_id")
    val memoryId: String,
    val statement: String,
    @ColumnInfo("occurred_at")
    val occurredAt: Long? = null,
    @ColumnInfo("imported_at")
    val importedAt: Long? = null,
    @ColumnInfo("people_json")
    val peopleJson: String? = null,
    val place: String? = null,
    val topic: String? = null,
    val event: String? = null,
    val change: String? = null,
    val reflection: String? = null,
    @ColumnInfo("source_kind")
    val sourceKind: String? = null,
    @ColumnInfo("source_ref")
    val sourceRef: String? = null,
    @ColumnInfo("source_excerpt")
    val sourceExcerpt: String? = null,
    val importance: Double? = null,
    val stability: Double? = null,
    val confidence: Double? = null,
    @ColumnInfo("tags_json")
    val tagsJson: String? = null,
    @ColumnInfo("relations_json")
    val relationsJson: String? = null,
    @ColumnInfo(name = "recall_count", defaultValue = "0")
    val recallCount: Int = 0,
    @ColumnInfo("last_recalled_at")
    val lastRecalledAt: Long? = null,
    @ColumnInfo(name = "surface_count", defaultValue = "0")
    val surfaceCount: Int = 0,
    @ColumnInfo("last_surfaced_at")
    val lastSurfacedAt: Long? = null,
    @ColumnInfo(defaultValue = "1.0")
    val activation: Double = 1.0,
    @ColumnInfo(name = "positive_feedback_count", defaultValue = "0")
    val positiveFeedbackCount: Int = 0,
    @ColumnInfo(name = "negative_feedback_count", defaultValue = "0")
    val negativeFeedbackCount: Int = 0,
    @ColumnInfo(defaultValue = "'ACTIVE'")
    val state: String = "ACTIVE",
    @ColumnInfo(defaultValue = "0")
    val pinned: Boolean = false,
    @ColumnInfo("created_at")
    val createdAt: Long,
    @ColumnInfo("updated_at")
    val updatedAt: Long,
)

@Entity(
    tableName = "memory_recall_trace",
    primaryKeys = ["id"],
    indices = [
        Index(value = ["memory_id", "created_at"]),
        Index(value = ["conversation_id"]),
    ],
)
data class MemoryRecallTraceEntity(
    val id: String,
    @ColumnInfo("memory_id")
    val memoryId: String,
    @ColumnInfo("conversation_id")
    val conversationId: String? = null,
    val query: String,
    @ColumnInfo("created_at")
    val createdAt: Long,
    @ColumnInfo("token_budget")
    val tokenBudget: Int,
    @ColumnInfo("injected_content")
    val injectedContent: String,
    @ColumnInfo("injected_tokens")
    val injectedTokens: Int,
    val destination: String,
    @ColumnInfo(defaultValue = "'LEGACY_RECALL'")
    val mode: String = "LEGACY_RECALL",
    val feedback: String? = null,
)

@Entity(
    tableName = "memory_recall_candidate",
    primaryKeys = ["trace_id", "atom_id"],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = MemoryRecallTraceEntity::class,
            parentColumns = ["id"],
            childColumns = ["trace_id"],
            onDelete = androidx.room.ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["trace_id"]), Index(value = ["atom_id"])],
)
data class MemoryRecallCandidateEntity(
    @ColumnInfo("trace_id")
    val traceId: String,
    @ColumnInfo("atom_id")
    val atomId: String,
    val rank: Int,
    val statement: String,
    @ColumnInfo("source_summary")
    val sourceSummary: String? = null,
    @ColumnInfo("total_score")
    val totalScore: Double,
    @ColumnInfo("lexical_score")
    val lexicalScore: Double,
    @ColumnInfo("activation_score")
    val activationScore: Double,
    @ColumnInfo("importance_score")
    val importanceScore: Double,
    @ColumnInfo("filtered_reason")
    val filteredReason: String? = null,
    val injected: Boolean,
    @ColumnInfo("token_cost")
    val tokenCost: Int,
)

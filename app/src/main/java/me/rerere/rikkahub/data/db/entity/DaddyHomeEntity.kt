package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "actor_capability_grant",
    primaryKeys = ["id"],
    indices = [
        Index(value = ["actor_id", "capability", "scope_type", "scope_value"]),
        Index(value = ["actor_id", "revoked_at"]),
    ],
)
data class ActorCapabilityGrantEntity(
    val id: String,
    @ColumnInfo("actor_id") val actorId: String,
    val capability: String,
    @ColumnInfo("scope_type") val scopeType: String,
    @ColumnInfo("scope_value") val scopeValue: String,
    @ColumnInfo("granted_by") val grantedBy: String,
    @ColumnInfo("execution_source") val executionSource: String,
    @ColumnInfo("execution_account_ref") val executionAccountRef: String? = null,
    @ColumnInfo("execution_organization_ref") val executionOrganizationRef: String? = null,
    @ColumnInfo("billing_source") val billingSource: String? = null,
    @ColumnInfo("created_at") val createdAt: Long,
    @ColumnInfo("expires_at") val expiresAt: Long? = null,
    @ColumnInfo("revoked_at") val revokedAt: Long? = null,
    @ColumnInfo("revoked_by") val revokedBy: String? = null,
)

@Entity(
    tableName = "actor_audit_event",
    primaryKeys = ["id"],
    indices = [
        Index(value = ["actor_id", "occurred_at"]),
        Index(value = ["capability", "occurred_at"]),
    ],
)
data class ActorAuditEventEntity(
    val id: String,
    @ColumnInfo("actor_id") val actorId: String,
    val action: String,
    val capability: String? = null,
    @ColumnInfo("execution_source") val executionSource: String,
    @ColumnInfo("source_ref") val sourceRef: String? = null,
    @ColumnInfo("target_ref") val targetRef: String? = null,
    val result: String,
    val detail: String? = null,
    @ColumnInfo("occurred_at") val occurredAt: Long,
)

@Entity(
    tableName = "github_repository_binding",
    primaryKeys = ["id"],
    indices = [
        Index(value = ["account_id", "owner", "repository"], unique = true),
        Index(value = ["actor_id"]),
    ],
)
data class GitHubRepositoryBindingEntity(
    val id: String,
    @ColumnInfo("account_id") val accountId: Long,
    @ColumnInfo("actor_id") val actorId: String,
    val owner: String,
    val repository: String,
    @ColumnInfo("default_branch") val defaultBranch: String? = null,
    @ColumnInfo("path_prefix") val pathPrefix: String? = null,
    @ColumnInfo("workspace_id") val workspaceId: String? = null,
    @ColumnInfo("allow_branch_write") val allowBranchWrite: Boolean = false,
    @ColumnInfo("created_at") val createdAt: Long,
    @ColumnInfo("updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "activity_journal",
    primaryKeys = ["id"],
    indices = [
        Index(value = ["actor_id", "started_at"]),
        Index(value = ["category", "started_at"]),
        Index(value = ["related_kind", "related_ref"]),
    ],
)
data class ActivityJournalEntity(
    val id: String,
    @ColumnInfo("actor_id") val actorId: String,
    val category: String,
    val title: String,
    val summary: String? = null,
    @ColumnInfo("execution_source") val executionSource: String,
    @ColumnInfo("execution_account_ref") val executionAccountRef: String? = null,
    @ColumnInfo("execution_organization_ref") val executionOrganizationRef: String? = null,
    @ColumnInfo("billing_source") val billingSource: String? = null,
    @ColumnInfo("tool_names_json") val toolNamesJson: String? = null,
    @ColumnInfo("started_at") val startedAt: Long,
    @ColumnInfo("ended_at") val endedAt: Long? = null,
    @ColumnInfo("active_duration_ms") val activeDurationMs: Long? = null,
    @ColumnInfo("waiting_duration_ms") val waitingDurationMs: Long = 0,
    val result: String = "RUNNING",
    @ColumnInfo("failure_reason") val failureReason: String? = null,
    @ColumnInfo("output_refs_json") val outputRefsJson: String? = null,
    @ColumnInfo("related_kind") val relatedKind: String? = null,
    @ColumnInfo("related_ref") val relatedRef: String? = null,
    @ColumnInfo("created_at") val createdAt: Long,
    @ColumnInfo("updated_at") val updatedAt: Long,
)

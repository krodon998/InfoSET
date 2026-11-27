package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.ActivityJournalEntity
import me.rerere.rikkahub.data.db.entity.ActorAuditEventEntity
import me.rerere.rikkahub.data.db.entity.ActorCapabilityGrantEntity
import me.rerere.rikkahub.data.db.entity.GitHubRepositoryBindingEntity

@Dao
interface DaddyHomeDAO {
    @Query(
        "SELECT * FROM actor_capability_grant WHERE actor_id = :actorId " +
            "ORDER BY capability, created_at DESC"
    )
    fun observeGrants(actorId: String): Flow<List<ActorCapabilityGrantEntity>>

    @Query(
        "SELECT * FROM actor_capability_grant WHERE actor_id = :actorId AND capability = :capability " +
            "AND scope_type = :scopeType AND scope_value = :scopeValue AND revoked_at IS NULL " +
            "AND (expires_at IS NULL OR expires_at > :now) ORDER BY created_at DESC LIMIT 1"
    )
    suspend fun findActiveGrant(
        actorId: String,
        capability: String,
        scopeType: String,
        scopeValue: String,
        now: Long,
    ): ActorCapabilityGrantEntity?

    @Upsert
    suspend fun upsertGrant(grant: ActorCapabilityGrantEntity)

    @Query(
        "UPDATE actor_capability_grant SET revoked_at = :revokedAt, revoked_by = :revokedBy " +
            "WHERE actor_id = :actorId AND capability = :capability AND scope_type = :scopeType " +
            "AND scope_value = :scopeValue AND revoked_at IS NULL"
    )
    suspend fun revokeGrant(
        actorId: String,
        capability: String,
        scopeType: String,
        scopeValue: String,
        revokedAt: Long,
        revokedBy: String,
    ): Int

    @Upsert
    suspend fun upsertAuditEvent(event: ActorAuditEventEntity)

    @Query("SELECT * FROM actor_audit_event WHERE actor_id = :actorId ORDER BY occurred_at DESC LIMIT :limit")
    fun observeRecentAudit(actorId: String, limit: Int = 100): Flow<List<ActorAuditEventEntity>>

    @Upsert
    suspend fun upsertRepositoryBinding(binding: GitHubRepositoryBindingEntity)

    @Query("DELETE FROM github_repository_binding WHERE id = :id")
    suspend fun deleteRepositoryBinding(id: String)

    @Query("SELECT * FROM github_repository_binding WHERE id = :id")
    suspend fun getRepositoryBinding(id: String): GitHubRepositoryBindingEntity?

    @Query(
        "SELECT * FROM github_repository_binding WHERE actor_id = :actorId AND account_id = :accountId " +
            "AND owner = :owner COLLATE NOCASE AND repository = :repository COLLATE NOCASE LIMIT 1"
    )
    suspend fun findRepositoryBinding(
        actorId: String,
        accountId: Long,
        owner: String,
        repository: String,
    ): GitHubRepositoryBindingEntity?

    @Query("SELECT * FROM github_repository_binding WHERE actor_id = :actorId ORDER BY owner, repository")
    fun observeRepositoryBindings(actorId: String): Flow<List<GitHubRepositoryBindingEntity>>

    @Upsert
    suspend fun upsertActivity(activity: ActivityJournalEntity)

    @Query("SELECT * FROM activity_journal WHERE id = :id")
    suspend fun getActivity(id: String): ActivityJournalEntity?

    @Query("SELECT * FROM activity_journal WHERE id = :id")
    fun observeActivity(id: String): Flow<ActivityJournalEntity?>

    @Query(
        "SELECT * FROM activity_journal WHERE actor_id = :actorId AND started_at >= :fromInclusive " +
            "AND started_at < :toExclusive ORDER BY started_at DESC"
    )
    fun observeActivities(
        actorId: String,
        fromInclusive: Long,
        toExclusive: Long,
    ): Flow<List<ActivityJournalEntity>>
}

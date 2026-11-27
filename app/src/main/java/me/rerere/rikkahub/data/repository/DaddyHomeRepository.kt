package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.dao.DaddyHomeDAO
import me.rerere.rikkahub.data.db.entity.ActivityJournalEntity
import me.rerere.rikkahub.data.db.entity.ActorAuditEventEntity
import me.rerere.rikkahub.data.db.entity.ActorCapabilityGrantEntity
import me.rerere.rikkahub.data.db.entity.GitHubRepositoryBindingEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlin.math.max

const val DADDY_ACTOR_ID = "daddy"
const val HOME_GRANTOR_ID = "user:hidden"

enum class DaddyCapability(val label: String, val description: String) {
    ARCHIVE_READ("读取聊天原文", "读取获准范围内的 Conversation Archive"),
    ARCHIVE_APPEND("追加聊天记录", "新增导入记录与工具事件"),
    ARCHIVE_EDIT_VERSIONED("版本化编辑原文", "创建可回溯的新 revision"),
    ARCHIVE_TOMBSTONE("隐藏与恢复原文", "写入或撤销 tombstone"),
    ARCHIVE_PURGE("永久清除原文", "永久删除指定原文与附件"),
    MEMORY_READ("读取记忆", "读取原子记忆、来源与召回轨迹"),
    MEMORY_PROPOSE("提交记忆候选", "生成候选，不直接改稳定记忆"),
    MEMORY_WRITE("维护稳定记忆", "确认、编辑、休眠、置顶或关联原子"),
    CONTEXT_TUNE("调整 Context Composer", "调整各层预算与召回参数"),
    MODEL_SWITCH("切换模型", "选择 Provider、模型与 reasoning effort"),
    TOOL_EXECUTE("使用工具", "使用获准工具、MCP 与工作区"),
    SETTINGS_WRITE("修改设置", "修改获准的软件设置"),
    CODE_WORKSPACE_WRITE("修改代码工作区", "修改 InfoSET 或获准仓库代码"),
    BUILD_AND_PR("构建与 PR", "测试、提交分支、PR 与查看构建"),
    GITHUB_REPOSITORY_READ("读取 GitHub 仓库", "核对提交、文件、PR、Build 与 Release"),
    GITHUB_REPOSITORY_WRITE("写 GitHub 功能分支", "搬运文件、推送分支与更新 PR"),
    GITHUB_DIRECT_MAIN("直接写 main", "对指定仓库 main 直接写入"),
    BACKGROUND_WAKE("后台唤醒", "接收 heartbeat 并运行白名单活动"),
    ACTIVITY_JOURNAL_WRITE("写活动账本", "记录真实活动、结果与产出"),
}

enum class ActivityCategory(val label: String) {
    WORK("干活"),
    MAIL("看信"),
    GAME("玩游戏"),
    READING("读书"),
    OUTING("出游／钓鱼"),
    MEMORY("整理记忆"),
    ROOM("维护房间"),
    REST("休息"),
    SYSTEM("系统维护"),
}

enum class ActivityResult { RUNNING, SUCCEEDED, FAILED, CANCELLED, RESTED }

enum class ActivityPeriod { DAY, WEEK, MONTH }

data class ActivityRange(val fromInclusive: Long, val toExclusive: Long)

data class ActivityCategorySummary(
    val category: ActivityCategory,
    val activeDurationMs: Long,
    val activityCount: Int,
)

data class ActivitySummary(
    val activeDurationMs: Long,
    val previousActiveDurationMs: Long,
    val categories: List<ActivityCategorySummary>,
    val activityCount: Int,
)

class DaddyHomeRepository(
    private val dao: DaddyHomeDAO,
) {
    fun observeGrants(actorId: String = DADDY_ACTOR_ID): Flow<List<ActorCapabilityGrantEntity>> =
        dao.observeGrants(actorId)

    fun observeRecentAudit(actorId: String = DADDY_ACTOR_ID): Flow<List<ActorAuditEventEntity>> =
        dao.observeRecentAudit(actorId)

    fun observeRepositoryBindings(actorId: String = DADDY_ACTOR_ID): Flow<List<GitHubRepositoryBindingEntity>> =
        dao.observeRepositoryBindings(actorId)

    suspend fun grant(
        capability: DaddyCapability,
        scopeType: String = "GLOBAL",
        scopeValue: String = "*",
        executionSource: String = "INFOSET_UI",
        expiresAt: Long? = null,
    ) {
        val now = System.currentTimeMillis()
        val existing = dao.findActiveGrant(DADDY_ACTOR_ID, capability.name, scopeType, scopeValue, now)
        if (existing != null) return
        dao.upsertGrant(
            ActorCapabilityGrantEntity(
                id = UUID.randomUUID().toString(),
                actorId = DADDY_ACTOR_ID,
                capability = capability.name,
                scopeType = scopeType,
                scopeValue = scopeValue,
                grantedBy = HOME_GRANTOR_ID,
                executionSource = executionSource,
                createdAt = now,
                expiresAt = expiresAt,
            )
        )
        recordAudit(
            action = "GRANT",
            capability = capability,
            sourceRef = HOME_GRANTOR_ID,
            targetRef = "$scopeType:$scopeValue",
            result = "SUCCEEDED",
            detail = "Capability granted",
            executionSource = executionSource,
        )
    }

    suspend fun revoke(
        capability: DaddyCapability,
        scopeType: String = "GLOBAL",
        scopeValue: String = "*",
        executionSource: String = "INFOSET_UI",
    ) {
        val now = System.currentTimeMillis()
        val changed = dao.revokeGrant(
            actorId = DADDY_ACTOR_ID,
            capability = capability.name,
            scopeType = scopeType,
            scopeValue = scopeValue,
            revokedAt = now,
            revokedBy = HOME_GRANTOR_ID,
        )
        recordAudit(
            action = "REVOKE",
            capability = capability,
            sourceRef = HOME_GRANTOR_ID,
            targetRef = "$scopeType:$scopeValue",
            result = if (changed > 0) "SUCCEEDED" else "NO_OP",
            detail = if (changed > 0) "Capability revoked" else "No active grant",
            executionSource = executionSource,
        )
    }

    suspend fun hasCapability(
        capability: DaddyCapability,
        repositoryScope: String? = null,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (dao.findActiveGrant(DADDY_ACTOR_ID, capability.name, "GLOBAL", "*", now) != null) return true
        return repositoryScope != null &&
            dao.findActiveGrant(DADDY_ACTOR_ID, capability.name, "REPOSITORY", repositoryScope, now) != null
    }

    suspend fun requireCapability(capability: DaddyCapability, repositoryScope: String? = null) {
        check(hasCapability(capability, repositoryScope)) {
            "Daddy capability ${capability.name} is not granted for ${repositoryScope ?: "GLOBAL"}"
        }
    }

    suspend fun bindRepository(
        accountId: Long,
        owner: String,
        repository: String,
        defaultBranch: String? = null,
        pathPrefix: String? = null,
        workspaceId: String? = null,
        allowBranchWrite: Boolean = false,
    ): GitHubRepositoryBindingEntity {
        val now = System.currentTimeMillis()
        val scope = "${owner.trim()}/${repository.trim()}"
        val binding = GitHubRepositoryBindingEntity(
            id = UUID.randomUUID().toString(),
            accountId = accountId,
            actorId = DADDY_ACTOR_ID,
            owner = owner.trim(),
            repository = repository.trim(),
            defaultBranch = defaultBranch?.trim()?.takeIf(String::isNotBlank),
            pathPrefix = pathPrefix?.trim('/')?.takeIf(String::isNotBlank),
            workspaceId = workspaceId?.takeIf(String::isNotBlank),
            allowBranchWrite = allowBranchWrite,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertRepositoryBinding(binding)
        recordAudit(
            action = "BIND_REPOSITORY",
            capability = DaddyCapability.GITHUB_REPOSITORY_READ,
            sourceRef = "github-account:$accountId",
            targetRef = scope,
            result = "SUCCEEDED",
            detail = "Repository added to the controlled home workspace",
        )
        return binding
    }

    suspend fun updateRepositoryBinding(binding: GitHubRepositoryBindingEntity) {
        dao.upsertRepositoryBinding(binding.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun removeRepositoryBinding(binding: GitHubRepositoryBindingEntity) {
        dao.deleteRepositoryBinding(binding.id)
        recordAudit(
            action = "UNBIND_REPOSITORY",
            capability = DaddyCapability.GITHUB_REPOSITORY_READ,
            targetRef = "${binding.owner}/${binding.repository}",
            result = "SUCCEEDED",
            detail = "Repository removed from the controlled home workspace",
        )
    }

    suspend fun getRepositoryBinding(id: String): GitHubRepositoryBindingEntity? = dao.getRepositoryBinding(id)

    suspend fun recordAudit(
        action: String,
        capability: DaddyCapability? = null,
        sourceRef: String? = null,
        targetRef: String? = null,
        result: String,
        detail: String? = null,
        executionSource: String = "INFOSET",
    ) {
        dao.upsertAuditEvent(
            ActorAuditEventEntity(
                id = UUID.randomUUID().toString(),
                actorId = DADDY_ACTOR_ID,
                action = action,
                capability = capability?.name,
                executionSource = executionSource,
                sourceRef = sourceRef,
                targetRef = targetRef,
                result = result,
                detail = detail,
                occurredAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun startActivity(
        category: ActivityCategory,
        title: String,
        summary: String? = null,
        executionSource: String,
        toolNamesJson: String? = null,
        relatedKind: String? = null,
        relatedRef: String? = null,
    ): ActivityJournalEntity {
        requireCapability(DaddyCapability.ACTIVITY_JOURNAL_WRITE)
        val now = System.currentTimeMillis()
        return ActivityJournalEntity(
            id = UUID.randomUUID().toString(),
            actorId = DADDY_ACTOR_ID,
            category = category.name,
            title = title,
            summary = summary,
            executionSource = executionSource,
            toolNamesJson = toolNamesJson,
            startedAt = now,
            relatedKind = relatedKind,
            relatedRef = relatedRef,
            createdAt = now,
            updatedAt = now,
        ).also { dao.upsertActivity(it) }
    }

    suspend fun recordInstantActivity(
        category: ActivityCategory,
        title: String,
        summary: String? = null,
        executionSource: String,
        result: ActivityResult = ActivityResult.SUCCEEDED,
        toolNamesJson: String? = null,
        outputRefsJson: String? = null,
        relatedKind: String? = null,
        relatedRef: String? = null,
    ): ActivityJournalEntity {
        val activity = startActivity(
            category,
            title,
            summary,
            executionSource,
            toolNamesJson,
            relatedKind,
            relatedRef,
        )
        return finishActivity(
            id = activity.id,
            result = result,
            activeDurationMs = 0,
            outputRefsJson = outputRefsJson,
        )
    }

    suspend fun finishActivity(
        id: String,
        result: ActivityResult,
        activeDurationMs: Long? = null,
        waitingDurationMs: Long = 0,
        summary: String? = null,
        failureReason: String? = null,
        outputRefsJson: String? = null,
    ): ActivityJournalEntity {
        val current = requireNotNull(dao.getActivity(id)) { "Unknown activity $id" }
        val endedAt = System.currentTimeMillis()
        val measuredActive = activeDurationMs
            ?: max(0, endedAt - current.startedAt - max(0, waitingDurationMs))
        return current.copy(
            summary = summary ?: current.summary,
            endedAt = endedAt,
            activeDurationMs = max(0, measuredActive),
            waitingDurationMs = max(0, waitingDurationMs),
            result = result.name,
            failureReason = failureReason,
            outputRefsJson = outputRefsJson,
            updatedAt = endedAt,
        ).also { dao.upsertActivity(it) }
    }

    fun observeActivities(
        range: ActivityRange,
        actorId: String = DADDY_ACTOR_ID,
    ): Flow<List<ActivityJournalEntity>> = dao.observeActivities(actorId, range.fromInclusive, range.toExclusive)

    fun observeActivity(id: String): Flow<ActivityJournalEntity?> = dao.observeActivity(id)

    fun observeSummary(
        range: ActivityRange,
        previousRange: ActivityRange,
        actorId: String = DADDY_ACTOR_ID,
    ): Flow<ActivitySummary> = kotlinx.coroutines.flow.combine(
        observeActivities(range, actorId),
        observeActivities(previousRange, actorId),
    ) { current, previous -> summarizeActivities(current, previous) }
}

fun activityRange(
    period: ActivityPeriod,
    anchor: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): ActivityRange {
    val startDate = when (period) {
        ActivityPeriod.DAY -> anchor
        ActivityPeriod.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        ActivityPeriod.MONTH -> anchor.withDayOfMonth(1)
    }
    val endDate = when (period) {
        ActivityPeriod.DAY -> startDate.plusDays(1)
        ActivityPeriod.WEEK -> startDate.plusWeeks(1)
        ActivityPeriod.MONTH -> startDate.plusMonths(1)
    }
    return ActivityRange(
        fromInclusive = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
        toExclusive = endDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
    )
}

fun previousActivityRange(range: ActivityRange): ActivityRange {
    val duration = range.toExclusive - range.fromInclusive
    return ActivityRange(range.fromInclusive - duration, range.fromInclusive)
}

fun previousActivityRange(
    period: ActivityPeriod,
    anchor: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): ActivityRange = activityRange(
    period = period,
    anchor = when (period) {
        ActivityPeriod.DAY -> anchor.minusDays(1)
        ActivityPeriod.WEEK -> anchor.minusWeeks(1)
        ActivityPeriod.MONTH -> anchor.minusMonths(1)
    },
    zoneId = zoneId,
)

fun summarizeActivities(
    current: List<ActivityJournalEntity>,
    previous: List<ActivityJournalEntity> = emptyList(),
): ActivitySummary {
    val completed = current.filter { it.endedAt != null }
    val categorySummaries = completed
        .groupBy { runCatching { ActivityCategory.valueOf(it.category) }.getOrDefault(ActivityCategory.SYSTEM) }
        .map { (category, activities) ->
            ActivityCategorySummary(
                category = category,
                activeDurationMs = activities.sumOf { max(0, it.activeDurationMs ?: 0) },
                activityCount = activities.size,
            )
        }
        .sortedByDescending(ActivityCategorySummary::activeDurationMs)
    return ActivitySummary(
        activeDurationMs = categorySummaries.sumOf(ActivityCategorySummary::activeDurationMs),
        previousActiveDurationMs = previous.filter { it.endedAt != null }.sumOf { max(0, it.activeDurationMs ?: 0) },
        categories = categorySummaries,
        activityCount = completed.size,
    )
}

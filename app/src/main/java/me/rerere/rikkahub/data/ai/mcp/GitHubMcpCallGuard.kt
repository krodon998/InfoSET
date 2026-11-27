package me.rerere.rikkahub.data.ai.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.ai.account.GitHubAccountManager
import me.rerere.rikkahub.data.db.entity.GitHubRepositoryBindingEntity
import me.rerere.rikkahub.data.repository.ActivityCategory
import me.rerere.rikkahub.data.repository.ActivityResult
import me.rerere.rikkahub.data.repository.DaddyCapability
import me.rerere.rikkahub.data.repository.DaddyHomeRepository

internal enum class GitHubMcpOperation { READ, WRITE, BUILD, DIRECT_MAIN }

internal data class GitHubMcpTarget(
    val owner: String,
    val repository: String,
    val branch: String?,
    val paths: List<String>,
) {
    val scope: String get() = "$owner/$repository"
}

internal data class AuthorizedGitHubMcpCall(
    val activityId: String,
    val toolName: String,
    val capability: DaddyCapability,
    val sourceRef: String,
    val targetRef: String,
)

/** Enforces Daddy grants and controlled-repository bounds around official GitHub MCP calls. */
internal class GitHubMcpCallGuard(
    private val accountManager: GitHubAccountManager,
    private val homeRepository: DaddyHomeRepository,
) {
    suspend fun authorize(toolName: String, args: JsonObject): AuthorizedGitHubMcpCall {
        val credential = accountManager.requireCredential()
        val sourceRef = "github-account:${credential.accountId}"
        val target = githubMcpTarget(args)
        if (target == null) {
            homeRepository.recordAudit(
                action = "GITHUB_MCP_${toolName.uppercase()}",
                capability = DaddyCapability.TOOL_EXECUTE,
                sourceRef = sourceRef,
                result = "DENIED",
                detail = "Missing controlled repository target",
                executionSource = "OFFICIAL_GITHUB_MCP",
            )
            error("GitHub MCP tool $toolName has no controlled repository target")
        }
        val classifiedOperation = githubMcpOperation(toolName, target.branch)
        if (classifiedOperation == null) {
            homeRepository.recordAudit(
                action = "GITHUB_MCP_${toolName.uppercase()}",
                capability = DaddyCapability.TOOL_EXECUTE,
                sourceRef = sourceRef,
                targetRef = target.scope,
                result = "DENIED",
                detail = "Unclassified tool",
                executionSource = "OFFICIAL_GITHUB_MCP",
            )
            error("GitHub MCP tool $toolName is not classified for Daddy")
        }
        val binding = homeRepository.findRepositoryBinding(credential.accountId, target.owner, target.repository)
        if (binding == null) {
            homeRepository.recordAudit(
                action = "GITHUB_MCP_${toolName.uppercase()}",
                capability = classifiedOperation.capability,
                sourceRef = sourceRef,
                targetRef = target.scope,
                result = "DENIED",
                detail = "Repository is not bound",
                executionSource = "OFFICIAL_GITHUB_MCP",
            )
            error("Repository ${target.scope} is not bound to Daddy's home")
        }
        val operation = if (
            classifiedOperation != GitHubMcpOperation.READ &&
            target.branch != null &&
            target.branch.equals(binding.defaultBranch, ignoreCase = true)
        ) {
            GitHubMcpOperation.DIRECT_MAIN
        } else {
            classifiedOperation
        }
        val capability = operation.capability
        val targetRef = buildString {
            append(target.scope)
            target.paths.firstOrNull()?.let { append('/').append(it) }
        }
        try {
            homeRepository.requireCapability(DaddyCapability.TOOL_EXECUTE)
            homeRepository.requireCapability(DaddyCapability.ACTIVITY_JOURNAL_WRITE)
            homeRepository.requireCapability(DaddyCapability.GITHUB_REPOSITORY_READ, target.scope)
            validatePaths(binding, target.paths)
            when (operation) {
                GitHubMcpOperation.READ -> Unit
                GitHubMcpOperation.WRITE -> {
                    homeRepository.requireCapability(DaddyCapability.GITHUB_REPOSITORY_WRITE, target.scope)
                    check(binding.allowBranchWrite) { "Branch writes are disabled for ${target.scope}" }
                }
                GitHubMcpOperation.BUILD -> {
                    homeRepository.requireCapability(DaddyCapability.BUILD_AND_PR, target.scope)
                    homeRepository.requireCapability(DaddyCapability.GITHUB_REPOSITORY_WRITE, target.scope)
                }
                GitHubMcpOperation.DIRECT_MAIN -> {
                    homeRepository.requireCapability(DaddyCapability.BUILD_AND_PR, target.scope)
                    homeRepository.requireCapability(DaddyCapability.GITHUB_DIRECT_MAIN, target.scope)
                }
            }
        } catch (error: Throwable) {
            homeRepository.recordAudit(
                action = "GITHUB_MCP_${toolName.uppercase()}",
                capability = capability,
                sourceRef = sourceRef,
                targetRef = targetRef,
                result = "DENIED",
                detail = error.message,
                executionSource = "OFFICIAL_GITHUB_MCP",
            )
            throw error
        }
        val activity = homeRepository.startActivity(
            category = ActivityCategory.WORK,
            title = "GitHub MCP · $toolName",
            summary = targetRef,
            executionSource = "OFFICIAL_GITHUB_MCP",
            executionAccountRef = sourceRef,
            toolNamesJson = "[\"github.$toolName\"]",
            relatedKind = "GITHUB_REPOSITORY",
            relatedRef = target.scope,
        )
        return AuthorizedGitHubMcpCall(
            activityId = activity.id,
            toolName = toolName,
            capability = capability,
            sourceRef = sourceRef,
            targetRef = targetRef,
        )
    }

    suspend fun complete(
        call: AuthorizedGitHubMcpCall,
        result: ActivityResult,
        waitingDurationMs: Long,
        failure: Throwable? = null,
    ) {
        homeRepository.finishActivity(
            id = call.activityId,
            result = result,
            activeDurationMs = 0,
            waitingDurationMs = waitingDurationMs,
            summary = if (failure == null) "${call.toolName} 完成" else "${call.toolName} 失败",
            failureReason = failure?.javaClass?.simpleName,
            outputRefsJson = "[\"activity:${call.activityId}\"]",
        )
        homeRepository.recordAudit(
            action = "GITHUB_MCP_${call.toolName.uppercase()}",
            capability = call.capability,
            sourceRef = call.sourceRef,
            targetRef = call.targetRef,
            result = result.name,
            detail = failure?.javaClass?.simpleName,
            executionSource = "OFFICIAL_GITHUB_MCP",
        )
    }

    private fun validatePaths(binding: GitHubRepositoryBindingEntity, paths: List<String>) {
        paths.forEach { path ->
            val normalized = path.trim().trim('/')
            require(normalized.isNotEmpty() && ".." !in normalized.split('/')) {
                "GitHub path escapes the controlled repository"
            }
            binding.pathPrefix?.let { prefix ->
                require(normalized == prefix || normalized.startsWith("$prefix/")) {
                    "GitHub path is outside the controlled prefix $prefix"
                }
            }
        }
    }
}

internal fun githubMcpOperation(toolName: String, branch: String?): GitHubMcpOperation? {
    val name = toolName.lowercase()
    if (name == "merge_pull_request" || branch.equals("main", true) || branch.equals("master", true)) {
        return GitHubMcpOperation.DIRECT_MAIN
    }
    if (name.contains("pull_request") || name.contains("workflow") || name.contains("actions")) {
        return if (name.isReadTool()) GitHubMcpOperation.READ else GitHubMcpOperation.BUILD
    }
    return when {
        name.isReadTool() -> GitHubMcpOperation.READ
        name.isWriteTool() -> GitHubMcpOperation.WRITE
        else -> null
    }
}

internal fun githubMcpTarget(args: JsonObject): GitHubMcpTarget? {
    var owner = args.string("owner")
    var repository = args.string("repo", "repository", "repo_name")
    if (owner == null && repository?.contains('/') == true) {
        val parts = repository.split('/', limit = 2)
        owner = parts[0]
        repository = parts[1]
    }
    if (owner.isNullOrBlank() || repository.isNullOrBlank()) return null
    val paths = buildList {
        args.string("path", "file_path")?.let(::add)
        (args["files"] as? JsonArray)?.forEach { element ->
            runCatching { element.jsonObject.string("path", "file_path") }.getOrNull()?.let(::add)
        }
    }
    return GitHubMcpTarget(
        owner = owner.trim(),
        repository = repository.trim(),
        branch = args.string("branch", "ref", "base", "base_branch"),
        paths = paths,
    )
}

private val GitHubMcpOperation.capability: DaddyCapability
    get() = when (this) {
        GitHubMcpOperation.READ -> DaddyCapability.GITHUB_REPOSITORY_READ
        GitHubMcpOperation.WRITE -> DaddyCapability.GITHUB_REPOSITORY_WRITE
        GitHubMcpOperation.BUILD -> DaddyCapability.BUILD_AND_PR
        GitHubMcpOperation.DIRECT_MAIN -> DaddyCapability.GITHUB_DIRECT_MAIN
    }

private fun String.isReadTool(): Boolean =
    startsWith("get_") || startsWith("list_") || startsWith("search_") || startsWith("read_") ||
        startsWith("fetch_") || startsWith("download_") || endsWith("_read") || endsWith("_get") ||
        endsWith("_list") || endsWith("_search")

private fun String.isWriteTool(): Boolean =
    startsWith("create_") || startsWith("update_") || startsWith("delete_") || startsWith("add_") ||
        startsWith("remove_") || startsWith("push_") || startsWith("fork_") || startsWith("run_") ||
        startsWith("request_") || startsWith("cancel_") || startsWith("rerun_") || startsWith("submit_") ||
        startsWith("dismiss_") || startsWith("lock_") || startsWith("unlock_") || startsWith("mark_") ||
        startsWith("convert_")

private fun JsonObject.string(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
    this[name]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
}

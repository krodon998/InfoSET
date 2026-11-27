package me.rerere.rikkahub.data.repository

import android.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.rerere.common.http.await
import me.rerere.rikkahub.data.ai.account.GitHubAccountManager
import me.rerere.rikkahub.data.db.entity.GitHubRepositoryBindingEntity
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.workspace.WorkspaceCommandResult
import me.rerere.workspace.WorkspaceFileEntry
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.system.measureTimeMillis

@Serializable
data class GitHubRepositorySnapshot(
    @SerialName("full_name") val fullName: String,
    @SerialName("default_branch") val defaultBranch: String,
    val private: Boolean,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
private data class GitHubContentResponse(
    val type: String,
    val path: String,
    val sha: String,
    val content: String? = null,
    val encoding: String? = null,
)

data class GitHubTextFile(
    val path: String,
    val sha: String,
    val text: String,
)

/**
 * Token-confined GitHub bridge for repositories explicitly added to Daddy's home.
 * Credentials are read only while building an Authorization header and are never returned,
 * persisted to Room, written to Activity Journal, or placed in an error message.
 */
class GitHubHomeGateway(
    private val httpClient: OkHttpClient,
    private val accountManager: GitHubAccountManager,
    private val homeRepository: DaddyHomeRepository,
    private val workspaceRepository: WorkspaceRepository,
) {
    suspend fun inspectRepository(bindingId: String): GitHubRepositorySnapshot {
        val binding = requireBinding(bindingId)
        val scope = binding.scope
        homeRepository.requireCapability(DaddyCapability.GITHUB_REPOSITORY_READ, scope)
        return auditedRequest(
            action = "GITHUB_REPOSITORY_INSPECT",
            capability = DaddyCapability.GITHUB_REPOSITORY_READ,
            binding = binding,
            method = "GET",
            url = apiUrl(binding),
        ) { body -> JsonInstant.decodeFromString(GitHubRepositorySnapshot.serializer(), body) }
            .also { snapshot ->
                homeRepository.updateRepositoryBinding(binding.copy(defaultBranch = snapshot.defaultBranch))
            }
    }

    suspend fun readTextFile(
        bindingId: String,
        path: String,
        ref: String? = null,
    ): GitHubTextFile {
        val binding = requireBinding(bindingId)
        val scope = binding.scope
        homeRepository.requireCapability(DaddyCapability.GITHUB_REPOSITORY_READ, scope)
        val controlledPath = binding.controlledPath(path)
        val query = ref?.takeIf(String::isNotBlank)?.let { "?ref=${encode(it)}" }.orEmpty()
        return auditedRequest(
            action = "GITHUB_FILE_READ",
            capability = DaddyCapability.GITHUB_REPOSITORY_READ,
            binding = binding,
            method = "GET",
            url = "${apiUrl(binding)}/contents/${encodePath(controlledPath)}$query",
            targetSuffix = controlledPath,
        ) { body ->
            val content = JsonInstant.decodeFromString(GitHubContentResponse.serializer(), body)
            check(content.type == "file") { "GitHub path is not a file" }
            check(content.encoding == "base64" && content.content != null) { "GitHub file is not UTF-8 base64 content" }
            val decoded = Base64.decode(content.content.replace("\n", ""), Base64.DEFAULT).toString(Charsets.UTF_8)
            GitHubTextFile(content.path, content.sha, decoded)
        }
    }

    suspend fun writeTextFile(
        bindingId: String,
        path: String,
        content: String,
        message: String,
        branch: String,
        currentSha: String? = null,
    ): String {
        val binding = requireBinding(bindingId)
        val scope = binding.scope
        val directMain = branch == binding.defaultBranch || branch == "main"
        val capability = if (directMain) {
            DaddyCapability.GITHUB_DIRECT_MAIN
        } else {
            DaddyCapability.GITHUB_REPOSITORY_WRITE
        }
        homeRepository.requireCapability(capability, scope)
        homeRepository.requireCapability(DaddyCapability.ACTIVITY_JOURNAL_WRITE)
        check(directMain || binding.allowBranchWrite) { "Branch writes are disabled for $scope" }
        val controlledPath = binding.controlledPath(path)
        val body = buildJsonObject {
            put("message", message)
            put("content", Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP))
            put("branch", branch)
            currentSha?.let { put("sha", it) }
        }
        var responseSha = ""
        var failure: Throwable? = null
        val activity = homeRepository.startActivity(
            category = ActivityCategory.WORK,
            title = "写入 GitHub 功能分支",
            summary = "准备写入 $scope/$controlledPath",
            executionSource = "GITHUB_HOME_GATEWAY",
            toolNamesJson = "[\"github.contents.write\"]",
            relatedKind = "GITHUB_REPOSITORY",
            relatedRef = scope,
        )
        val waiting = measureTimeMillis {
            try {
                responseSha = auditedRequest(
                    action = "GITHUB_FILE_WRITE",
                    capability = capability,
                    binding = binding,
                    method = "PUT",
                    url = "${apiUrl(binding)}/contents/${encodePath(controlledPath)}",
                    requestBody = JsonInstant.encodeToString(JsonObject.serializer(), body),
                    targetSuffix = controlledPath,
                ) { responseBody ->
                    val json = JsonInstant.parseToJsonElement(responseBody).jsonObject
                    json["commit"]?.jsonObject?.get("sha")?.toString()?.trim('"').orEmpty()
                }
            } catch (error: Throwable) {
                failure = error
            }
        }
        homeRepository.finishActivity(
            id = activity.id,
            summary = failure?.let { "写入失败：${it.message}" } ?: "已提交 $scope/$controlledPath",
            result = if (failure == null) ActivityResult.SUCCEEDED else ActivityResult.FAILED,
            activeDurationMs = 0,
            waitingDurationMs = waiting,
            failureReason = failure?.message,
            outputRefsJson = responseSha.takeIf(String::isNotBlank)?.let { "[\"commit:$it\"]" },
        )
        failure?.let { throw it }
        return responseSha
    }

    suspend fun copyTextFileToWorkspace(
        bindingId: String,
        remotePath: String,
        destinationPath: String,
    ): WorkspaceFileEntry {
        val binding = requireBinding(bindingId)
        val scope = binding.scope
        val workspaceId = requireNotNull(binding.workspaceId) { "This repository is not linked to a workspace" }
        homeRepository.requireCapability(DaddyCapability.GITHUB_REPOSITORY_READ, scope)
        homeRepository.requireCapability(DaddyCapability.CODE_WORKSPACE_WRITE)
        homeRepository.requireCapability(DaddyCapability.TOOL_EXECUTE)
        homeRepository.requireCapability(DaddyCapability.ACTIVITY_JOURNAL_WRITE)
        val activity = homeRepository.startActivity(
            category = ActivityCategory.WORK,
            title = "搬运 GitHub 文件到工作区",
            summary = "$scope/$remotePath → workspace:$destinationPath",
            executionSource = "GITHUB_HOME_GATEWAY",
            toolNamesJson = "[\"github.contents.read\",\"workspace.write_text\"]",
            relatedKind = "GITHUB_REPOSITORY",
            relatedRef = scope,
        )
        val started = System.currentTimeMillis()
        return try {
            val remote = readTextFile(bindingId, remotePath)
            val output = workspaceRepository.writeText(workspaceId, destinationPath, remote.text, overwrite = true)
            val elapsed = System.currentTimeMillis() - started
            homeRepository.finishActivity(
                id = activity.id,
                result = ActivityResult.SUCCEEDED,
                activeDurationMs = 0,
                waitingDurationMs = elapsed,
                summary = "已搬运 ${remote.path} 到 ${output.path}",
                outputRefsJson = "[\"workspace:${output.path}\",\"github:${remote.sha}\"]",
            )
            homeRepository.recordAudit(
                action = "GITHUB_FILE_COPY_TO_WORKSPACE",
                capability = DaddyCapability.CODE_WORKSPACE_WRITE,
                sourceRef = "$scope/${remote.path}@${remote.sha}",
                targetRef = "workspace:$workspaceId/${output.path}",
                result = "SUCCEEDED",
                executionSource = "GITHUB_HOME_GATEWAY",
            )
            output
        } catch (error: Throwable) {
            val elapsed = System.currentTimeMillis() - started
            homeRepository.finishActivity(
                id = activity.id,
                result = ActivityResult.FAILED,
                activeDurationMs = 0,
                waitingDurationMs = elapsed,
                summary = "搬运失败",
                failureReason = error.message,
            )
            throw error
        }
    }

    suspend fun runWorkspaceCommand(
        bindingId: String,
        command: String,
        cwd: String = "",
    ): WorkspaceCommandResult {
        val binding = requireBinding(bindingId)
        val scope = binding.scope
        val workspaceId = requireNotNull(binding.workspaceId) { "This repository is not linked to a workspace" }
        homeRepository.requireCapability(DaddyCapability.TOOL_EXECUTE)
        homeRepository.requireCapability(DaddyCapability.CODE_WORKSPACE_WRITE)
        homeRepository.requireCapability(DaddyCapability.BUILD_AND_PR, scope)
        homeRepository.requireCapability(DaddyCapability.ACTIVITY_JOURNAL_WRITE)
        val activity = homeRepository.startActivity(
            category = ActivityCategory.WORK,
            title = "调试受控工作区",
            summary = "在 $scope 的工作区运行命令",
            executionSource = "CONTROLLED_WORKSPACE",
            toolNamesJson = "[\"workspace.execute_command\"]",
            relatedKind = "GITHUB_REPOSITORY",
            relatedRef = scope,
        )
        val started = System.currentTimeMillis()
        return try {
            val result = workspaceRepository.executeCommand(workspaceId, command, cwd)
            val elapsed = System.currentTimeMillis() - started
            val status = if (result.exitCode == 0 && !result.timedOut) ActivityResult.SUCCEEDED else ActivityResult.FAILED
            homeRepository.finishActivity(
                id = activity.id,
                result = status,
                activeDurationMs = 0,
                waitingDurationMs = elapsed,
                summary = "命令结束：exit ${result.exitCode}",
                failureReason = result.stderr.takeIf { status == ActivityResult.FAILED }?.take(500),
                outputRefsJson = "[\"workspace-command:${activity.id}\"]",
            )
            homeRepository.recordAudit(
                action = "WORKSPACE_COMMAND",
                capability = DaddyCapability.TOOL_EXECUTE,
                sourceRef = "workspace:$workspaceId",
                targetRef = scope,
                result = status.name,
                detail = "exit=${result.exitCode}; timedOut=${result.timedOut}",
                executionSource = "CONTROLLED_WORKSPACE",
            )
            result
        } catch (error: Throwable) {
            val elapsed = System.currentTimeMillis() - started
            homeRepository.finishActivity(
                id = activity.id,
                result = ActivityResult.FAILED,
                activeDurationMs = 0,
                waitingDurationMs = elapsed,
                summary = "命令执行失败",
                failureReason = error.message,
            )
            throw error
        }
    }

    private suspend fun requireBinding(bindingId: String): GitHubRepositoryBindingEntity =
        requireNotNull(homeRepository.getRepositoryBinding(bindingId)) { "Repository is not bound to Daddy's home" }

    private suspend fun <T> auditedRequest(
        action: String,
        capability: DaddyCapability,
        binding: GitHubRepositoryBindingEntity,
        method: String,
        url: String,
        requestBody: String? = null,
        targetSuffix: String? = null,
        decode: (String) -> T,
    ): T {
        val credential = accountManager.requireCredential()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "${credential.tokenType} ${credential.accessToken}")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "InfoSET")
            .apply {
                when (method) {
                    "GET" -> get()
                    "PUT" -> put(requireNotNull(requestBody).toRequestBody(JSON_MEDIA_TYPE))
                    else -> error("Unsupported GitHub method")
                }
            }
            .build()
        val target = buildString {
            append(binding.scope)
            targetSuffix?.let { append('/').append(it) }
        }
        return try {
            val result = httpClient.newCall(request).await().use { response ->
                val body = response.body.string()
                check(response.isSuccessful) { "GitHub request failed: HTTP ${response.code}" }
                decode(body)
            }
            homeRepository.recordAudit(
                action = action,
                capability = capability,
                sourceRef = "github-account:${credential.accountId}",
                targetRef = target,
                result = "SUCCEEDED",
                executionSource = "GITHUB_HOME_GATEWAY",
            )
            result
        } catch (error: Throwable) {
            homeRepository.recordAudit(
                action = action,
                capability = capability,
                sourceRef = "github-account:${credential.accountId}",
                targetRef = target,
                result = "FAILED",
                detail = error.message,
                executionSource = "GITHUB_HOME_GATEWAY",
            )
            throw error
        }
    }

    private fun apiUrl(binding: GitHubRepositoryBindingEntity): String =
        "https://api.github.com/repos/${encode(binding.owner)}/${encode(binding.repository)}"

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun encodePath(value: String): String = value.split('/').joinToString("/") { encode(it) }

    private val GitHubRepositoryBindingEntity.scope: String get() = "$owner/$repository"

    private fun GitHubRepositoryBindingEntity.controlledPath(requested: String): String {
        val normalized = requested.trim().trim('/').also {
            require(it.isNotEmpty()) { "GitHub path is empty" }
            require(".." !in it.split('/')) { "GitHub path escapes the controlled prefix" }
        }
        return pathPrefix?.let { "$it/$normalized" } ?: normalized
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

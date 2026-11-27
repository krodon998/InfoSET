package me.rerere.rikkahub.ui.pages.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.ai.account.GitHubAccountManager
import me.rerere.rikkahub.data.ai.account.GitHubAccountStatus
import me.rerere.rikkahub.data.ai.account.ProviderAccountManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.isOfficialGitHubMcp
import me.rerere.rikkahub.data.ai.mcp.withOfficialGitHubMcp
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.entity.GitHubRepositoryBindingEntity
import me.rerere.rikkahub.data.repository.ActivityPeriod
import me.rerere.rikkahub.data.repository.DaddyCapability
import me.rerere.rikkahub.data.repository.DaddyHomeRepository
import me.rerere.rikkahub.data.repository.GitHubHomeGateway
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.repository.activityRange
import me.rerere.rikkahub.data.repository.previousActivityRange
import java.time.LocalDate
import kotlin.uuid.Uuid

class DaddyHomeVM(
    private val accountManager: GitHubAccountManager,
    private val providerAccountManager: ProviderAccountManager,
    private val repository: DaddyHomeRepository,
    private val githubGateway: GitHubHomeGateway,
    private val settingsStore: SettingsStore,
    workspaceRepository: WorkspaceRepository,
) : ViewModel() {
    val accountStatus = accountManager.status
    val grants = repository.observeGrants().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val bindings = repository.observeRepositoryBindings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val audit = repository.observeRecentAudit()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val workspaces = workspaceRepository.listFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val previewText = MutableStateFlow<String?>(null)
    val previewTitle = MutableStateFlow<String?>(null)
    val githubMcpEnabled = settingsStore.settingsFlow
        .map { settings -> settings.mcpServers.any { it.isOfficialGitHubMcp() && it.commonOptions.enable } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val chatGPTProvider = settingsStore.settingsFlow
        .map { settings -> settings.providers.filterIsInstance<ProviderSetting.ChatGPT>().firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val providerLoginStatus = providerAccountManager.loginStatus

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun startLogin(context: Context) = accountManager.startLogin(context)

    fun startChatGPTLogin(context: Context) {
        viewModelScope.launch {
            val provider = chatGPTProvider.value ?: ProviderSetting.ChatGPT(name = "ChatGPT / Codex").also { created ->
                settingsStore.update { it.copy(providers = it.providers + created) }
            }
            providerAccountManager.startLogin(provider.id, context)
        }
    }

    fun logoutChatGPT() {
        chatGPTProvider.value?.let { provider ->
            viewModelScope.launch { providerAccountManager.logout(provider.id) }
        }
    }

    fun cancelProviderLogin(providerId: Uuid) = providerAccountManager.cancelLogin(providerId)

    fun cancelLogin() = accountManager.cancelLogin()

    fun logout() {
        viewModelScope.launch {
            runCatching { accountManager.logout() }
                .onFailure { _messages.emit(it.message ?: "退出 GitHub 失败") }
        }
    }

    fun reconnect(context: Context) {
        viewModelScope.launch {
            runCatching { accountManager.logout() }
                .onSuccess { accountManager.startLogin(context) }
                .onFailure { _messages.emit(it.message ?: "重新连接 GitHub 失败") }
        }
    }

    fun setGitHubMcpEnabled(enabled: Boolean) {
        if (enabled && accountStatus.value !is GitHubAccountStatus.Connected) {
            _messages.tryEmit("先连接 GitHub 账号")
            return
        }
        viewModelScope.launch {
            runCatching {
                settingsStore.update { it.withOfficialGitHubMcp(enabled) }
            }.onSuccess {
                _messages.emit(if (enabled) "官方 GitHub MCP 已接入 Daddy" else "官方 GitHub MCP 已停用")
            }.onFailure { _messages.emit(it.message ?: "GitHub MCP 更新失败") }
        }
    }

    fun setCapability(capability: DaddyCapability, enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (enabled) repository.grant(capability) else repository.revoke(capability)
            }.onFailure { _messages.emit(it.message ?: "权限更新失败") }
        }
    }

    fun setRepositoryCapability(
        binding: GitHubRepositoryBindingEntity,
        capability: DaddyCapability,
        enabled: Boolean,
    ) {
        val scope = "${binding.owner}/${binding.repository}"
        viewModelScope.launch {
            runCatching {
                if (enabled) {
                    repository.grant(capability, "REPOSITORY", scope)
                } else {
                    repository.revoke(capability, "REPOSITORY", scope)
                }
                if (capability == DaddyCapability.GITHUB_REPOSITORY_WRITE) {
                    repository.updateRepositoryBinding(binding.copy(allowBranchWrite = enabled))
                }
            }.onFailure { _messages.emit(it.message ?: "仓库权限更新失败") }
        }
    }

    fun addRepository(owner: String, name: String, pathPrefix: String?, workspaceId: String?) {
        val account = accountStatus.value as? GitHubAccountStatus.Connected
        if (account == null) {
            _messages.tryEmit("先连接 GitHub 账号")
            return
        }
        if (owner.isBlank() || name.isBlank()) {
            _messages.tryEmit("填写 owner 和仓库名")
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.bindRepository(
                    accountId = account.accountId,
                    owner = owner,
                    repository = name,
                    pathPrefix = pathPrefix,
                    workspaceId = workspaceId,
                )
            }.onSuccess {
                _messages.emit("已加入受控仓库")
            }.onFailure { _messages.emit(it.message ?: "加入仓库失败") }
        }
    }

    fun removeRepository(binding: GitHubRepositoryBindingEntity) {
        viewModelScope.launch {
            runCatching { repository.removeRepositoryBinding(binding) }
                .onFailure { _messages.emit(it.message ?: "移除仓库失败") }
        }
    }

    fun inspectRepository(binding: GitHubRepositoryBindingEntity) {
        viewModelScope.launch {
            runCatching { githubGateway.inspectRepository(binding.id) }
                .onSuccess { _messages.emit("已核对 ${it.fullName} · ${it.defaultBranch}") }
                .onFailure { _messages.emit(it.message ?: "仓库核对失败") }
        }
    }

    fun readFile(binding: GitHubRepositoryBindingEntity, path: String) {
        viewModelScope.launch {
            runCatching { githubGateway.readTextFile(binding.id, path) }
                .onSuccess {
                    previewTitle.value = "${binding.owner}/${binding.repository}/${it.path}"
                    previewText.value = it.text
                }
                .onFailure { _messages.emit(it.message ?: "读取文件失败") }
        }
    }

    fun copyFile(binding: GitHubRepositoryBindingEntity, remotePath: String, destinationPath: String) {
        viewModelScope.launch {
            runCatching { githubGateway.copyTextFileToWorkspace(binding.id, remotePath, destinationPath) }
                .onSuccess { _messages.emit("已搬到工作区：${it.path}") }
                .onFailure { _messages.emit(it.message ?: "搬运文件失败") }
        }
    }

    fun runCommand(binding: GitHubRepositoryBindingEntity, command: String) {
        viewModelScope.launch {
            runCatching { githubGateway.runWorkspaceCommand(binding.id, command) }
                .onSuccess {
                    previewTitle.value = "${binding.owner}/${binding.repository} · exit ${it.exitCode}"
                    previewText.value = buildString {
                        append(it.stdout)
                        if (it.stderr.isNotBlank()) append("\n\n[stderr]\n").append(it.stderr)
                    }
                }
                .onFailure { _messages.emit(it.message ?: "调试命令失败") }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityJournalVM(
    private val repository: DaddyHomeRepository,
) : ViewModel() {
    val period = MutableStateFlow(ActivityPeriod.DAY)
    val anchor = MutableStateFlow(LocalDate.now())

    private val ranges = combine(period, anchor) { selectedPeriod, selectedAnchor ->
        activityRange(selectedPeriod, selectedAnchor) to previousActivityRange(selectedPeriod, selectedAnchor)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        activityRange(ActivityPeriod.DAY) to previousActivityRange(ActivityPeriod.DAY),
    )

    val activities = ranges.flatMapLatest { repository.observeActivities(it.first) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summary = ranges.flatMapLatest { selectedRanges ->
        repository.observeSummary(selectedRanges.first, selectedRanges.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectPeriod(value: ActivityPeriod) {
        period.value = value
    }

    fun move(offset: Long) {
        anchor.value = when (period.value) {
            ActivityPeriod.DAY -> anchor.value.plusDays(offset)
            ActivityPeriod.WEEK -> anchor.value.plusWeeks(offset)
            ActivityPeriod.MONTH -> anchor.value.plusMonths(offset)
        }
    }
}

class ActivityDetailVM(
    activityId: String,
    repository: DaddyHomeRepository,
) : ViewModel() {
    val activity = repository.observeActivity(activityId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

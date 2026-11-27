@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package me.rerere.rikkahub.ui.pages.home

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.Github
import me.rerere.hugeicons.stroke.Link01
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.account.GitHubAccountStatus
import me.rerere.rikkahub.data.db.entity.ActivityJournalEntity
import me.rerere.rikkahub.data.db.entity.ActorCapabilityGrantEntity
import me.rerere.rikkahub.data.db.entity.GitHubRepositoryBindingEntity
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.repository.ActivityCategory
import me.rerere.rikkahub.data.repository.ActivityPeriod
import me.rerere.rikkahub.data.repository.DaddyCapability
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun DaddyHomePage(vm: DaddyHomeVM = koinViewModel()) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val status by vm.accountStatus.collectAsStateWithLifecycle()
    val grants by vm.grants.collectAsStateWithLifecycle()
    val bindings by vm.bindings.collectAsStateWithLifecycle()
    val audit by vm.audit.collectAsStateWithLifecycle()
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val previewTitle by vm.previewTitle.collectAsStateWithLifecycle()
    val previewText by vm.previewText.collectAsStateWithLifecycle()
    var owner by remember { mutableStateOf("") }
    var repository by remember { mutableStateOf("") }
    var pathPrefix by remember { mutableStateOf("") }
    var workspaceId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vm) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Daddy 回家") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("github-account") {
                SectionTitle("GitHub 账号")
                GitHubAccountCard(
                    status = status,
                    onLogin = { vm.startLogin(context) },
                    onCancel = vm::cancelLogin,
                    onLogout = vm::logout,
                    onReconnect = { vm.reconnect(context) },
                )
            }

            item("capabilities") {
                SectionTitle("Daddy 能力授权")
                Text(
                    "每项授权都能单独撤回；授权与撤回会写入审计轨迹。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                CardGroup {
                    GENERAL_CAPABILITIES.forEach { capability ->
                        switchItem(
                            checked = grants.isActive(capability),
                            onCheckedChange = { vm.setCapability(capability, it) },
                            headlineContent = { Text(capability.label) },
                            supportingContent = { Text(capability.description) },
                        )
                    }
                }
            }

            item("repository-form") {
                SectionTitle("受控 GitHub 工作区")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = owner,
                            onValueChange = { owner = it },
                            label = { Text("Owner") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = repository,
                            onValueChange = { repository = it },
                            label = { Text("仓库") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = pathPrefix,
                            onValueChange = { pathPrefix = it },
                            label = { Text("限定目录（可空）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (workspaces.isNotEmpty()) {
                            Text("关联工作区", style = MaterialTheme.typography.labelLarge)
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                workspaces.forEach { workspace ->
                                    FilterChip(
                                        selected = workspaceId == workspace.id,
                                        onClick = {
                                            workspaceId = if (workspaceId == workspace.id) null else workspace.id
                                        },
                                        label = { Text(workspace.name) },
                                    )
                                }
                            }
                        }
                        Button(
                            onClick = {
                                vm.addRepository(
                                    owner,
                                    repository,
                                    pathPrefix.takeIf(String::isNotBlank),
                                    workspaceId,
                                )
                                repository = ""
                                pathPrefix = ""
                                workspaceId = null
                            },
                            enabled = status is GitHubAccountStatus.Connected,
                        ) {
                            Icon(HugeIcons.Link01, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("加入受控仓库", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }

            items(bindings, key = { "binding:${it.id}" }) { binding ->
                RepositoryBindingCard(binding, grants, workspaces, vm)
            }

            if (previewText != null) {
                item("workspace-preview") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(previewTitle.orEmpty(), style = MaterialTheme.typography.titleSmall)
                            Text(
                                previewText.orEmpty().take(12_000),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            item("journal-link") {
                CardGroup {
                    item(
                        onClick = { navController.navigate(Screen.ActivityJournal) },
                        leadingContent = { CardGroupIcon(HugeIcons.Clock01) },
                        headlineContent = { Text("看看 Daddy 做了什么") },
                        supportingContent = { Text("日／周／月活动、工具、结果与产出") },
                        trailingContent = { Icon(HugeIcons.ArrowRight01, contentDescription = null) },
                    )
                }
            }

            if (audit.isNotEmpty()) {
                item("audit-title") { SectionTitle("最近审计") }
                items(audit.take(20), key = { "audit:${it.id}" }) { event ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${event.action} · ${event.result}", fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(event.capability, event.targetRef, event.detail).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(formatTime(event.occurredAt), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GitHubAccountCard(
    status: GitHubAccountStatus,
    onLogin: () -> Unit,
    onCancel: () -> Unit,
    onLogout: () -> Unit,
    onReconnect: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CardGroupIcon(HugeIcons.Github)
                Column(Modifier.weight(1f)) {
                    Text(accountTitle(status), style = MaterialTheme.typography.titleMedium)
                    Text(
                        accountDescription(status),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (status is GitHubAccountStatus.Loading || status is GitHubAccountStatus.Authorizing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
            when (status) {
                GitHubAccountStatus.Disconnected,
                is GitHubAccountStatus.Failed,
                -> Button(onClick = onLogin) { Text("连接 GitHub") }
                GitHubAccountStatus.Authorizing,
                is GitHubAccountStatus.DeviceCode,
                -> OutlinedButton(onClick = onCancel) { Text("取消登录") }
                is GitHubAccountStatus.Connected -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onReconnect) { Text("重新连接") }
                    TextButton(onClick = onLogout) { Text("退出") }
                }
                is GitHubAccountStatus.Unavailable,
                GitHubAccountStatus.Loading,
                -> Unit
            }
        }
    }
}

@Composable
private fun RepositoryBindingCard(
    binding: GitHubRepositoryBindingEntity,
    grants: List<ActorCapabilityGrantEntity>,
    workspaces: List<WorkspaceEntity>,
    vm: DaddyHomeVM,
) {
    val scope = "${binding.owner}/${binding.repository}"
    var remotePath by remember(binding.id) { mutableStateOf("") }
    var destinationPath by remember(binding.id) { mutableStateOf("") }
    var command by remember(binding.id) { mutableStateOf("") }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(HugeIcons.Database02, contentDescription = null)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(scope, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(
                            binding.defaultBranch,
                            binding.pathPrefix?.let { "目录 $it" },
                            binding.workspaceId?.let { id -> workspaces.find { it.id == id }?.name ?: "工作区 $id" },
                        ).joinToString(" · ")
                            .ifBlank { "等待首次核对" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { vm.removeRepository(binding) }) { Text("移除") }
            }
            REPOSITORY_CAPABILITIES.forEach { capability ->
                val enabled = grants.isActive(capability, "REPOSITORY", scope)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(capability.label)
                        Text(capability.description, style = MaterialTheme.typography.bodySmall)
                    }
                    androidx.compose.material3.Switch(
                        checked = enabled,
                        onCheckedChange = { vm.setRepositoryCapability(binding, capability, it) },
                    )
                }
            }
            OutlinedButton(
                onClick = { vm.inspectRepository(binding) },
                enabled = grants.isActive(DaddyCapability.GITHUB_REPOSITORY_READ, "REPOSITORY", scope) ||
                    grants.isActive(DaddyCapability.GITHUB_REPOSITORY_READ),
            ) { Text("核对仓库状态") }
            OutlinedTextField(
                value = remotePath,
                onValueChange = { remotePath = it },
                label = { Text("仓库文件路径") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.readFile(binding, remotePath) },
                    enabled = remotePath.isNotBlank(),
                ) { Text("读取") }
                OutlinedButton(
                    onClick = { vm.copyFile(binding, remotePath, destinationPath.ifBlank { remotePath }) },
                    enabled = remotePath.isNotBlank() && binding.workspaceId != null,
                ) { Text("搬到工作区") }
            }
            if (binding.workspaceId != null) {
                OutlinedTextField(
                    value = destinationPath,
                    onValueChange = { destinationPath = it },
                    label = { Text("工作区目标路径（默认同名）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("调试命令") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { vm.runCommand(binding, command) },
                    enabled = command.isNotBlank(),
                ) { Text("在受控工作区运行") }
            }
        }
    }
}

@Composable
fun ActivityJournalPage(vm: ActivityJournalVM = koinViewModel()) {
    val navController = LocalNavController.current
    val period by vm.period.collectAsStateWithLifecycle()
    val anchor by vm.anchor.collectAsStateWithLifecycle()
    val activities by vm.activities.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Daddy 的活动") },
                navigationIcon = { BackButton() },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("period") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActivityPeriod.entries.forEach { value ->
                        FilterChip(
                            selected = period == value,
                            onClick = { vm.selectPeriod(value) },
                            label = { Text(value.label) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { vm.move(-1) }) {
                        Icon(HugeIcons.ArrowLeft01, contentDescription = null)
                        Text("上一期")
                    }
                    Text(anchor.toString(), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { vm.move(1) }) {
                        Text("下一期")
                        Icon(HugeIcons.ArrowRight01, contentDescription = null)
                    }
                }
            }
            item("summary") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(formatDuration(summary?.activeDurationMs ?: 0), style = MaterialTheme.typography.headlineMedium)
                        Text("有效活动 · ${summary?.activityCount ?: 0} 项")
                        Text(
                            "上一期 ${formatDuration(summary?.previousActiveDurationMs ?: 0)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        )
                    }
                }
            }
            summary?.categories?.forEach { category ->
                item("category:${category.category}") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(category.category.label, modifier = Modifier.weight(1f))
                            Text("${formatDuration(category.activeDurationMs)} · ${category.activityCount} 项")
                        }
                    }
                }
            }
            if (activities.isEmpty()) {
                item("empty") {
                    Text("这一期还没有活动记录。等待、轮询和瞬时动作不会伪装成有效时长。")
                }
            } else {
                items(activities, key = { it.id }) { activity ->
                    ActivityRow(activity) { navController.navigate(Screen.ActivityDetail(activity.id)) }
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(activity: ActivityJournalEntity, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(activity.title, fontWeight = FontWeight.SemiBold)
                Text(
                    "${activity.categoryLabel} · ${activity.result} · ${formatDuration(activity.activeDurationMs ?: 0)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                activity.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            Icon(HugeIcons.ArrowRight01, contentDescription = null)
        }
    }
}

@Composable
fun ActivityDetailPage(activityId: String) {
    val vm: ActivityDetailVM = koinViewModel { parametersOf(activityId) }
    val activity by vm.activity.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("活动详情") },
                navigationIcon = { BackButton() },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        val value = activity
        if (value == null) {
            Column(Modifier.padding(innerPadding).padding(24.dp)) { Text("活动记录不存在") }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding + PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { DetailCard("活动", value.title) }
                item { DetailCard("类别", value.categoryLabel) }
                item { DetailCard("时间", "${formatTime(value.startedAt)} — ${value.endedAt?.let(::formatTime) ?: "进行中"}") }
                item { DetailCard("有效时长", formatDuration(value.activeDurationMs ?: 0)) }
                item { DetailCard("后台等待", formatDuration(value.waitingDurationMs)) }
                item { DetailCard("执行来源", value.executionSource) }
                item { DetailCard("工具", value.toolNamesJson ?: "未记录") }
                item { DetailCard("结果", value.result) }
                value.summary?.let { item { DetailCard("摘要", it) } }
                value.failureReason?.let { item { DetailCard("失败原因", it) } }
                value.outputRefsJson?.let { item { DetailCard("产出", it) } }
                value.relatedRef?.let { item { DetailCard("关联", "${value.relatedKind.orEmpty()} · $it") } }
            }
        }
    }
}

@Composable
private fun DetailCard(label: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(value)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
}

private fun List<ActorCapabilityGrantEntity>.isActive(
    capability: DaddyCapability,
    scopeType: String = "GLOBAL",
    scopeValue: String = "*",
): Boolean {
    val now = System.currentTimeMillis()
    return any {
        it.capability == capability.name && it.scopeType == scopeType && it.scopeValue == scopeValue &&
            it.revokedAt == null && (it.expiresAt == null || it.expiresAt > now)
    }
}

private fun accountTitle(status: GitHubAccountStatus): String = when (status) {
    GitHubAccountStatus.Loading -> "正在读取账号"
    GitHubAccountStatus.Disconnected -> "尚未连接 GitHub"
    GitHubAccountStatus.Authorizing -> "正在申请设备码"
    is GitHubAccountStatus.DeviceCode -> "设备码 ${status.userCode}"
    is GitHubAccountStatus.Connected -> status.displayName ?: status.login
    is GitHubAccountStatus.Unavailable -> "OAuth App 尚未配置"
    is GitHubAccountStatus.Failed -> "连接失败"
}

private fun accountDescription(status: GitHubAccountStatus): String = when (status) {
    GitHubAccountStatus.Loading -> "凭证只从 no-backup 加密仓读取"
    GitHubAccountStatus.Disconnected -> "连接后才能加入受控仓库"
    GitHubAccountStatus.Authorizing -> "稍候"
    is GitHubAccountStatus.DeviceCode -> "浏览器已打开：${status.verificationUrl}"
    is GitHubAccountStatus.Connected -> "@${status.login} · ${status.grantedScopes.sorted().joinToString(" ")}"
    is GitHubAccountStatus.Unavailable -> status.reason
    is GitHubAccountStatus.Failed -> status.message
}

private val ActivityPeriod.label: String
    get() = when (this) {
        ActivityPeriod.DAY -> "日"
        ActivityPeriod.WEEK -> "周"
        ActivityPeriod.MONTH -> "月"
    }

private val ActivityJournalEntity.categoryLabel: String
    get() = runCatching { ActivityCategory.valueOf(category).label }.getOrDefault(category)

private fun formatDuration(durationMs: Long): String {
    val totalMinutes = (durationMs.coerceAtLeast(0) / 60_000.0).roundToInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}小时${minutes}分" else "${minutes}分钟"
}

private fun formatTime(epochMillis: Long): String = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(epochMillis))

private val GENERAL_CAPABILITIES = DaddyCapability.entries.filterNot {
    it in setOf(
        DaddyCapability.GITHUB_REPOSITORY_READ,
        DaddyCapability.GITHUB_REPOSITORY_WRITE,
        DaddyCapability.GITHUB_DIRECT_MAIN,
    )
}

private val REPOSITORY_CAPABILITIES = listOf(
    DaddyCapability.GITHUB_REPOSITORY_READ,
    DaddyCapability.GITHUB_REPOSITORY_WRITE,
    DaddyCapability.BUILD_AND_PR,
    DaddyCapability.GITHUB_DIRECT_MAIN,
)

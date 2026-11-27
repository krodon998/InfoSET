package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ai.ModelField
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.ReasoningButton
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.TagsInput
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.hooks.heroAnimation
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.toFixed
import me.rerere.ui.components.Select
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt
import kotlin.uuid.Uuid
import me.rerere.rikkahub.data.model.Tag as DataTag

@Composable
fun AssistantBasicPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_tab_basic))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        AssistantBasicContent(
            innerPadding = innerPadding,
            assistant = assistant,
            providers = providers,
            tags = tags,
            workspaces = workspaces,
            onUpdate = { vm.update(it) },
            vm = vm
        )
    }
}

@Composable
internal fun AssistantBasicContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    providers: List<me.rerere.ai.provider.ProviderSetting>,
    tags: List<DataTag>,
    workspaces: List<WorkspaceEntity>,
    onUpdate: (Assistant) -> Unit,
    vm: AssistantDetailVM
) {
    val modelListState = rememberModelListState(
        modelId = assistant.chatModelId,
        providers = providers,
        type = ModelType.CHAT,
    )
    ModelListSheet(
        state = modelListState,
        onSelect = { onUpdate(assistant.copy(chatModelId = it.id)) },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(innerPadding)
            .padding(bottom = 16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            UIAvatar(
                value = assistant.avatar,
                name = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                onUpdate = { avatar ->
                    onUpdate(
                        assistant.copy(
                            avatar = avatar
                        )
                    )
                },
                modifier = Modifier
                    .size(104.dp)
                    .heroAnimation("assistant_${assistant.id}")
            )
        }

        CardGroup {
            formItem(
                headlineContent = { Text(stringResource(R.string.assistant_page_name)) },
            ) {
                OutlinedTextField(
                    value = assistant.name,
                    onValueChange = {
                        onUpdate(
                            assistant.copy(
                                name = it
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            formItem(
                headlineContent = { Text(stringResource(R.string.assistant_page_tags)) },
            ) {
                TagsInput(
                    value = assistant.tags,
                    tags = tags,
                    onValueChange = { tagIds, tagList ->
                        vm.updateTags(tagIds, tagList)
                    },
                )
            }
            formItem(
                headlineContent = { Text(stringResource(R.string.assistant_page_workspace)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_workspace_desc)) },
            ) {
                val selectedWorkspace = workspaces.find { it.id == assistant.workspaceId?.toString() }
                Select(
                    options = listOf<WorkspaceEntity?>(null) + workspaces,
                    selectedOption = selectedWorkspace,
                    onOptionSelected = { workspace ->
                        onUpdate(
                            assistant.copy(
                                workspaceId = workspace?.id?.let { Uuid.parse(it) }
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    optionToString = { workspace ->
                        workspace?.name ?: stringResource(R.string.workspace_no_binding)
                    },
                )
            }
            switchItem(
                checked = assistant.useAssistantAvatar,
                onCheckedChange = {
                    onUpdate(
                        assistant.copy(
                            useAssistantAvatar = it
                        )
                    )
                },
                supportingContent = { Text(stringResource(R.string.assistant_page_use_assistant_avatar_desc)) },
                headlineContent = { Text(stringResource(R.string.assistant_page_use_assistant_avatar)) },
            )
        }

        CardGroup {
            formItem(
                headlineContent = { Text(stringResource(R.string.assistant_page_chat_model)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_chat_model_desc)) },
            ) {
                ModelField(
                    model = modelListState.currentModel,
                    onClick = { modelListState.open() },
                )
            }
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_thinking_budget)) },
                trailingContent = {
                    ReasoningButton(
                        reasoningLevel = assistant.reasoningLevel,
                        onUpdateReasoningLevel = { level ->
                            onUpdate(assistant.copy(reasoningLevel = level))
                        }
                    )
                },
            )
            item(
                headlineContent = { Text(stringResource(R.string.assistant_context_budget)) },
                supportingContent = { Text(stringResource(R.string.assistant_context_budget_auto_desc)) },
                trailingContent = { Text(stringResource(R.string.auto)) },
            )
            item(
                headlineContent = { Text(stringResource(R.string.assistant_max_output_tokens)) },
                supportingContent = { Text(stringResource(R.string.assistant_max_output_tokens_auto_desc)) },
                trailingContent = { Text(stringResource(R.string.auto)) },
            )
        }

        CardGroup {
            switchItem(
                checked = assistant.useGradientBackground,
                onCheckedChange = {
                    onUpdate(
                        assistant.copy(
                            useGradientBackground = it
                        )
                    )
                },
                supportingContent = { Text(stringResource(R.string.assistant_page_gradient_background_desc)) },
                headlineContent = { Text(stringResource(R.string.assistant_page_gradient_background)) },
            )

            if (!assistant.useGradientBackground) {
                formItem(
                    headlineContent = { Text(stringResource(R.string.assistant_page_chat_background)) },
                    supportingContent = { Text(stringResource(R.string.assistant_page_chat_background_desc)) },
                ) {
                    BackgroundPicker(
                        background = assistant.background,
                        backgroundOpacity = assistant.backgroundOpacity,
                        onUpdate = { background ->
                            onUpdate(
                                assistant.copy(
                                    background = background
                                )
                            )
                        }
                    )
                }
            }

            if (!assistant.useGradientBackground && assistant.background != null) {
                val backgroundOpacity = assistant.backgroundOpacity.coerceIn(0f, 1f)
                formItem(
                    headlineContent = { Text(stringResource(R.string.assistant_page_background_opacity)) },
                    supportingContent = { Text(stringResource(R.string.assistant_page_background_opacity_desc)) },
                    trailingContent = {
                        Text(
                            text = stringResource(
                                R.string.assistant_page_background_opacity_value,
                                (backgroundOpacity * 100).roundToInt()
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                ) {
                    Slider(
                        value = backgroundOpacity,
                        onValueChange = {
                            onUpdate(
                                assistant.copy(
                                    backgroundOpacity = it.toFixed(2).toFloatOrNull()?.coerceIn(0f, 1f) ?: 1.0f
                                )
                            )
                        },
                        valueRange = 0f..1f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

package me.rerere.rikkahub.ui.pages.chat

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ui.toast.ToastType
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.partIdAt
import me.rerere.ai.ui.partIndexOf
import me.rerere.ai.ui.stickerMetadata
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getSelectedASRProvider
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.locateReplyTarget
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.service.BackgroundShellStatus
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.service.SessionActivity
import me.rerere.rikkahub.ui.components.ai.BackgroundTasksSheet
import me.rerere.rikkahub.ui.components.ai.ChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.ui.components.ai.ChatTopBarCapsule
import me.rerere.rikkahub.ui.components.ai.FilesPicker
import me.rerere.rikkahub.ui.components.ai.SearchMode
import me.rerere.rikkahub.ui.components.ai.StickerPickerSheet
import me.rerere.rikkahub.ui.components.ai.completion.WorkspaceCompletionProvider
import me.rerere.rikkahub.ui.components.ai.rememberChatAttachmentPickerActions
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.utils.base64Decode
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

@Composable
fun ChatPage(
    id: Uuid,
    text: String?,
    files: List<Uri>,
    nodeId: Uuid? = null,
    messageId: Uuid? = null,
    partId: Uuid? = null,
) {
    val vm: ChatVM = koinViewModel(
        parameters = {
            parametersOf(id.toString())
        }
    )
    val filesManager: FilesManager = koinInject()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    val setting by vm.settings.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val processingStatus by vm.processingStatus.collectAsStateWithLifecycle()
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()

    // 新会话发出第一条消息前，跟随抽屉当前选中的文件夹（#2133）
    val drawerVm: ChatDrawerVM = koinViewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity)
    val selectedFolderId by drawerVm.selectedFolderId.collectAsStateWithLifecycle()
    LaunchedEffect(selectedFolderId) {
        vm.updateDraftFolder(selectedFolderId)
    }

    val startVoiceMode = rememberVoiceModeStarter(vm, setting)

    val inputState = vm.inputState

    // 初始化输入状态（处理传入的 files 和 text 参数）
    LaunchedEffect(files, text) {
        if (files.isNotEmpty()) {
            val localFiles = filesManager.createChatFilesByContents(files)
            val contentTypes = files.mapNotNull { file ->
                filesManager.getFileMimeType(file)
            }
            val parts = buildList {
                localFiles.forEachIndexed { index, file ->
                    val type = contentTypes.getOrNull(index)
                    if (type?.startsWith("image/") == true) {
                        add(UIMessagePart.Image(url = file.toString()))
                    } else if (type?.startsWith("video/") == true) {
                        add(UIMessagePart.Video(url = file.toString()))
                    } else if (type?.startsWith("audio/") == true) {
                        add(UIMessagePart.Audio(url = file.toString()))
                    }
                }
            }
            inputState.messageContent = parts
        }
        text?.base64Decode()?.let { decodedText ->
            if (decodedText.isNotEmpty()) {
                inputState.setMessageText(decodedText)
            }
        }
    }

    val chatListState = rememberLazyListState()
    LaunchedEffect(nodeId, messageId, partId, conversation.messageNodes.size) {
        if (!vm.chatListInitialized && conversation.messageNodes.isNotEmpty()) {
            val target = when {
                messageId != null -> conversation.messageNodes.mapIndexedNotNull { index, node ->
                    val branchIndex = node.messages.indexOfFirst { it.id == messageId }
                    if (branchIndex >= 0) Triple(index, node, branchIndex) else null
                }.firstOrNull()
                nodeId != null -> conversation.messageNodes.indexOfFirst { it.id == nodeId }
                    .takeIf { it >= 0 }
                    ?.let { index ->
                        val node = conversation.messageNodes[index]
                        Triple(index, node, node.selectIndex)
                    }
                else -> null
            }
            if (target != null) {
                val (index, node, branchIndex) = target
                if (node.selectIndex != branchIndex) {
                    vm.selectMessageNode(node.id, branchIndex)
                }
                // Resolve the part identity now so legacy messages use their deterministic stable part id.
                if (partId != null) {
                    node.messages[branchIndex].partIndexOf(partId)
                }
                chatListState.scrollToItem(index)
            } else {
                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
            }
            vm.chatListInitialized = true
        }
    }

    ChatPageContent(
        onStartVoiceMode = startVoiceMode,
        inputState = inputState,
        loadingJob = loadingJob,
        processingStatus = processingStatus,
        setting = setting,
        conversation = conversation,
        navController = navController,
        vm = vm,
        chatListState = chatListState,
        assistant = assistant,
        currentChatModel = currentChatModel,
        errors = errors,
        focusMessageId = messageId,
        focusPartId = partId,
        onDismissError = { vm.dismissError(it) },
        onClearAllErrors = { vm.clearAllErrors() },
    )
}

@Composable
private fun ChatPageContent(
    onStartVoiceMode: () -> Unit,
    inputState: ChatInputState,
    loadingJob: Job?,
    processingStatus: String? = null,
    setting: Settings,
    conversation: Conversation,
    navController: Navigator,
    vm: ChatVM,
    chatListState: LazyListState,
    assistant: Assistant,
    currentChatModel: Model?,
    errors: List<ChatError>,
    focusMessageId: Uuid? = null,
    focusPartId: Uuid? = null,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val workspaceRepository: WorkspaceRepository = koinInject()
    val filesManager: FilesManager = koinInject()
    val sessionActivity by vm.sessionActivity.collectAsStateWithLifecycle()

    val selectionState = rememberChatSelectionState()
    val hazeState = rememberHazeState()
    var showFilesSheet by remember { mutableStateOf(false) }
    var showStickersSheet by remember { mutableStateOf(false) }
    val attachmentPickerActions = rememberChatAttachmentPickerActions(
        inputState = inputState,
        setting = setting,
        onAttachmentAdded = { showFilesSheet = false },
    )
    val allowAudioVideoAttachments =
        currentChatModel?.findProvider(setting.providers) is ProviderSetting.Google

    val completionProviders = remember(assistant.workspaceId, conversation.workspaceCwd, workspaceRepository) {
        assistant.workspaceId?.let { workspaceId ->
            listOf(
                WorkspaceCompletionProvider(
                    workspaceId = workspaceId.toString(),
                    repository = workspaceRepository,
                    currentCwd = conversation.workspaceCwd,
                )
            )
        }.orEmpty()
    }

    val listScrolledUnderTopBar by remember(chatListState) {
        derivedStateOf { chatListState.canScrollBackward }
    }

    TTSAutoPlay(vm = vm, setting = setting, conversation = conversation)

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        AssistantBackground(setting = setting, modifier = Modifier.hazeSource(hazeState))
        Scaffold(
            topBar = {
                TopBar(
                    settings = setting,
                    assistant = assistant,
                    hazeState = hazeState,
                    showCapsuleBackground = listScrolledUnderTopBar,
                    loading = loadingJob != null,
                    processingStatus = processingStatus,
                    onOpenSettings = {
                        navController.navigate(Screen.ChatSettings(assistant.id.toString()))
                    },
                )
            },
            bottomBar = {
                val messageQueue by vm.messageQueue.collectAsStateWithLifecycle()
                val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
                val backgroundTasks by vm.backgroundTasks.collectAsStateWithLifecycle()
                var showBackgroundTasks by remember { mutableStateOf(false) }
                if (showBackgroundTasks) {
                    BackgroundTasksSheet(
                        tasks = backgroundTasks,
                        onStop = vm::stopBackgroundTask,
                        onDismiss = { showBackgroundTasks = false },
                    )
                }
                ChatInput(
                    onStartVoiceMode = onStartVoiceMode,
                    voiceState = voiceState,
                    onStopVoiceMode = vm.voiceSession::stop,
                    state = inputState,
                    messageQueue = messageQueue,
                    onRemoveQueuedMessage = vm::removeQueuedMessage,
                    onBeginEditQueuedMessage = vm::beginEditQueuedMessage,
                    onFinishEditQueuedMessage = vm::finishEditQueuedMessage,
                    onResumeMessageQueue = vm::resumeMessageQueue,
                    runningBackgroundTasks = backgroundTasks.count { it.status == BackgroundShellStatus.RUNNING },
                    onShowBackgroundTasks = { showBackgroundTasks = true },
                    loading = loadingJob != null,
                    settings = setting,
                    assistant = assistant,
                    chatModel = currentChatModel,
                    hazeState = hazeState,
                    completionProviders = completionProviders,
                    onCancelClick = {
                        vm.stopGeneration()
                    },
                    onSendClick = {
                        if (currentChatModel == null) {
                            toaster.show("请先选择模型", type = ToastType.Error)
                            return@ChatInput
                        }
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(
                                parts = inputState.getContents(),
                                messageId = inputState.editingMessage!!,
                            )
                        } else {
                            vm.handleMessageSend(
                                content = inputState.getContents(),
                                annotations = listOfNotNull(inputState.replyReference),
                            )
                            scope.launch {
                                delay(100.milliseconds)
                                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                            }
                        }
                        inputState.clearInput()
                    },
                    onLongSendClick = {
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(
                                parts = inputState.getContents(),
                                messageId = inputState.editingMessage!!,
                            )
                        } else {
                            vm.handleMessageSend(
                                content = inputState.getContents(),
                                annotations = listOfNotNull(inputState.replyReference),
                                answer = false,
                            )
                            scope.launch {
                                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                            }
                        }
                        inputState.clearInput()
                    },
                    onUpdateChatModel = vm::setChatModel,
                    onUpdateAssistant = vm::updateAssistant,
                    onStickersClick = {
                        showStickersSheet = true
                    },
                    onMoreClick = {
                        showFilesSheet = true
                    },
                )
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            ChatList(
                innerPadding = innerPadding,
                conversation = conversation,
                state = chatListState,
                selectionState = selectionState,
                loading = loadingJob != null,
                compressing = sessionActivity == SessionActivity.Compressing,
                processingStatus = processingStatus,
                settings = setting,
                hazeState = hazeState,
                errors = errors,
                focusMessageId = focusMessageId,
                focusPartId = focusPartId,
                onDismissError = onDismissError,
                onClearAllErrors = onClearAllErrors,
                onRegenerate = {
                    vm.regenerateAtMessage(it)
                },
                onEdit = {
                    inputState.editingMessage = it.id
                    inputState.setContents(it.parts)
                },
                onForkMessage = {
                    scope.launch {
                        val fork = vm.forkMessage(message = it)
                        navigateToChatPage(navController, chatId = fork.id)
                    }
                },
                onDelete = {
                    if (loadingJob != null) {
                        vm.showDeleteBlockedWhileGeneratingError()
                    } else {
                        vm.deleteMessage(it)
                    }
                },
                onReply = { message, partIndex, segmentIndex ->
                    val speaker = when (message.role) {
                        me.rerere.ai.core.MessageRole.USER -> setting.displaySetting.userNickname.ifBlank { "自己" }
                        else -> assistant.name.ifBlank { currentChatModel?.displayName ?: "助手" }
                    }
                    inputState.replyReference = UIMessageAnnotation.ReplyReference(
                        targetMessageId = message.id,
                        targetPartId = partIndex?.let(message::partIdAt),
                        targetPartIndex = partIndex,
                        targetSegmentIndex = segmentIndex,
                        scope = if (partIndex != null || segmentIndex != null) {
                            me.rerere.ai.ui.ReplyScope.SELECTION
                        } else {
                            me.rerere.ai.ui.ReplyScope.WHOLE
                        },
                        targetRole = message.role,
                        speakerSnapshot = speaker,
                        previewSnapshot = message.replyPreview(partIndex, segmentIndex),
                    )
                },
                onOpenReplyReference = { reference ->
                    conversation.locateReplyTarget(reference)?.let { location ->
                        val targetNode = conversation.messageNodes[location.nodeIndex]
                        if (targetNode.selectIndex != location.branchIndex) {
                            vm.selectMessageNode(location.nodeId, location.branchIndex)
                        }
                        scope.launch { chatListState.animateScrollToItem(location.nodeIndex) }
                    }
                },
                onUpdateMessage = { newNode ->
                    vm.selectMessageNode(newNode.id, newNode.selectIndex)
                },
                onClickSuggestion = { suggestion ->
                    inputState.editingMessage = null
                    inputState.setMessageText(suggestion)
                },
                onTranslate = { message, locale ->
                    vm.translateMessage(message, locale)
                },
                onClearTranslation = { message ->
                    vm.clearTranslationField(message.id)
                },
                onToolApproval = { toolCallId, approved, reason ->
                    vm.handleToolApproval(toolCallId, approved, reason)
                },
                onToolAnswer = { toolCallId, answer ->
                    vm.handleToolAnswer(toolCallId, answer)
                },
                onToggleFavorite = { node ->
                    vm.toggleMessageFavorite(node)
                },
                onConversationSystemPromptChange = { newPrompt ->
                    vm.updateConversationSystemPrompt(newPrompt)
                },
            )
        }

        if (showFilesSheet) {
            ChatFilesPickerSheet(
                inputState = inputState,
                setting = setting,
                conversation = conversation,
                assistant = assistant,
                chatModel = currentChatModel,
                vm = vm,
                attachmentPickerActions = attachmentPickerActions,
                onStartVoiceMode = onStartVoiceMode,
                onOpenStickers = {
                    showFilesSheet = false
                    showStickersSheet = true
                },
                onDismiss = { showFilesSheet = false },
            )
        }
        if (showStickersSheet) {
            StickerPickerSheet(
                onStickerSelected = { sticker ->
                    scope.launch {
                        val copy = filesManager.copyManagedFile(sticker, FileFolders.UPLOAD)
                        inputState.insertInlinePart(UIMessagePart.Image(
                            url = filesManager.getFile(copy).toUri().toString(),
                            metadata = stickerMetadata(sticker.relativePath),
                        ))
                        showStickersSheet = false
                    }
                },
                onDismiss = { showStickersSheet = false },
            )
        }
    }
}

@Composable
private fun ChatFilesPickerSheet(
    inputState: ChatInputState,
    setting: Settings,
    conversation: Conversation,
    assistant: Assistant,
    chatModel: Model?,
    vm: ChatVM,
    attachmentPickerActions: ChatAttachmentPickerActions,
    onStartVoiceMode: () -> Unit,
    onOpenStickers: () -> Unit,
    onDismiss: () -> Unit,
) {
    val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showCompressDialog by remember { mutableStateOf(false) }

    fun dismissAll() {
        showCompressDialog = false
        onDismiss()
    }

    val filesSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(
        sheetState = filesSheetState,
        onDismissRequest = { dismissAll() },
    ) {
        FilesPicker(
            conversation = conversation,
            state = inputState,
            assistant = assistant,
            chatModel = chatModel,
            mcpManager = vm.mcpManager,
            onCompressContext = vm::handleCompressContext,
            onUpdateAssistant = vm::updateAssistant,
            onUpdateWorkspaceCwd = vm::updateWorkspaceCwd,
            onUpdateSearchMode = { mode ->
                vm.updateSearch(
                    enableWebSearch = mode == SearchMode.LOCAL,
                    builtInSearch = mode == SearchMode.BUILT_IN,
                )
            },
            onUpdateSearchService = { index ->
                vm.updateSettings { it.copy(searchServiceSelected = index) }
            },
            showCompressDialog = showCompressDialog,
            onShowCompressDialogChange = { showCompressDialog = it },
            onDismiss = { dismissAll() },
            onTakePic = attachmentPickerActions.onTakePicture,
            onPickImage = attachmentPickerActions.onPickImage,
            onPickVideo = attachmentPickerActions.onPickVideo,
            onPickAudio = attachmentPickerActions.onPickAudio,
            onPickFile = attachmentPickerActions.onPickFile,
            onSketch = attachmentPickerActions.onSketch,
            onOpenStickers = onOpenStickers,
            onStartVoiceMode = if (
                setting.getSelectedASRProvider()?.supportsServerVadVoiceMode == true &&
                voiceState.phase == VoicePhase.Off
            ) {
                {
                    dismissAll()
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onStartVoiceMode()
                }
            } else null,
        )
    }
}

@Composable
private fun TopBar(
    settings: Settings,
    assistant: Assistant,
    hazeState: HazeState,
    showCapsuleBackground: Boolean,
    loading: Boolean,
    processingStatus: String?,
    onOpenSettings: () -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        title = {
            Column {
                Text(
                    text = assistant.name.ifBlank { "Aire" },
                    maxLines = 1,
                    style = MaterialTheme.typography.titleMedium,
                    overflow = TextOverflow.Ellipsis,
                )
                val status = processingStatus ?: if (loading) "正在输入…" else ""
                if (status.isNotEmpty()) {
                    Text(
                        text = status,
                        maxLines = 1,
                        color = LocalContentColor.current.copy(alpha = 0.62f),
                        style = MaterialTheme.typography.labelSmall,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        actions = {
            ChatTopBarCapsule(
                hazeState = hazeState,
                displaySetting = settings.displaySetting,
                showBackground = showCapsuleBackground,
                modifier = Modifier.padding(start = 4.dp, end = 8.dp),
            ) {
                IconButton(
                    onClick = onOpenSettings,
                ) {
                    Icon(HugeIcons.Menu03, "聊天设置")
                }
            }
        },
    )
}

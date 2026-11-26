package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Smile
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getQuickMessagesOfAssistant
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.service.InboxState
import me.rerere.rikkahub.service.QueuedMessage
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionContext
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionItem
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionList
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionProvider
import me.rerere.rikkahub.ui.components.ui.KeepScreenOn
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.hooks.ChatInputState
import org.koin.compose.koinInject
import me.rerere.rikkahub.ui.pages.chat.VoicePhase
import me.rerere.rikkahub.ui.pages.chat.VoiceSessionState
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.uuid.Uuid
import kotlin.math.roundToInt

@Composable
fun ChatInput(
    state: ChatInputState,
    loading: Boolean,
    settings: Settings,
    // 会话视角下的助手和模型：会话开始后以会话上固定的配置为准
    assistant: Assistant,
    chatModel: Model?,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    completionProviders: List<ChatCompletionProvider> = emptyList(),
    onUpdateChatModel: (Model) -> Unit,
    onUpdateAssistant: (Assistant) -> Unit,
    onMoreClick: () -> Unit,
    onStickersClick: () -> Unit,
    onCancelClick: () -> Unit,
    onSendClick: () -> Unit,
    onLongSendClick: () -> Unit,
    messageQueue: InboxState = InboxState(),
    onRemoveQueuedMessage: (Uuid) -> Unit = {},
    onBeginEditQueuedMessage: (Uuid) -> QueuedMessage? = { null },
    onFinishEditQueuedMessage: (Uuid, List<UIMessagePart>?) -> Unit = { _, _ -> },
    onResumeMessageQueue: () -> Unit = {},
    runningBackgroundTasks: Int = 0,
    onShowBackgroundTasks: () -> Unit = {},
    onStartVoiceMode: (() -> Unit)? = null,
    voiceState: VoiceSessionState = VoiceSessionState(),
    onStopVoiceMode: () -> Unit = {},
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val themeShape = MaterialTheme.shapes.largeIncreased
    val containerShape = RoundedCornerShape(
        topStart = themeShape.topStart,
        topEnd = themeShape.topEnd,
        bottomEnd = themeShape.bottomEnd,
        bottomStart = themeShape.bottomStart,
    )
    val modelListState = rememberModelListState(
        modelId = chatModel?.id,
        providers = settings.providers,
        type = ModelType.CHAT,
    )
    fun sendMessage() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (loading && state.isEmpty()) onCancelClick() else onSendClick()
    }

    fun sendMessageWithoutAnswer() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (loading && state.isEmpty()) onCancelClick() else onLongSendClick()
    }

    // 底部渐变遮罩：从输入区顶部一直过渡到屏幕底部（键盘弹出时到键盘上沿），避免消息透到系统导航栏
    val scrimColor = MaterialTheme.colorScheme.background
    val imeInsets = WindowInsets.ime
    Surface(
        color = Color.Transparent,
        modifier = Modifier.drawWithCache {
            val brush = Brush.verticalGradient(
                colors = listOf(scrimColor.copy(alpha = 0f), scrimColor),
                startY = 0f,
                endY = (size.height - imeInsets.getBottom(this)).coerceAtLeast(1f),
            )
            onDrawBehind { drawRect(brush) }
        },
    ) {
        Column(
            modifier = modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp)
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MessageQueuePanel(
                state = messageQueue,
                onRemove = onRemoveQueuedMessage,
                onBeginEdit = onBeginEditQueuedMessage,
                onFinishEdit = onFinishEditQueuedMessage,
                onResume = onResumeMessageQueue,
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(containerShape)
                    .chatInputHaze(
                        hazeState = hazeState,
                        displaySetting = settings.displaySetting,
                        containerColor = containerColor,
                        shape = containerShape,
                    ),
                shape = containerShape,
                tonalElevation = 0.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                color = if (settings.displaySetting.enableBlurEffect) Color.Transparent else containerColor,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (voiceState.phase != VoicePhase.Off) {
                        VoiceModeRow(
                            state = voiceState,
                            onStop = onStopVoiceMode,
                            onRetry = { onStartVoiceMode?.invoke() },
                        )
                        androidx.compose.material3.HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                    state.replyReference?.let { reference ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 12.dp, top = 7.dp, bottom = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.chat_page_reply_to, reference.speakerSnapshot),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    Text(
                                        text = reference.previewSnapshot,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = { state.replyReference = null }) {
                                    Icon(HugeIcons.Cancel01, contentDescription = stringResource(R.string.cancel))
                                }
                            }
                        }
                    }
                    if (state.messageContent.isNotEmpty()) {
                        MediaFileInputRow(state = state)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        ModelHoldPickerButton(
                            state = modelListState,
                            reasoningLevel = assistant.reasoningLevel,
                            reasoningEnabled = chatModel?.abilities?.contains(ModelAbility.REASONING) == true,
                            onUpdateReasoningLevel = {
                                onUpdateAssistant(assistant.copy(reasoningLevel = it))
                            },
                        )
                        TextInputRow(
                            state = state,
                            assistant = assistant,
                            completionProviders = completionProviders,
                            onSendMessage = { sendMessage() },
                            onStickersClick = onStickersClick,
                            onMoreClick = onMoreClick,
                            modifier = Modifier.weight(1f),
                        )
                        ActionIconButton(onClick = onStickersClick) {
                            Icon(HugeIcons.Smile, contentDescription = "表情包")
                        }
                        SendButton(
                            loading = loading,
                            empty = state.isEmpty(),
                            onClick = { sendMessage() },
                            onQueue = { sendMessageWithoutAnswer() },
                        )
                        if (loading) {
                            KeepScreenOn()
                        }
                    }
                    if (runningBackgroundTasks > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            BackgroundTasksButton(
                                running = runningBackgroundTasks,
                                onClick = onShowBackgroundTasks,
                            )
                        }
                    }
                }
            }

        }
    }

    ModelListSheet(
        state = modelListState,
        onSelect = onUpdateChatModel,
    )
}

private val reasoningHoldChoices = ReasoningLevel.entries
private val ReasoningHoldRowHeight = 42.dp
private val ReasoningHoldPopupWidth = 116.dp
private val HoldTargetGap = 10.dp

@Composable
private fun ModelHoldPickerButton(
    state: ModelListState,
    reasoningLevel: ReasoningLevel,
    reasoningEnabled: Boolean,
    onUpdateReasoningLevel: (ReasoningLevel) -> Unit,
) {
    val model = state.currentModel
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val buttonSize = 48.dp
    val rowHeightPx = with(density) { ReasoningHoldRowHeight.toPx() }
    val popupWidthPx = with(density) { ReasoningHoldPopupWidth.toPx() }
    val gapPx = with(density) { HoldTargetGap.toPx() }
    val popupHeightPx = rowHeightPx * reasoningHoldChoices.size
    var anchorPosition by remember { mutableStateOf(IntOffset.Zero) }
    var holding by remember { mutableStateOf(false) }
    var highlightedIndex by remember { mutableStateOf<Int?>(null) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(buttonSize)
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInWindow()
                anchorPosition = IntOffset(position.x.roundToInt(), position.y.roundToInt())
            }
            .clip(CircleShape)
            .pointerInput(reasoningEnabled) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    var earlyReleasePosition: androidx.compose.ui.geometry.Offset? = null
                    val releasedBeforeLongPress = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == pointerId }
                            if (change == null || !change.pressed) {
                                earlyReleasePosition = change?.position ?: down.position
                                return@withTimeoutOrNull true
                            }
                            change.consume()
                        }
                    } == true

                    if (releasedBeforeLongPress) {
                        val position = earlyReleasePosition ?: down.position
                        if (
                            position.x in 0f..size.width.toFloat() &&
                            position.y in 0f..size.height.toFloat()
                        ) {
                            state.open()
                        }
                        return@awaitEachGesture
                    }

                    if (!reasoningEnabled) {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == pointerId } ?: break
                            change.consume()
                            if (!change.pressed) break
                        }
                        return@awaitEachGesture
                    }

                    holding = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    var previousIndex: Int? = null
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == pointerId }
                        if (change == null) break
                        val index = resolveReasoningHoldIndex(
                            x = change.position.x,
                            y = change.position.y,
                            popupWidth = popupWidthPx,
                            rowHeight = rowHeightPx,
                            choiceCount = reasoningHoldChoices.size,
                            gap = gapPx,
                        )
                        highlightedIndex = index
                        if (index != null && index != previousIndex) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        }
                        previousIndex = index
                        change.consume()
                        if (!change.pressed) {
                            index?.let { onUpdateReasoningLevel(reasoningHoldChoices[it]) }
                            break
                        }
                    }
                    holding = false
                    highlightedIndex = null
                }
            },
    ) {
        if (model != null) {
            AutoAIIcon(
                modifier = Modifier.size(36.dp),
                name = model.modelId,
                color = Color.Transparent,
            )
        } else {
            Icon(
                imageVector = HugeIcons.Brain02,
                contentDescription = stringResource(R.string.setting_model_page_chat_model),
                modifier = Modifier.size(20.dp),
            )
        }
    }

    if (holding) {
        Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset(
                x = anchorPosition.x,
                y = anchorPosition.y - popupHeightPx.roundToInt() - gapPx.roundToInt(),
            ),
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            Surface(
                modifier = Modifier.widthIn(min = ReasoningHoldPopupWidth, max = ReasoningHoldPopupWidth),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column {
                    reasoningHoldChoices.forEachIndexed { index, level ->
                        Surface(
                            color = if (highlightedIndex == index) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = ReasoningHoldRowHeight)
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = reasoningHoldLabel(level),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (level == reasoningLevel) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun reasoningHoldLabel(level: ReasoningLevel): String = when (level) {
    ReasoningLevel.OFF -> stringResource(R.string.reasoning_off)
    ReasoningLevel.AUTO -> stringResource(R.string.reasoning_auto)
    ReasoningLevel.LOW -> stringResource(R.string.reasoning_light)
    ReasoningLevel.MEDIUM -> stringResource(R.string.reasoning_medium)
    ReasoningLevel.HIGH -> stringResource(R.string.reasoning_heavy)
    ReasoningLevel.XHIGH -> stringResource(R.string.reasoning_xhigh)
    ReasoningLevel.MAX -> stringResource(R.string.reasoning_max)
}

@Composable
private fun SendButton(
    loading: Boolean,
    empty: Boolean,
    onClick: () -> Unit,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val buttonSize = 38.dp
    val buttonSizePx = with(density) { buttonSize.toPx() }
    val gapPx = with(density) { HoldTargetGap.toPx() }
    val showStop = loading && empty
    val canSend = showStop || !empty
    var pressed by remember { mutableStateOf(false) }
    var selectedTarget by remember { mutableStateOf(SendHoldTarget.NONE) }
    var anchorPosition by remember { mutableStateOf(IntOffset.Zero) }
    val containerColor = when {
        showStop -> MaterialTheme.colorScheme.errorContainer
        empty -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.primary
    }
    val contentColor = when {
        showStop -> MaterialTheme.colorScheme.onErrorContainer
        empty -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onPrimary
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(buttonSize)
            .testTag("chat_send_button")
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInWindow()
                anchorPosition = IntOffset(position.x.roundToInt(), position.y.roundToInt())
            }
            .clip(CircleShape)
            .pointerInput(canSend, showStop) {
                if (!canSend) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId: PointerId = down.id
                    pressed = true
                    selectedTarget = SendHoldTarget.SEND
                    var previousTarget = selectedTarget
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == pointerId }
                        if (change == null) break
                        val target = resolveSendHoldTarget(
                            x = change.position.x,
                            y = change.position.y,
                            buttonSize = buttonSizePx,
                            gap = gapPx,
                            queueVisible = !showStop,
                        )
                        selectedTarget = target
                        if (target == SendHoldTarget.QUEUE && previousTarget != SendHoldTarget.QUEUE) {
                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        }
                        previousTarget = target
                        change.consume()
                        if (!change.pressed) {
                            when (target) {
                                SendHoldTarget.SEND -> onClick()
                                SendHoldTarget.QUEUE -> onQueue()
                                SendHoldTarget.NONE -> Unit
                            }
                            break
                        }
                    }
                    pressed = false
                    selectedTarget = SendHoldTarget.NONE
                }
            }
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = containerColor,
            content = {},
        )
        Icon(
            imageVector = when {
                showStop -> HugeIcons.Cancel01
                else -> HugeIcons.ArrowUp02
            },
            contentDescription = stringResource(if (showStop) R.string.stop else R.string.send),
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
    }

    if (pressed && !showStop) {
        Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset(
                x = anchorPosition.x,
                y = anchorPosition.y - buttonSizePx.roundToInt() - gapPx.roundToInt(),
            ),
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            Surface(
                modifier = Modifier.size(buttonSize),
                shape = CircleShape,
                color = if (selectedTarget == SendHoldTarget.QUEUE) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (selectedTarget == SendHoldTarget.QUEUE) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = HugeIcons.Clock01,
                        contentDescription = "暂存气泡，暂不请求模型",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

internal enum class SendHoldTarget { SEND, QUEUE, NONE }

internal fun resolveSendHoldTarget(
    x: Float,
    y: Float,
    buttonSize: Float,
    gap: Float,
    queueVisible: Boolean,
): SendHoldTarget {
    if (x in 0f..buttonSize && y in 0f..buttonSize) return SendHoldTarget.SEND
    val queueTop = -buttonSize - gap
    if (queueVisible && x in 0f..buttonSize && y in queueTop..(queueTop + buttonSize)) {
        return SendHoldTarget.QUEUE
    }
    return SendHoldTarget.NONE
}

internal fun resolveReasoningHoldIndex(
    x: Float,
    y: Float,
    popupWidth: Float,
    rowHeight: Float,
    choiceCount: Int,
    gap: Float,
): Int? {
    if (x !in 0f..popupWidth || choiceCount <= 0) return null
    val popupTop = -(rowHeight * choiceCount) - gap
    if (y !in popupTop..-gap) return null
    return ((y - popupTop) / rowHeight).toInt().coerceIn(0, choiceCount - 1)
}

@Composable
private fun BackgroundTasksButton(
    running: Int,
    onClick: () -> Unit,
) {
    BadgedBox(
        modifier = Modifier.padding(horizontal = 5.dp),
        badge = {
            Badge(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Text(running.toString()) }
        },
    ) {
        ActionIconButton(onClick = onClick) {
            Icon(
                imageVector = HugeIcons.ComputerTerminal01,
                contentDescription = stringResource(R.string.background_tasks_running, running),
            )
        }
    }
}

@Composable
private fun ActionIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(30.dp),
        shape = CircleShape,
        tonalElevation = 0.dp,
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
private fun TextInputRow(
    state: ChatInputState,
    assistant: Assistant,
    completionProviders: List<ChatCompletionProvider>,
    onSendMessage: () -> Unit,
    onStickersClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = LocalSettings.current
    val filesManager: FilesManager = koinInject()
    val quickMessages = remember(settings.quickMessages, assistant.quickMessageIds) {
        settings.getQuickMessagesOfAssistant(assistant)
    }

    var isEditingReasoning by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (state.isEditing()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.editing))
                    Spacer(Modifier.weight(1f))
                    if (state.editingReasoning != null) {
                        TextButton(onClick = { isEditingReasoning = true }) {
                            Text(stringResource(R.string.chat_input_edit_reasoning))
                        }
                    }
                    Icon(
                        imageVector = HugeIcons.Cancel01,
                        contentDescription = stringResource(R.string.cancel_edit),
                        modifier = Modifier.clickable { state.clearInput() }
                    )
                }
            }
        }

        var isFocused by remember { mutableStateOf(false) }
        var isFullScreen by remember { mutableStateOf(false) }
        var completionList by remember { mutableStateOf<ChatCompletionList?>(null) }
        val receiveContentListener = remember(
            settings.displaySetting.pasteLongTextAsFile, settings.displaySetting.pasteLongTextThreshold
        ) {
            ReceiveContentListener { transferableContent ->
                when {
                    transferableContent.hasMediaType(MediaType.Image) -> {
                        transferableContent.consume { item ->
                            val uri = item.uri
                            if (uri != null) {
                                state.addImages(
                                    filesManager.createChatFilesByContents(
                                        listOf(uri)
                                    )
                                )
                            }
                            uri != null
                        }
                    }

                    settings.displaySetting.pasteLongTextAsFile && transferableContent.hasMediaType(MediaType.Text) -> {
                        transferableContent.consume { item ->
                            val text = item.text?.toString()
                            if (text != null && text.length > settings.displaySetting.pasteLongTextThreshold) {
                                val document = filesManager.createChatTextFile(text)
                                state.addFiles(listOf(document))
                                true
                            } else {
                                false
                            }
                        }
                    }

                    else -> transferableContent
                }
            }
        }

        LaunchedEffect(completionProviders, isFocused) {
            if (!isFocused || completionProviders.isEmpty()) {
                completionList = null
                return@LaunchedEffect
            }

            snapshotFlow {
                ChatCompletionContext(
                    text = state.textContent.text.toString(),
                    selection = state.textContent.selection,
                )
            }.collectLatest { context ->
                val lists = completionProviders.mapNotNull { provider ->
                    try {
                        provider.complete(context)
                            ?.takeIf { it.items.isNotEmpty() }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
                val primary = lists.firstOrNull()
                completionList = primary?.let { list ->
                    val mergedItems = lists
                        .filter { it.replacementRange == list.replacementRange }
                        .flatMap { it.items }
                        .distinctBy { it.label to it.insertText }
                        .sortedWith(
                            compareByDescending<ChatCompletionItem> { it.sortScore }
                                .thenBy { it.label.length }
                                .thenBy { it.label.lowercase() }
                        )
                        .take(8)
                    list.copy(items = mergedItems)
                }
            }
        }

        completionList?.takeIf { it.items.isNotEmpty() }?.let { list ->
            CompletionPopup(
                completionList = list,
                onItemClick = { item ->
                    state.applyCompletion(list.replacementRange, item)
                    completionList = null
                },
            )
        }

        TextField(
            state = state.textContent,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("chat_input")
                .contentReceiver(receiveContentListener)
                .onFocusChanged {
                    isFocused = it.isFocused
                },
            shape = MaterialTheme.shapes.largeIncreased,
            placeholder = {
                Text(stringResource(R.string.chat_input_placeholder))
            },
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = if (settings.displaySetting.sendOnEnter) ImeAction.Send else ImeAction.Default
            ),
            onKeyboardAction = {
                if (settings.displaySetting.sendOnEnter && !state.isEmpty()) {
                    onSendMessage()
                }
            },
            colors = TextFieldDefaults.colors().copy(
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (state.textContent.text.count { it == '\n' } >= 2) {
                        IconButton(
                            onClick = {
                                isFullScreen = !isFullScreen
                            }) {
                            Icon(HugeIcons.ArrowUp01, "展开详细编辑")
                        }
                    }
                }
            },
            leadingIcon = if (quickMessages.isNotEmpty()) {
                {
                    QuickMessageButton(quickMessages = quickMessages, state = state)
                }
            } else null,
        )
        if (isFullScreen) {
            FullScreenEditor(
                textState = state.textContent,
                placeholder = stringResource(R.string.chat_input_placeholder),
                onStickersClick = {
                    isFullScreen = false
                    onStickersClick()
                },
                onMoreClick = {
                    isFullScreen = false
                    onMoreClick()
                },
            ) {
                isFullScreen = false
            }
        }
        val reasoningState = state.editingReasoning
        if (isEditingReasoning && reasoningState != null) {
            FullScreenEditor(textState = reasoningState, placeholder = stringResource(R.string.chat_input_reasoning_placeholder)) {
                isEditingReasoning = false
            }
        }
    }
}

@Composable
private fun CompletionPopup(
    completionList: ChatCompletionList,
    onItemClick: (ChatCompletionItem) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            items(
                items = completionList.items,
                key = { item -> "${item.label}:${item.insertText}" },
            ) { item ->
                Surface(
                    onClick = { onItemClick(item) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item.icon?.let { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            item.detail?.let { detail ->
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ChatInputState.applyCompletion(
    replacementRange: TextRange,
    item: ChatCompletionItem,
) {
    val textLength = textContent.text.length
    val start = replacementRange.min.coerceIn(0, textLength)
    val end = replacementRange.max.coerceIn(start, textLength)
    textContent.edit {
        replace(start, end, item.insertText)
        selection = TextRange(start + item.insertText.length)
    }
}

@Composable
private fun QuickMessageButton(
    quickMessages: List<QuickMessage>,
    state: ChatInputState,
) {
    var expanded by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    IconButton(
        onClick = {
            expanded = !expanded
        }) {
        Icon(HugeIcons.Zap, null)
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 200.dp, max = 360.dp)
        ) {
            quickMessages.forEach { quickMessage ->
                // 短按追加到输入框末尾，长按替换输入框内容
                Surface(
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                state.appendText(quickMessage.content)
                                expanded = false
                            },
                            onLongClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                state.setMessageText(quickMessage.content)
                                expanded = false
                            },
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = quickMessage.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = quickMessage.content,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenEditor(
    textState: TextFieldState,
    placeholder: String,
    onStickersClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    onDone: () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = {
            onDone()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
            verticalArrangement = Arrangement.Bottom
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxHeight(0.9f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onDone) {
                        Icon(HugeIcons.ArrowDown01, contentDescription = "关闭详细编辑")
                    }
                    TextField(
                        state = textState,
                        modifier = Modifier
                            .padding(bottom = 2.dp)
                            .weight(1f)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(32.dp),
                        placeholder = {
                            Text(placeholder)
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                        colors = TextFieldDefaults.colors().copy(
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                    )
                    if (onStickersClick != null || onMoreClick != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            onStickersClick?.let { openStickers ->
                                ActionIconButton(onClick = openStickers) {
                                    Icon(HugeIcons.Smile, contentDescription = "表情包")
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            onMoreClick?.let { openMore ->
                                ActionIconButton(onClick = openMore) {
                                    Icon(HugeIcons.Add01, contentDescription = stringResource(R.string.more_options))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

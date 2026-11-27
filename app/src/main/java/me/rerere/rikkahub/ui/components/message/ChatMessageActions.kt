package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.util.fastForEachIndexed
import kotlinx.coroutines.delay
import kotlinx.datetime.toJavaLocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.derivedMessageSegments
import me.rerere.ai.ui.messageBreakDisplayText
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.hugeicons.stroke.FavouriteCircle
import me.rerere.hugeicons.stroke.GitFork
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.hugeicons.stroke.Share04
import me.rerere.hugeicons.stroke.StopCircle
import me.rerere.hugeicons.stroke.TextSelection
import me.rerere.hugeicons.stroke.Translate
import me.rerere.hugeicons.stroke.VolumeHigh
import me.rerere.hugeicons.stroke.WebDesign01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.utils.copyMessageToClipboard
import me.rerere.rikkahub.utils.extractQuotedContentAsText
import me.rerere.rikkahub.utils.removeBracketedContent
import me.rerere.rikkahub.utils.toLocalString
import me.rerere.rikkahub.utils.toMessageTimeString
import me.rerere.rikkahub.utils.writeClipboardText
import me.rerere.ui.components.RikkaConfirmDialog
import java.util.Locale

@Composable
fun ColumnScope.ChatMessageActionButtons(
    message: UIMessage,
    node: MessageNode,
    onUpdate: (MessageNode) -> Unit,
    onRegenerate: () -> Unit,
    onOpenActionSheet: () -> Unit,
    onTranslate: ((UIMessage, Locale) -> Unit)? = null,
    onClearTranslation: (UIMessage) -> Unit = {},
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    var isPendingDelete by remember { mutableStateOf(false) }
    var showTranslateDialog by remember { mutableStateOf(false) }
    var showRegenerateConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(isPendingDelete) {
        if (isPendingDelete) {
            delay(3000) // 3秒后自动取消
            isPendingDelete = false
        }
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        ChatMessageActionButton(
            icon = HugeIcons.Copy01,
            contentDescription = stringResource(R.string.copy),
            onClick = {
                context.copyMessageToClipboard(
                    message = message,
                    messageBreaksEnabled = settings.displaySetting.enableAssistantMessageBreaks,
                    maxSegments = settings.displaySetting.messageBreakMaxSegments,
                )
            },
        )

        ChatMessageActionButton(
            icon = HugeIcons.Refresh03,
            contentDescription = stringResource(R.string.regenerate),
            onClick = {
                if (message.role == MessageRole.USER) {
                    showRegenerateConfirm = true
                } else {
                    onRegenerate()
                }
            },
        )

        if (message.role == MessageRole.ASSISTANT) {
            val tts = LocalTTSState.current
            val isSpeaking by tts.isSpeaking.collectAsState()
            val isAvailable by tts.isAvailable.collectAsState()
            // 朗读中换成带底色的方角按钮
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                IconToggleButton(
                    checked = isSpeaking,
                    onCheckedChange = { speak ->
                        if (speak) {
                            val text = message.messageBreakDisplayText(
                                enabled = settings.displaySetting.enableAssistantMessageBreaks,
                                maxSegments = settings.displaySetting.messageBreakMaxSegments,
                            )
                            var textToSpeak = text
                            if (settings.displaySetting.ttsOnlyReadQuoted) {
                                textToSpeak = textToSpeak.extractQuotedContentAsText() ?: textToSpeak
                            }
                            if (settings.displaySetting.ttsOnlyReadOutsideBrackets) {
                                textToSpeak = textToSpeak.removeBracketedContent() ?: textToSpeak
                            }
                            tts.speak(textToSpeak)
                        } else {
                            tts.stop()
                        }
                    },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    modifier = Modifier.size(IconButtonDefaults.extraSmallContainerSize()),
                    enabled = isAvailable,
                    colors = IconButtonDefaults.iconToggleButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Icon(
                        imageVector = if (isSpeaking) HugeIcons.StopCircle else HugeIcons.VolumeHigh,
                        contentDescription = stringResource(R.string.tts),
                        modifier = Modifier.size(ActionIconSize),
                    )
                }
            }

            // Translation button
            if (onTranslate != null) {
                ChatMessageActionButton(
                    icon = HugeIcons.Translate,
                    contentDescription = stringResource(R.string.translate),
                    onClick = { showTranslateDialog = true },
                )
            }
        }

        ChatMessageActionButton(
            icon = HugeIcons.MoreVertical,
            contentDescription = stringResource(R.string.more_options),
            onClick = onOpenActionSheet,
        )

        ChatMessageBranchSelector(
            node = node,
            onUpdate = onUpdate,
        )

        if (settings.displaySetting.showDateTimeInMessage) {
            Text(
                text = message.createdAt.toJavaLocalDateTime().toMessageTimeString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    // Translation dialog
    if (showTranslateDialog && onTranslate != null) {
        LanguageSelectionDialog(
            onLanguageSelected = { language ->
                showTranslateDialog = false
                onTranslate(message, language)
            },
            onClearTranslation = {
                showTranslateDialog = false
                onClearTranslation(message)
            },
            onDismissRequest = {
                showTranslateDialog = false
            },
        )
    }

    // Regenerate confirmation dialog
    RikkaConfirmDialog(
        show = showRegenerateConfirm,
        title = stringResource(R.string.regenerate),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showRegenerateConfirm = false
            onRegenerate()
        },
        onDismiss = { showRegenerateConfirm = false },
        text = { Text(stringResource(R.string.regenerate_confirm_message)) }
    )
}

private val ActionIconSize = 18.dp

// 消息下方的小号图标按钮，按下时圆形收成方角
@Composable
internal fun ChatMessageActionButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // 按钮排得密，不让 48dp 的最小触控尺寸把间距撑开
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        IconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = modifier.size(IconButtonDefaults.extraSmallContainerSize()),
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(ActionIconSize),
            )
        }
    }
}

private class MessageSheetAction(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

internal data class MessageBubbleTarget(
    val partIndex: Int? = null,
    val segmentIndex: Int? = null,
)

internal fun UIMessage.textForBubbleTarget(
    target: MessageBubbleTarget,
    breaksEnabled: Boolean,
    maxSegments: Int,
): String {
    target.segmentIndex?.let { segmentIndex ->
        return derivedMessageSegments(enabled = breaksEnabled, maxSegments = maxSegments)
            .firstOrNull { it.index == segmentIndex }
            ?.text
            .orEmpty()
    }
    target.partIndex?.let { partIndex ->
        return (parts.getOrNull(partIndex) as? UIMessagePart.Text)?.text.orEmpty()
    }
    return messageBreakDisplayText(enabled = breaksEnabled, maxSegments = maxSegments)
}

/** Compact context menu used by the QQ-style timeline. */
@Composable
fun ChatMessageContextMenu(
    message: UIMessage,
    target: MessageBubbleTarget,
    isFavorite: Boolean,
    onCopy: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
    onRegenerate: () -> Unit,
    onReply: () -> Unit,
    onTranslate: (() -> Unit)?,
    onEdit: () -> Unit,
    onFork: () -> Unit,
    onDelete: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    @Composable
    fun QuickAction(
        icon: ImageVector,
        label: String,
        modifier: Modifier = Modifier,
        onClick: () -> Unit,
    ) {
        Column(
            modifier = modifier
                .clickable {
                    onDismissRequest()
                    onClick()
                }
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }

    @Composable
    fun ListAction(icon: ImageVector, label: String, destructive: Boolean = false, onClick: () -> Unit) {
        val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onDismissRequest()
                    onClick()
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Text(text = label, color = color, style = MaterialTheme.typography.bodyLarge)
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.width(300.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                ) {
                    QuickAction(
                        HugeIcons.Copy01,
                        stringResource(R.string.copy),
                        Modifier.weight(1f),
                        onCopy,
                    )
                    QuickAction(
                        HugeIcons.FavouriteCircle,
                        if (isFavorite) "取消收藏" else "收藏",
                        Modifier.weight(1f),
                        onToggleFavorite ?: {},
                    )
                    QuickAction(
                        HugeIcons.Refresh03,
                        stringResource(R.string.regenerate),
                        Modifier.weight(1f),
                        onRegenerate,
                    )
                }
                ListAction(HugeIcons.MessageAdd01, "引用", onClick = onReply)
                if (onTranslate != null) {
                    ListAction(HugeIcons.Translate, stringResource(R.string.translate), onClick = onTranslate)
                }
                ListAction(HugeIcons.Edit01, stringResource(R.string.edit), onClick = onEdit)
                ListAction(HugeIcons.GitFork, stringResource(R.string.create_fork), onClick = onFork)
                ListAction(HugeIcons.Delete01, stringResource(R.string.delete), destructive = true, onClick = onDelete)
            }
        }
    }
}

@Composable
fun ChatMessageActionsSheet(
    message: UIMessage,
    model: Model?,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onFork: () -> Unit,
    onReply: (partIndex: Int?, segmentIndex: Int?) -> Unit,
    onSelectAndCopy: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onWebViewPreview: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        // WebView Preview (only show if message has text content)
        val hasTextContent = message.parts.filterIsInstance<UIMessagePart.Text>()
            .any { it.text.isNotBlank() }
        val derivedSegments = message.derivedMessageSegments(
            enabled = settings.displaySetting.enableAssistantMessageBreaks,
            maxSegments = settings.displaySetting.messageBreakMaxSegments,
        )
        val actions = buildList {
            add(
                MessageSheetAction(
                    HugeIcons.MessageAdd01,
                    stringResource(R.string.chat_page_reply_whole),
                    onClick = { onReply(null, null) },
                )
            )
            if (message.role == MessageRole.ASSISTANT && derivedSegments.size > 1) {
                derivedSegments.forEach { segment ->
                    add(
                        MessageSheetAction(
                            HugeIcons.MessageAdd01,
                            stringResource(R.string.chat_page_reply_part, segment.index + 1),
                            onClick = { onReply(null, segment.index) },
                        )
                    )
                    add(
                        MessageSheetAction(
                            HugeIcons.Copy01,
                            stringResource(R.string.chat_page_copy_part, segment.index + 1),
                            onClick = { context.writeClipboardText(segment.text) },
                        )
                    )
                }
            } else {
                val textPartIndices = message.parts.indices.filter { message.parts[it] is UIMessagePart.Text }
                if (textPartIndices.size > 1) {
                    textPartIndices.forEachIndexed { bubbleIndex, partIndex ->
                        add(
                            MessageSheetAction(
                                HugeIcons.MessageAdd01,
                                stringResource(R.string.chat_page_reply_part, bubbleIndex + 1),
                                onClick = { onReply(partIndex, null) },
                            )
                        )
                        add(
                            MessageSheetAction(
                                HugeIcons.Copy01,
                                stringResource(R.string.chat_page_copy_part, bubbleIndex + 1),
                                onClick = {
                                    context.writeClipboardText((message.parts[partIndex] as UIMessagePart.Text).text)
                                },
                            )
                        )
                    }
                }
            }
            add(MessageSheetAction(HugeIcons.TextSelection, stringResource(R.string.select_and_copy), onClick = onSelectAndCopy))
            if (hasTextContent) {
                add(MessageSheetAction(HugeIcons.WebDesign01, stringResource(R.string.render_with_webview), onClick = onWebViewPreview))
            }
            add(MessageSheetAction(HugeIcons.Edit01, stringResource(R.string.edit), onClick = onEdit))
            add(MessageSheetAction(HugeIcons.Share04, stringResource(R.string.share), onClick = onShare))
            add(MessageSheetAction(HugeIcons.GitFork, stringResource(R.string.create_fork), onClick = onFork))
            if (onToggleFavorite != null) {
                add(
                    MessageSheetAction(
                        icon = HugeIcons.FavouriteCircle,
                        label = stringResource(
                            if (isFavorite) R.string.chat_message_remove_favorite
                            else R.string.chat_message_add_favorite
                        ),
                        onClick = onToggleFavorite,
                    )
                )
            }
            add(MessageSheetAction(HugeIcons.Delete01, stringResource(R.string.delete), destructive = true, onClick = onDelete))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                actions.fastForEachIndexed { index, action ->
                    SegmentedListItem(
                        onClick = {
                            onDismissRequest()
                            action.onClick()
                        },
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = actions.size),
                        colors = if (action.destructive) {
                            ListItemDefaults.segmentedColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                leadingContentColor = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        } else {
                            ListItemDefaults.segmentedColors()
                        },
                        leadingContent = {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = null,
                            )
                        },
                    ) {
                        Text(action.label)
                    }
                }
            }

            // Message Info
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                    Text(message.createdAt.toJavaLocalDateTime().toLocalString())
                    if (model != null) {
                        Text(model.displayName)
                    }
                }
            }
        }
    }
}

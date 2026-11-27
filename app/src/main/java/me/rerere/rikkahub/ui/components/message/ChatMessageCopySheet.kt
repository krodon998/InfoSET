package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.rerere.ai.ui.UIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.utils.writeClipboardText

@Composable
fun ChatMessageCopySheet(
    message: UIMessage,
    target: MessageBubbleTarget = MessageBubbleTarget(),
    onEdit: (() -> Unit)? = null,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        sheetGesturesEnabled = false,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        onDismissRequest()
                    }
                ) {
                    Icon(HugeIcons.Cancel01, null)
                }

                Text(
                    text = "消息预览",
                    style = MaterialTheme.typography.headlineSmall,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            context.writeClipboardText(
                                message.textForBubbleTarget(
                                    target = target,
                                    breaksEnabled = settings.displaySetting.enableAssistantMessageBreaks,
                                    maxSegments = settings.displaySetting.messageBreakMaxSegments,
                                )
                            )
                        }
                    ) {
                        Icon(HugeIcons.Copy01, contentDescription = stringResource(R.string.copy))
                    }
                    if (onEdit != null) {
                        IconButton(
                            onClick = {
                                onDismissRequest()
                                onEdit()
                            }
                        ) {
                            Icon(HugeIcons.Edit01, contentDescription = stringResource(R.string.edit))
                        }
                    }
                }
            }

            // Content
            val rawText = message.textForBubbleTarget(
                target = target,
                breaksEnabled = settings.displaySetting.enableAssistantMessageBreaks,
                maxSegments = settings.displaySetting.messageBreakMaxSegments,
            )

            if (rawText.isBlank()) {
                // No text content available
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_text_content_to_copy),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(text = rawText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

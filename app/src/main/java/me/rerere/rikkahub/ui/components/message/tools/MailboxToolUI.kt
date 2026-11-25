package me.rerere.rikkahub.ui.components.message.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Calendar03
import me.rerere.rikkahub.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val MAILBOX_TOOL_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
private val MAILBOX_TOOL_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/**
 * Future mailbox letters contain their private body in the tool arguments. This renderer is the
 * privacy boundary for chat history: before delivery it never mounts the generic JSON preview,
 * so code-block copy/download actions cannot expose the sealed payload either.
 */
object MailboxWriteToolUI : ToolUIRenderer {
    override val toolName: String = "mailbox_write_letter"
    override val revealRawArgumentsOnUnparsableOutput: Boolean = false

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.Calendar03

    @Composable
    override fun title(context: ToolUIContext): String {
        val unlockAt = remember(context.arguments) { parseMailboxToolUnlockAt(context.arguments) }
        return stringResource(
            if (rememberMailboxToolLocked(context.arguments, unlockAt)) {
                R.string.mailbox_tool_locked_title
            } else {
                R.string.mailbox_tool_arrived
            }
        )
    }

    override fun hasSummary(context: ToolUIContext): Boolean = true

    @Composable
    override fun Summary(context: ToolUIContext) {
        val unlockAt = remember(context.arguments) { parseMailboxToolUnlockAt(context.arguments) }
        val arrival = remember(unlockAt) { unlockAt?.toMailboxDisplayTime() }
        Text(
            text = arrival?.let { stringResource(R.string.mailbox_tool_arrives_at, it) }
                ?: stringResource(R.string.mailbox_tool_locked_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
        )
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        val unlockAt = remember(context.arguments) { parseMailboxToolUnlockAt(context.arguments) }
        if (rememberMailboxToolLocked(context.arguments, unlockAt)) {
            SealedMailboxToolPreview(unlockAt = unlockAt)
        } else {
            DefaultToolPreview(context = context)
        }
    }
}

@Composable
private fun rememberMailboxToolLocked(arguments: JsonElement, unlockAt: Long?): Boolean {
    val now by produceState(initialValue = System.currentTimeMillis(), unlockAt) {
        val target = unlockAt ?: return@produceState
        val remaining = target - System.currentTimeMillis()
        if (remaining > 0) delay(remaining)
        value = System.currentTimeMillis()
    }
    return isMailboxToolLocked(arguments, now)
}

@Composable
private fun SealedMailboxToolPreview(unlockAt: Long?) {
    Column(
        modifier = Modifier
            .fillMaxHeight(0.58f)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.mailbox_tool_locked_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = HugeIcons.Calendar03,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.mailbox_tool_locked_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    unlockAt?.toMailboxDisplayTime()?.let { arrival ->
                        Text(
                            text = stringResource(R.string.mailbox_tool_arrives_at, arrival),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

internal fun isMailboxToolLocked(arguments: JsonElement, nowMillis: Long): Boolean =
    parseMailboxToolUnlockAt(arguments)?.let { it > nowMillis } ?: true

internal fun parseMailboxToolUnlockAt(arguments: JsonElement): Long? {
    val value = arguments.getStringContent("unlock_at")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    value.toLongOrNull()?.let { return it }

    return (
        runCatching { Instant.parse(value) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(value).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value).atZone(MAILBOX_TOOL_ZONE).toInstant() }.getOrNull()
            ?: runCatching { LocalDate.parse(value).atStartOfDay(MAILBOX_TOOL_ZONE).toInstant() }.getOrNull()
        )?.toEpochMilli()
}

private fun Long.toMailboxDisplayTime(): String =
    Instant.ofEpochMilli(this).atZone(MAILBOX_TOOL_ZONE).format(MAILBOX_TOOL_TIME_FORMAT)

package me.rerere.rikkahub.data.search

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.partIdAt
import me.rerere.rikkahub.data.model.Conversation

enum class ArchiveBrowseKind {
    MESSAGE,
    IMAGE,
    VIDEO,
    FILE,
    LINK,
}

data class ArchiveBrowseResult(
    val kind: ArchiveBrowseKind,
    val conversationId: String,
    val nodeId: String,
    val messageId: String,
    val partId: String?,
    val title: String,
    val createdAt: LocalDateTime,
    val preview: String,
)

private val URL_REGEX = Regex("""https?://[^\s<>()\[\]{}]+""", RegexOption.IGNORE_CASE)

fun Conversation.projectArchive(
    kind: ArchiveBrowseKind,
    date: LocalDate? = null,
): List<ArchiveBrowseResult> = buildList {
    messageNodes.forEach { node ->
        node.messages.forEach messageLoop@ { message ->
            if (date != null && message.createdAt.date != date) return@messageLoop
            when (kind) {
                ArchiveBrowseKind.MESSAGE -> add(
                    message.result(
                        conversation = this@projectArchive,
                        nodeId = node.id.toString(),
                        kind = kind,
                        partId = message.partIdAt(0)?.toString(),
                        preview = message.archivePreview(),
                    )
                )

                ArchiveBrowseKind.IMAGE -> message.parts.forEachIndexed { index, part ->
                    if (part is UIMessagePart.Image) {
                        add(
                            message.result(
                                this@projectArchive,
                                node.id.toString(),
                                kind,
                                message.partIdAt(index)?.toString(),
                                "图片 · ${part.url.substringAfterLast('/').takeIf { it.isNotBlank() } ?: "image"}",
                            )
                        )
                    }
                }

                ArchiveBrowseKind.VIDEO -> message.parts.forEachIndexed { index, part ->
                    if (part is UIMessagePart.Video) {
                        add(
                            message.result(
                                this@projectArchive,
                                node.id.toString(),
                                kind,
                                message.partIdAt(index)?.toString(),
                                "视频 · ${part.url.substringAfterLast('/').takeIf { it.isNotBlank() } ?: "video"}",
                            )
                        )
                    }
                }

                ArchiveBrowseKind.FILE -> message.parts.forEachIndexed { index, part ->
                    when (part) {
                        is UIMessagePart.Document -> add(
                            message.result(
                                this@projectArchive,
                                node.id.toString(),
                                kind,
                                message.partIdAt(index)?.toString(),
                                part.fileName,
                            )
                        )

                        is UIMessagePart.Audio -> add(
                            message.result(
                                this@projectArchive,
                                node.id.toString(),
                                kind,
                                message.partIdAt(index)?.toString(),
                                "音频 · ${part.url.substringAfterLast('/').takeIf { it.isNotBlank() } ?: "audio"}",
                            )
                        )

                        else -> Unit
                    }
                }

                ArchiveBrowseKind.LINK -> message.parts.forEachIndexed { index, part ->
                    if (part is UIMessagePart.Text) {
                        URL_REGEX.findAll(part.text).forEach { match ->
                            add(
                                message.result(
                                    this@projectArchive,
                                    node.id.toString(),
                                    kind,
                                    message.partIdAt(index)?.toString(),
                                    match.value.trimEnd('.', ',', ';', '，', '。', '；'),
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}.sortedByDescending(ArchiveBrowseResult::createdAt)

private fun UIMessage.result(
    conversation: Conversation,
    nodeId: String,
    kind: ArchiveBrowseKind,
    partId: String?,
    preview: String,
) = ArchiveBrowseResult(
    kind = kind,
    conversationId = conversation.id.toString(),
    nodeId = nodeId,
    messageId = id.toString(),
    partId = partId,
    title = conversation.title,
    createdAt = createdAt,
    preview = preview,
)

private fun UIMessage.archivePreview(): String {
    val text = parts.filterIsInstance<UIMessagePart.Text>()
        .joinToString("\n") { it.text }
        .trim()
    if (text.isNotBlank()) return text.take(240)

    return when (val part = parts.firstOrNull()) {
        is UIMessagePart.Image -> "图片"
        is UIMessagePart.Video -> "视频"
        is UIMessagePart.Audio -> "音频"
        is UIMessagePart.Document -> part.fileName
        is UIMessagePart.Tool -> "工具 · ${part.toolName}"
        is UIMessagePart.Notice -> part.title
        else -> "(无文字内容)"
    }
}

package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.asReplyReferenceExcerpt
import me.rerere.ai.ui.partIndexOf

/** Adds deterministic reply context to the provider projection without changing stored messages. */
object ReplyReferenceTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> = projectReplyReferences(messages)
}

internal fun projectReplyReferences(messages: List<UIMessage>): List<UIMessage> {
    val targets = messages.associateBy(UIMessage::id)
    return messages.map { message ->
        val reference = message.annotations.filterIsInstance<UIMessageAnnotation.ReplyReference>().firstOrNull()
            ?: return@map message
        val target = targets[reference.targetMessageId]
        val targetPartIndex = target?.let { messageTarget ->
            reference.targetPartId?.let(messageTarget::partIndexOf) ?: reference.targetPartIndex
        } ?: reference.targetPartIndex
        val preview = target
            ?.replyPreview(targetPartIndex, reference.targetSegmentIndex)
            ?.takeIf { it.isNotBlank() }
            ?: reference.previewSnapshot
        val stablePreview = preview.asReplyReferenceExcerpt()
        val scope = reference.effectiveScope.name.lowercase()
        val attributes = buildList {
            add("msg_id=${reference.targetMessageId}")
            add("scope=$scope")
            reference.targetSegmentIndex?.let { add("segment_id=${reference.targetMessageId}:$it") }
            reference.targetPartId?.let { add("part_id=$it") }
        }.joinToString(", ")
        val quoted = buildString {
            appendLine("[quoted: $attributes]")
            appendLine(stablePreview.replace("[/quoted]", "\\[/quoted]"))
            append("[/quoted]")
        }
        message.copy(parts = listOf(UIMessagePart.Text(quoted)) + message.parts)
    }
}

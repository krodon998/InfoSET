package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
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
        }
        val preview = target
            ?.replyPreview(targetPartIndex)
            ?.takeIf { it.isNotBlank() }
            ?: reference.previewSnapshot
        val stablePreview = preview.replace(Regex("\\s+"), " ").trim().take(120)
        val speaker = reference.speakerSnapshot.replace(Regex("\\s+"), " ").trim().ifBlank {
            reference.targetRole.name.lowercase()
        }
        val prefix = "[Replying to $speaker: \"${stablePreview.replace("\\", "\\\\").replace("\"", "\\\"")}\"]"
        message.copy(parts = listOf(UIMessagePart.Text(prefix)) + message.parts)
    }
}

package me.rerere.ai.ui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.ai.core.MessageRole
import kotlin.uuid.Uuid

@Serializable
sealed class UIMessageAnnotation {
    @Serializable
    @SerialName("url_citation")
    data class UrlCitation(
        val title: String,
        val url: String
    ) : UIMessageAnnotation()

    /**
     * Stable reply target kept separately from the user's text.
     *
     * [previewSnapshot] is deliberately persisted so the reply remains understandable when the
     * target is outside the current branch, omitted from an import, or deleted later.
     */
    @Serializable
    @SerialName("reply_reference")
    data class ReplyReference(
        val targetMessageId: Uuid,
        val targetPartId: Uuid? = null,
        // Compatibility locator for archives created before stable part IDs.
        val targetPartIndex: Int? = null,
        val targetRole: MessageRole,
        val speakerSnapshot: String,
        val previewSnapshot: String,
    ) : UIMessageAnnotation()
}

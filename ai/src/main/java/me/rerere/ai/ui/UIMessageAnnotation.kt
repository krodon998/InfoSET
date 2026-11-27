@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package me.rerere.ai.ui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
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
        @SerialName("message_id")
        @JsonNames("targetMessageId")
        val targetMessageId: Uuid,
        val targetPartId: Uuid? = null,
        // Compatibility locator for archives created before stable part IDs.
        val targetPartIndex: Int? = null,
        val targetSegmentIndex: Int? = null,
        val scope: ReplyScope? = null,
        val targetRole: MessageRole,
        val speakerSnapshot: String,
        @SerialName("excerpt")
        @JsonNames("previewSnapshot")
        val previewSnapshot: String,
    ) : UIMessageAnnotation() {
        val effectiveScope: ReplyScope
            get() = scope ?: if (targetPartIndex != null || targetPartId != null || targetSegmentIndex != null) {
                ReplyScope.SELECTION
            } else {
                ReplyScope.WHOLE
            }
    }
}

@Serializable
enum class ReplyScope {
    @SerialName("whole")
    WHOLE,

    @SerialName("selection")
    SELECTION,
}

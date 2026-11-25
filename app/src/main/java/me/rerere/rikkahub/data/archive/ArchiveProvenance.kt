package me.rerere.rikkahub.data.archive

import kotlinx.serialization.Serializable

/**
 * Stable origin metadata for one message in the future unified timeline.
 *
 * The archive layer keeps this separate from the mutable conversation UI so imported messages can
 * always be traced back to their source window, branch, model, and frontend.
 */
@Serializable
data class ArchiveProvenance(
    val source: String,
    val conversation: String,
    val messageTimeEpochMillis: Long?,
    val branch: String?,
    val model: String?,
    val frontend: String,
    val sourceMessageId: String?,
    val sourceOrder: Long,
)

/** A stable target used when the unified timeline opens the original conversation. */
@Serializable
data class ArchiveMessageTarget(
    val provenance: ArchiveProvenance,
    val localConversationId: String?,
    val localMessageId: String?,
)

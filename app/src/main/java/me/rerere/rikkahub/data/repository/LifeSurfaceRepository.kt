package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.dao.LifeSurfaceDAO
import me.rerere.rikkahub.data.db.entity.LifeSurfaceInteractionEntity
import me.rerere.rikkahub.data.db.entity.LifeSurfaceItemEntity
import me.rerere.rikkahub.data.db.entity.LifeSurfaceObjectEntity
import kotlin.time.Clock
import kotlin.uuid.Uuid

const val LIFE_SURFACE_MOMENT = "MOMENT"
const val LIFE_SURFACE_BOARD = "BOARD"

const val LIFE_INTERACTION_LIKE = "LIKE"
const val LIFE_INTERACTION_FAVORITE = "FAVORITE"
const val LIFE_INTERACTION_COMMENT = "COMMENT"
const val LIFE_INTERACTION_REPLY = "REPLY"

const val LIFE_SOURCE_NATIVE = "NATIVE"
const val LIFE_SOURCE_IMPORTED_FRAGMENT = "IMPORTED_FRAGMENT"

data class LifeSurfaceObjectDraft(
    val kind: String,
    val ref: String,
    val label: String? = null,
    val payloadJson: String? = null,
)

class LifeSurfaceRepository(
    private val dao: LifeSurfaceDAO,
) {
    suspend fun createMoment(
        authorId: String,
        authorLabel: String,
        body: String? = null,
        objects: List<LifeSurfaceObjectDraft> = emptyList(),
        publishAt: Long = Clock.System.now().toEpochMilliseconds(),
        sourceKind: String = LIFE_SOURCE_NATIVE,
        sourceRef: String? = null,
        id: String = Uuid.random().toString(),
    ): LifeSurfaceItemEntity = createItem(
        id = id,
        surface = LIFE_SURFACE_MOMENT,
        authorId = authorId,
        authorLabel = authorLabel,
        body = body,
        objects = objects,
        publishAt = publishAt,
        sourceKind = sourceKind,
        sourceRef = sourceRef,
    )

    suspend fun leaveBoardMessage(
        authorId: String,
        authorLabel: String,
        targetActorId: String? = null,
        targetActorLabel: String? = null,
        body: String? = null,
        objects: List<LifeSurfaceObjectDraft> = emptyList(),
        publishAt: Long = Clock.System.now().toEpochMilliseconds(),
        expiresAt: Long? = null,
        sourceKind: String = LIFE_SOURCE_NATIVE,
        sourceRef: String? = null,
        id: String = Uuid.random().toString(),
    ): LifeSurfaceItemEntity {
        require(expiresAt == null || expiresAt > publishAt) {
            "expiresAt must be later than publishAt"
        }
        return createItem(
            id = id,
            surface = LIFE_SURFACE_BOARD,
            authorId = authorId,
            authorLabel = authorLabel,
            targetActorId = targetActorId,
            targetActorLabel = targetActorLabel,
            body = body,
            objects = objects,
            publishAt = publishAt,
            expiresAt = expiresAt,
            sourceKind = sourceKind,
            sourceRef = sourceRef,
        )
    }

    /**
     * 导入旧生活片段时保留精确来源、原文与原始时间。
     * 后续互动只追加 interaction，不会覆盖这些字段。
     */
    suspend fun importMomentFragment(
        authorId: String,
        authorLabel: String,
        originalText: String,
        originalCreatedAt: Long,
        sourceKind: String,
        sourceRef: String,
        objects: List<LifeSurfaceObjectDraft> = emptyList(),
        id: String = Uuid.random().toString(),
    ): LifeSurfaceItemEntity {
        require(originalText.isNotBlank()) { "originalText must not be blank" }
        require(sourceKind.isNotBlank()) { "sourceKind must not be blank" }
        require(sourceRef.isNotBlank()) { "sourceRef must not be blank" }
        return createItem(
            id = id,
            surface = LIFE_SURFACE_MOMENT,
            authorId = authorId,
            authorLabel = authorLabel,
            body = originalText,
            objects = objects,
            publishAt = originalCreatedAt,
            sourceKind = sourceKind,
            sourceRef = sourceRef,
            originalText = originalText,
            originalCreatedAt = originalCreatedAt,
        )
    }

    fun observeMoments(
        now: Long = Clock.System.now().toEpochMilliseconds(),
        limit: Int = 100,
    ): Flow<List<LifeSurfaceItemEntity>> = dao.observeMoments(now, limit)

    fun observeBoard(
        targetActorId: String? = null,
        now: Long = Clock.System.now().toEpochMilliseconds(),
        limit: Int = 100,
    ): Flow<List<LifeSurfaceItemEntity>> = dao.observeBoard(targetActorId, now, limit)

    suspend fun getItem(itemId: String): LifeSurfaceItemEntity? = dao.getItem(itemId)

    fun observeObjects(itemId: String): Flow<List<LifeSurfaceObjectEntity>> =
        dao.observeObjects(itemId)

    fun observeInteractions(itemId: String): Flow<List<LifeSurfaceInteractionEntity>> =
        dao.observeInteractions(itemId)

    suspend fun hasLiked(itemId: String, actorId: String): Boolean =
        dao.hasReaction(itemId, actorId, LIFE_INTERACTION_LIKE)

    suspend fun hasFavorited(itemId: String, actorId: String): Boolean =
        dao.hasReaction(itemId, actorId, LIFE_INTERACTION_FAVORITE)

    suspend fun addComment(
        itemId: String,
        actorId: String,
        actorLabel: String,
        body: String,
        source: String = LIFE_SOURCE_NATIVE,
        sourceRef: String? = null,
        createdAt: Long = Clock.System.now().toEpochMilliseconds(),
        id: String = Uuid.random().toString(),
    ): LifeSurfaceInteractionEntity {
        requireAvailableItem(itemId, createdAt)
        return createInteraction(
            id = id,
            itemId = itemId,
            actorId = actorId,
            actorLabel = actorLabel,
            type = LIFE_INTERACTION_COMMENT,
            body = body,
            source = source,
            sourceRef = sourceRef,
            createdAt = createdAt,
        )
    }

    suspend fun reply(
        itemId: String,
        parentInteractionId: String,
        actorId: String,
        actorLabel: String,
        body: String,
        source: String = LIFE_SOURCE_NATIVE,
        sourceRef: String? = null,
        createdAt: Long = Clock.System.now().toEpochMilliseconds(),
        id: String = Uuid.random().toString(),
    ): LifeSurfaceInteractionEntity {
        requireAvailableItem(itemId, createdAt)
        val parent = requireNotNull(dao.getInteraction(parentInteractionId)) {
            "parent interaction not found"
        }
        require(parent.itemId == itemId) { "reply parent belongs to a different item" }
        require(parent.type == LIFE_INTERACTION_COMMENT || parent.type == LIFE_INTERACTION_REPLY) {
            "reply parent must be a comment or reply"
        }
        return createInteraction(
            id = id,
            itemId = itemId,
            parentInteractionId = parentInteractionId,
            actorId = actorId,
            actorLabel = actorLabel,
            type = LIFE_INTERACTION_REPLY,
            body = body,
            source = source,
            sourceRef = sourceRef,
            createdAt = createdAt,
        )
    }

    suspend fun setLike(
        itemId: String,
        actorId: String,
        actorLabel: String,
        enabled: Boolean,
        source: String = LIFE_SOURCE_NATIVE,
        sourceRef: String? = null,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ) = setReaction(
        itemId = itemId,
        actorId = actorId,
        actorLabel = actorLabel,
        type = LIFE_INTERACTION_LIKE,
        enabled = enabled,
        source = source,
        sourceRef = sourceRef,
        now = now,
    )

    suspend fun setFavorite(
        itemId: String,
        actorId: String,
        actorLabel: String,
        enabled: Boolean,
        source: String = LIFE_SOURCE_NATIVE,
        sourceRef: String? = null,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ) = setReaction(
        itemId = itemId,
        actorId = actorId,
        actorLabel = actorLabel,
        type = LIFE_INTERACTION_FAVORITE,
        enabled = enabled,
        source = source,
        sourceRef = sourceRef,
        now = now,
    )

    suspend fun setBoardPinned(
        itemId: String,
        pinned: Boolean,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ) {
        requireBoardItemAvailable(itemId, now)
        check(dao.setPinnedAt(itemId, now.takeIf { pinned }, now) == 1) {
            "board item not found"
        }
    }

    suspend fun markBoardRead(
        itemId: String,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ) {
        requireBoardItemAvailable(itemId, now)
        check(dao.markRead(itemId, now, now) == 1) { "board item not found" }
    }

    suspend fun markBoardResponded(
        itemId: String,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ) {
        requireBoardItemAvailable(itemId, now)
        check(dao.markResponded(itemId, now, now) == 1) { "board item not found" }
    }

    private suspend fun createItem(
        id: String,
        surface: String,
        authorId: String,
        authorLabel: String,
        targetActorId: String? = null,
        targetActorLabel: String? = null,
        body: String? = null,
        objects: List<LifeSurfaceObjectDraft>,
        publishAt: Long,
        expiresAt: Long? = null,
        sourceKind: String,
        sourceRef: String?,
        originalText: String? = null,
        originalCreatedAt: Long? = null,
    ): LifeSurfaceItemEntity {
        require(authorId.isNotBlank()) { "authorId must not be blank" }
        require(authorLabel.isNotBlank()) { "authorLabel must not be blank" }
        require(sourceKind.isNotBlank()) { "sourceKind must not be blank" }
        val normalizedBody = body?.trim()?.takeIf { it.isNotEmpty() }
        require(normalizedBody != null || objects.isNotEmpty()) {
            "life surface item must contain text or an object"
        }
        require(objects.all { it.kind.isNotBlank() && it.ref.isNotBlank() }) {
            "life surface objects require kind and ref"
        }
        val now = Clock.System.now().toEpochMilliseconds()
        val item = LifeSurfaceItemEntity(
            id = id,
            surface = surface,
            authorId = authorId,
            authorLabel = authorLabel,
            targetActorId = targetActorId,
            targetActorLabel = targetActorLabel,
            body = normalizedBody,
            publishAt = publishAt,
            expiresAt = expiresAt,
            sourceKind = sourceKind,
            sourceRef = sourceRef,
            originalText = originalText,
            originalCreatedAt = originalCreatedAt,
            createdAt = now,
            updatedAt = now,
        )
        val objectRows = objects.mapIndexed { index, objectRef ->
            LifeSurfaceObjectEntity(
                id = Uuid.random().toString(),
                itemId = id,
                kind = objectRef.kind.trim(),
                ref = objectRef.ref.trim(),
                label = objectRef.label?.trim()?.takeIf { it.isNotEmpty() },
                payloadJson = objectRef.payloadJson,
                position = index,
                createdAt = now,
            )
        }
        dao.insertItemWithObjects(item, objectRows)
        return item
    }

    private suspend fun createInteraction(
        id: String,
        itemId: String,
        parentInteractionId: String? = null,
        actorId: String,
        actorLabel: String,
        type: String,
        body: String?,
        source: String,
        sourceRef: String?,
        createdAt: Long,
    ): LifeSurfaceInteractionEntity {
        require(actorId.isNotBlank()) { "actorId must not be blank" }
        require(actorLabel.isNotBlank()) { "actorLabel must not be blank" }
        require(source.isNotBlank()) { "source must not be blank" }
        val normalizedBody = body?.trim()?.takeIf { it.isNotEmpty() }
        if (type == LIFE_INTERACTION_COMMENT || type == LIFE_INTERACTION_REPLY) {
            require(normalizedBody != null) { "$type body must not be blank" }
        }
        val interaction = LifeSurfaceInteractionEntity(
            id = id,
            itemId = itemId,
            parentInteractionId = parentInteractionId,
            actorId = actorId,
            actorLabel = actorLabel,
            type = type,
            body = normalizedBody,
            source = source,
            sourceRef = sourceRef,
            createdAt = createdAt,
            updatedAt = createdAt,
        )
        dao.insertInteraction(interaction)
        return interaction
    }

    private suspend fun setReaction(
        itemId: String,
        actorId: String,
        actorLabel: String,
        type: String,
        enabled: Boolean,
        source: String,
        sourceRef: String?,
        now: Long,
    ) {
        requireAvailableItem(itemId, now)
        val row = if (enabled) {
            LifeSurfaceInteractionEntity(
                id = Uuid.random().toString(),
                itemId = itemId,
                actorId = actorId,
                actorLabel = actorLabel,
                type = type,
                source = source,
                sourceRef = sourceRef,
                createdAt = now,
                updatedAt = now,
            )
        } else {
            null
        }
        dao.replaceReaction(row, itemId, actorId, type)
    }

    private suspend fun requireAvailableItem(itemId: String, now: Long): LifeSurfaceItemEntity {
        val item = requireNotNull(dao.getItem(itemId)) { "life surface item not found" }
        require(item.publishAt <= now) { "life surface item is not published yet" }
        require(item.expiresAt == null || item.expiresAt > now) { "life surface item has expired" }
        return item
    }

    private suspend fun requireBoardItemAvailable(itemId: String, now: Long): LifeSurfaceItemEntity =
        requireAvailableItem(itemId, now).also {
            require(it.surface == LIFE_SURFACE_BOARD) { "life surface item is not a board message" }
        }
}

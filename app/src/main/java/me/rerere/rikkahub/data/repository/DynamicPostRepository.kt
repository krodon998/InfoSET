package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import me.rerere.rikkahub.data.db.dao.DynamicPostDAO
import me.rerere.rikkahub.data.db.entity.DynamicPostEntity
import me.rerere.rikkahub.data.db.entity.DynamicPostInteractionEntity
import me.rerere.rikkahub.data.db.entity.DynamicPostInteractionTypes
import me.rerere.rikkahub.data.db.entity.DynamicPostObjectEntity
import me.rerere.rikkahub.data.db.entity.DynamicPostStatuses
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.uuid.Uuid

const val DYNAMIC_POST_SOURCE_NATIVE = "NATIVE"

data class DynamicPostObjectDraft(
    val kind: String,
    val ref: String,
    val label: String? = null,
    val payloadJson: String? = null,
)

class DynamicPostRepository(
    private val dao: DynamicPostDAO,
) {
    fun observeTimeline(limit: Int = 100): Flow<List<DynamicPostEntity>> =
        dao.observeTimeline(limit)

    fun observeDrafts(authorId: String, limit: Int = 100): Flow<List<DynamicPostEntity>> =
        dao.observeDrafts(authorId, limit)

    fun observeScheduled(authorId: String, limit: Int = 100): Flow<List<DynamicPostEntity>> =
        dao.observeScheduled(authorId, limit)

    fun observeArchived(authorId: String, limit: Int = 100): Flow<List<DynamicPostEntity>> =
        dao.observeArchived(authorId, limit)

    fun observeObjects(postId: String): Flow<List<DynamicPostObjectEntity>> =
        dao.observeObjects(postId)

    fun observeInteractions(postId: String): Flow<List<DynamicPostInteractionEntity>> =
        dao.observeInteractions(postId)

    suspend fun getPost(postId: String): DynamicPostEntity? = dao.getPost(postId)

    suspend fun createDraft(
        authorId: String,
        authorLabel: String,
        body: String? = null,
        objects: List<DynamicPostObjectDraft> = emptyList(),
        sourceKind: String = DYNAMIC_POST_SOURCE_NATIVE,
        sourceRef: String? = null,
        now: Long = Clock.System.now().toEpochMilliseconds(),
        id: String = Uuid.random().toString(),
    ): DynamicPostEntity = createPost(
        id = id,
        authorId = authorId,
        authorLabel = authorLabel,
        body = body,
        objects = objects,
        status = DynamicPostStatuses.DRAFT,
        sourceKind = sourceKind,
        sourceRef = sourceRef,
        now = now,
    )

    suspend fun schedule(
        authorId: String,
        authorLabel: String,
        scheduledAt: Long,
        body: String? = null,
        objects: List<DynamicPostObjectDraft> = emptyList(),
        sourceKind: String = DYNAMIC_POST_SOURCE_NATIVE,
        sourceRef: String? = null,
        originalTimeZoneId: String = ZoneId.systemDefault().id,
        now: Long = Clock.System.now().toEpochMilliseconds(),
        id: String = Uuid.random().toString(),
    ): DynamicPostEntity {
        require(originalTimeZoneId.isNotBlank()) { "originalTimeZoneId must not be blank" }
        require(scheduledAt > now) { "scheduledAt must be in the future" }
        return createPost(
            id = id,
            authorId = authorId,
            authorLabel = authorLabel,
            body = body,
            objects = objects,
            status = DynamicPostStatuses.SCHEDULED,
            scheduledAt = scheduledAt,
            originalTimeZoneId = originalTimeZoneId,
            sourceKind = sourceKind,
            sourceRef = sourceRef,
            now = now,
        )
    }

    suspend fun publishNow(
        authorId: String,
        authorLabel: String,
        body: String? = null,
        objects: List<DynamicPostObjectDraft> = emptyList(),
        sourceKind: String = DYNAMIC_POST_SOURCE_NATIVE,
        sourceRef: String? = null,
        now: Long = Clock.System.now().toEpochMilliseconds(),
        id: String = Uuid.random().toString(),
    ): DynamicPostEntity = createPost(
        id = id,
        authorId = authorId,
        authorLabel = authorLabel,
        body = body,
        objects = objects,
        status = DynamicPostStatuses.PUBLISHED,
        publishedAt = now,
        sourceKind = sourceKind,
        sourceRef = sourceRef,
        now = now,
    )

    suspend fun updatePending(
        postId: String,
        actorId: String,
        body: String?,
        objects: List<DynamicPostObjectDraft>? = null,
        scheduledAt: Long?,
        timeZoneId: String? = null,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ): DynamicPostEntity {
        val current = requireOwnedPost(postId, actorId)
        require(current.status == DynamicPostStatuses.DRAFT || current.status == DynamicPostStatuses.SCHEDULED) {
            "Only draft or scheduled posts can change pending content"
        }
        if (scheduledAt != null) {
            require(scheduledAt > now) { "scheduledAt must be in the future" }
        }
        val normalizedBody = body.normalized()
        objects?.let(::validateObjects)
        val effectiveObjects = objects ?: dao.observeObjects(postId).first().map {
            DynamicPostObjectDraft(
                kind = it.kind,
                ref = it.ref,
                label = it.label,
                payloadJson = it.payloadJson,
            )
        }
        require(normalizedBody != null || effectiveObjects.isNotEmpty()) {
            "post must contain text or an object"
        }
        val status = if (scheduledAt == null) DynamicPostStatuses.DRAFT else DynamicPostStatuses.SCHEDULED
        val originalTimeZoneId = when {
            current.originalTimeZoneId != null -> current.originalTimeZoneId
            scheduledAt != null -> timeZoneId?.takeIf(String::isNotBlank) ?: ZoneId.systemDefault().id
            else -> null
        }
        check(
            dao.updatePendingWithObjects(
                id = postId,
                authorId = actorId,
                body = normalizedBody,
                status = status,
                scheduledAt = scheduledAt,
                originalTimeZoneId = originalTimeZoneId,
                updatedAt = now,
                objects = objects?.toRows(postId, now),
            ) == 1
        ) { "pending post could not be updated" }
        return requireNotNull(dao.getPost(postId))
    }

    suspend fun publishPending(
        postId: String,
        actorId: String,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ): DynamicPostEntity {
        check(dao.publish(postId, actorId, now, now) == 1) {
            "Only your draft or scheduled post can be published"
        }
        return requireNotNull(dao.getPost(postId))
    }

    suspend fun publishScheduledIfDue(
        postId: String,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ): Boolean = dao.publishDue(postId, now, now) == 1

    suspend fun editPublishedBody(
        postId: String,
        actorId: String,
        body: String?,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ): DynamicPostEntity {
        val post = requireOwnedPost(postId, actorId)
        require(post.status == DynamicPostStatuses.PUBLISHED) {
            "Only a published post can edit its body"
        }
        val normalizedBody = body.normalized()
        val objects = dao.observeObjects(postId).first()
        require(normalizedBody != null || objects.isNotEmpty()) {
            "published post cannot become empty"
        }
        check(dao.editPublishedBody(postId, actorId, normalizedBody, now) == 1) {
            "published post could not be edited"
        }
        return requireNotNull(dao.getPost(postId))
    }

    suspend fun archive(
        postId: String,
        actorId: String,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ): DynamicPostEntity {
        check(dao.archive(postId, actorId, now, now) == 1) {
            "Only your published post can be archived"
        }
        return requireNotNull(dao.getPost(postId))
    }

    suspend fun restoreArchive(
        postId: String,
        actorId: String,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ): DynamicPostEntity {
        check(dao.restoreArchive(postId, actorId, now) == 1) {
            "Only your archived post can be restored"
        }
        return requireNotNull(dao.getPost(postId))
    }

    suspend fun deletePost(
        postId: String,
        actorId: String,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ): DynamicPostEntity {
        check(dao.deletePostContent(postId, actorId, now)) {
            "Only your existing post can be deleted"
        }
        return requireNotNull(dao.getPost(postId))
    }

    suspend fun setReaction(
        postId: String,
        actorId: String,
        actorLabel: String,
        reaction: String?,
        source: String = DYNAMIC_POST_SOURCE_NATIVE,
        sourceRef: String? = null,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ) {
        requireInteractivePost(postId)
        require(actorId.isNotBlank()) { "actorId must not be blank" }
        require(actorLabel.isNotBlank()) { "actorLabel must not be blank" }
        val normalized = reaction.normalized()?.uppercase()
        val row = normalized?.let {
            DynamicPostInteractionEntity(
                id = Uuid.random().toString(),
                postId = postId,
                actorId = actorId,
                actorLabel = actorLabel,
                type = DynamicPostInteractionTypes.REACTION,
                value = it,
                source = source,
                sourceRef = sourceRef,
                createdAt = now,
                updatedAt = now,
            )
        }
        dao.replaceActorInteraction(
            interaction = row,
            postId = postId,
            actorId = actorId,
            type = DynamicPostInteractionTypes.REACTION,
        )
    }

    suspend fun setFavorite(
        postId: String,
        actorId: String,
        actorLabel: String,
        enabled: Boolean,
        source: String = DYNAMIC_POST_SOURCE_NATIVE,
        sourceRef: String? = null,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ) {
        requireInteractivePost(postId)
        require(actorId.isNotBlank()) { "actorId must not be blank" }
        require(actorLabel.isNotBlank()) { "actorLabel must not be blank" }
        val row = if (enabled) {
            DynamicPostInteractionEntity(
                id = Uuid.random().toString(),
                postId = postId,
                actorId = actorId,
                actorLabel = actorLabel,
                type = DynamicPostInteractionTypes.FAVORITE,
                source = source,
                sourceRef = sourceRef,
                createdAt = now,
                updatedAt = now,
            )
        } else {
            null
        }
        dao.replaceActorInteraction(
            interaction = row,
            postId = postId,
            actorId = actorId,
            type = DynamicPostInteractionTypes.FAVORITE,
        )
    }

    suspend fun addComment(
        postId: String,
        actorId: String,
        actorLabel: String,
        body: String,
        source: String = DYNAMIC_POST_SOURCE_NATIVE,
        sourceRef: String? = null,
        now: Long = Clock.System.now().toEpochMilliseconds(),
        id: String = Uuid.random().toString(),
    ): DynamicPostInteractionEntity {
        requireInteractivePost(postId)
        return createCommentLike(
            id = id,
            postId = postId,
            parentInteractionId = null,
            actorId = actorId,
            actorLabel = actorLabel,
            type = DynamicPostInteractionTypes.COMMENT,
            body = body,
            source = source,
            sourceRef = sourceRef,
            now = now,
        )
    }

    suspend fun reply(
        postId: String,
        parentInteractionId: String,
        actorId: String,
        actorLabel: String,
        body: String,
        source: String = DYNAMIC_POST_SOURCE_NATIVE,
        sourceRef: String? = null,
        now: Long = Clock.System.now().toEpochMilliseconds(),
        id: String = Uuid.random().toString(),
    ): DynamicPostInteractionEntity {
        requireInteractivePost(postId)
        val parent = requireNotNull(dao.getInteraction(parentInteractionId)) {
            "parent interaction not found"
        }
        require(parent.postId == postId) { "reply parent belongs to a different post" }
        require(
            parent.type == DynamicPostInteractionTypes.COMMENT ||
                parent.type == DynamicPostInteractionTypes.REPLY
        ) { "reply parent must be a comment or reply" }
        require(parent.deletedAt == null) { "cannot reply to a deleted comment" }
        return createCommentLike(
            id = id,
            postId = postId,
            parentInteractionId = parentInteractionId,
            actorId = actorId,
            actorLabel = actorLabel,
            type = DynamicPostInteractionTypes.REPLY,
            body = body,
            source = source,
            sourceRef = sourceRef,
            now = now,
        )
    }

    suspend fun deleteComment(
        interactionId: String,
        actorId: String,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ) {
        check(dao.tombstoneComment(interactionId, actorId, now, now) == 1) {
            "Only your active comment or reply can be deleted"
        }
    }

    private suspend fun createPost(
        id: String,
        authorId: String,
        authorLabel: String,
        body: String?,
        objects: List<DynamicPostObjectDraft>,
        status: String,
        scheduledAt: Long? = null,
        originalTimeZoneId: String? = null,
        publishedAt: Long? = null,
        sourceKind: String,
        sourceRef: String?,
        originalText: String? = null,
        originalCreatedAt: Long? = null,
        now: Long,
    ): DynamicPostEntity {
        require(authorId.isNotBlank()) { "authorId must not be blank" }
        require(authorLabel.isNotBlank()) { "authorLabel must not be blank" }
        require(status in DynamicPostStatuses.ALL) { "Unknown post status: $status" }
        require(sourceKind.isNotBlank()) { "sourceKind must not be blank" }
        val normalizedBody = body.normalized()
        validateObjects(objects)
        require(normalizedBody != null || objects.isNotEmpty()) {
            "post must contain text or an object"
        }
        val post = DynamicPostEntity(
            id = id,
            authorId = authorId,
            authorLabel = authorLabel,
            body = normalizedBody,
            status = status,
            scheduledAt = scheduledAt,
            originalTimeZoneId = originalTimeZoneId,
            publishedAt = publishedAt,
            sourceKind = sourceKind,
            sourceRef = sourceRef,
            originalText = originalText,
            originalCreatedAt = originalCreatedAt,
            createdAt = now,
            updatedAt = now,
        )
        dao.insertPostWithObjects(post, objects.toRows(id, now))
        return post
    }

    private suspend fun createCommentLike(
        id: String,
        postId: String,
        parentInteractionId: String?,
        actorId: String,
        actorLabel: String,
        type: String,
        body: String,
        source: String,
        sourceRef: String?,
        now: Long,
    ): DynamicPostInteractionEntity {
        require(actorId.isNotBlank()) { "actorId must not be blank" }
        require(actorLabel.isNotBlank()) { "actorLabel must not be blank" }
        require(source.isNotBlank()) { "source must not be blank" }
        val normalizedBody = body.normalized()
            ?: error("$type body must not be blank")
        val row = DynamicPostInteractionEntity(
            id = id,
            postId = postId,
            parentInteractionId = parentInteractionId,
            actorId = actorId,
            actorLabel = actorLabel,
            type = type,
            value = normalizedBody,
            source = source,
            sourceRef = sourceRef,
            createdAt = now,
            updatedAt = now,
        )
        dao.insertInteraction(row)
        return row
    }

    private suspend fun requireOwnedPost(postId: String, actorId: String): DynamicPostEntity {
        val post = requireNotNull(dao.getPost(postId)) { "post not found" }
        require(post.authorId == actorId) { "post belongs to another actor" }
        require(post.deletedAt == null) { "post has been deleted" }
        return post
    }

    private suspend fun requireInteractivePost(postId: String): DynamicPostEntity {
        val post = requireNotNull(dao.getPost(postId)) { "post not found" }
        require(post.status == DynamicPostStatuses.PUBLISHED && post.deletedAt == null) {
            "interactions require a published post"
        }
        return post
    }

    private fun validateObjects(objects: List<DynamicPostObjectDraft>) {
        require(objects.all { it.kind.isNotBlank() && it.ref.isNotBlank() }) {
            "post objects require kind and ref"
        }
    }

    private fun List<DynamicPostObjectDraft>.toRows(postId: String, now: Long) =
        mapIndexed { index, objectRef ->
            DynamicPostObjectEntity(
                id = Uuid.random().toString(),
                postId = postId,
                kind = objectRef.kind.trim(),
                ref = objectRef.ref.trim(),
                label = objectRef.label?.trim()?.takeIf(String::isNotEmpty),
                payloadJson = objectRef.payloadJson,
                position = index,
                createdAt = now,
            )
        }

    private fun String?.normalized(): String? =
        this?.trim()?.takeIf(String::isNotEmpty)
}

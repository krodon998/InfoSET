package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.dao.MoodDAO
import me.rerere.rikkahub.data.db.entity.MoodEventEntity
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

const val INFOSET_USER_ACTOR_ID = "user:self"

class MoodRepository(
    private val dao: MoodDAO,
) {
    suspend fun setMood(
        actorId: String,
        actorLabel: String,
        type: String,
        intensity: Double? = null,
        startedAt: Long = Clock.System.now().toEpochMilliseconds(),
        expectedEndAt: Long? = null,
        emoji: String? = null,
        note: String? = null,
        visibleToPartner: Boolean = true,
        affectsContext: Boolean = true,
        source: String,
        sourceRef: String? = null,
        id: String = Uuid.random().toString(),
    ): MoodEventEntity {
        require(actorId.isNotBlank()) { "actorId must not be blank" }
        require(actorLabel.isNotBlank()) { "actorLabel must not be blank" }
        require(type.isNotBlank()) { "mood type must not be blank" }
        require(expectedEndAt == null || expectedEndAt > startedAt) {
            "expectedEndAt must be later than startedAt"
        }
        val now = Clock.System.now().toEpochMilliseconds()
        val event = MoodEventEntity(
            id = id,
            actorId = actorId,
            actorLabel = actorLabel,
            type = type.trim(),
            intensity = intensity,
            startedAt = startedAt,
            expectedEndAt = expectedEndAt,
            emoji = emoji?.trim()?.takeIf { it.isNotEmpty() },
            note = note?.trim()?.takeIf { it.isNotEmpty() },
            visibleToPartner = visibleToPartner,
            affectsContext = affectsContext,
            source = source,
            sourceRef = sourceRef,
            createdAt = now,
            updatedAt = now,
        )
        dao.replaceActiveMood(event)
        return event
    }

    suspend fun currentMood(
        actorId: String,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ): MoodEventEntity? = dao.getActiveMood(actorId, now)

    fun observeHistory(actorId: String, limit: Int = 100): Flow<List<MoodEventEntity>> =
        dao.observeHistory(actorId, limit)

    suspend fun endMood(
        actorId: String,
        moodId: String? = null,
        endedAt: Long = Clock.System.now().toEpochMilliseconds(),
    ): Int = if (moodId == null) {
        dao.endOpenMoods(actorId = actorId, endedAt = endedAt, updatedAt = endedAt)
    } else {
        dao.endMood(id = moodId, actorId = actorId, endedAt = endedAt, updatedAt = endedAt)
    }

    /**
     * 只提供显式允许影响上下文的当前状态。
     *
     * 助手始终能看到自己的 mood；其他 actor 还需 visibleToPartner=true。
     * 返回的是动态共享状态，不写入长期记忆。
     */
    suspend fun contextForAssistant(
        assistantActorId: String,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ): String {
        val moods = dao.getContextMoods(
            actorIds = listOf(INFOSET_USER_ACTOR_ID, assistantActorId).distinct(),
            now = now,
        )
            .groupBy { it.actorId }
            .mapNotNull { (_, rows) -> rows.maxByOrNull { it.startedAt } }
            .filter { it.actorId == assistantActorId || it.visibleToPartner }
            .sortedWith(compareBy<MoodEventEntity> { it.actorId != INFOSET_USER_ACTOR_ID }.thenBy { it.actorId })

        if (moods.isEmpty()) return ""
        return buildString {
            append("[Mood State]")
            moods.forEach { mood ->
                append("\n- ")
                append(mood.actorLabel)
                append(": ")
                mood.emoji?.let {
                    append(it)
                    append(' ')
                }
                append(mood.type)
                mood.intensity?.let {
                    append(" (intensity=")
                    append(it)
                    append(')')
                }
                mood.note?.let {
                    append("; ")
                    append(it)
                }
                append("; since=")
                append(Instant.fromEpochMilliseconds(mood.startedAt))
                mood.expectedEndAt?.let {
                    append("; expected_end=")
                    append(Instant.fromEpochMilliseconds(it))
                }
            }
        }
    }
}

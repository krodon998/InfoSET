package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.dao.SharedStateDAO
import me.rerere.rikkahub.data.db.entity.SharedStateEventEntity
import me.rerere.rikkahub.data.db.entity.SharedStateKinds
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

const val INFOSET_USER_ACTOR_ID = "user:self"

data class SharedStateSnapshot(
    val mood: SharedStateEventEntity?,
    val moment: SharedStateEventEntity?,
    val defaultDurationMs: Long?,
)

class SharedStateRepository(
    private val dao: SharedStateDAO,
) {
    suspend fun snapshot(
        actorId: String,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ): SharedStateSnapshot {
        dao.expireDue(actorId, now)
        return SharedStateSnapshot(
            mood = dao.getActive(actorId, SharedStateKinds.MOOD, now),
            moment = dao.getActive(actorId, SharedStateKinds.MOMENT, now),
            defaultDurationMs = dao.getLastDurationMs(actorId),
        )
    }

    fun observeHistory(actorId: String, limit: Int = 100): Flow<List<SharedStateEventEntity>> =
        dao.observeHistory(actorId, limit)

    /**
     * Update one or both values with one shared duration.
     *
     * Omitted values are preserved when currently active and only have their expiry retimed.
     * The first ever edit must provide durationMs; later edits may reuse the actor's last duration.
     */
    suspend fun update(
        actorId: String,
        actorLabel: String,
        mood: String? = null,
        moodPresetKey: String? = null,
        moodIntensity: Double? = null,
        moodEmoji: String? = null,
        moment: String? = null,
        durationMs: Long? = null,
        visibleToPartner: Boolean = true,
        affectsContext: Boolean = true,
        source: String,
        sourceRef: String? = null,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ): SharedStateSnapshot {
        require(actorId.isNotBlank()) { "actorId must not be blank" }
        require(actorLabel.isNotBlank()) { "actorLabel must not be blank" }
        val normalizedMood = mood?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedMoment = moment?.trim()?.takeIf { it.isNotEmpty() }
        require(normalizedMood != null || normalizedMoment != null) {
            "At least one of mood or moment must be provided"
        }

        dao.expireDue(actorId, now)
        val effectiveDuration = durationMs ?: dao.getLastDurationMs(actorId)
            ?: error("durationMs is required for the first shared-state edit")
        require(effectiveDuration > 0) { "durationMs must be positive" }
        val expectedEndAt = now + effectiveDuration

        normalizedMood?.let {
            dao.applyEdit(
                SharedStateEventEntity(
                    id = Uuid.random().toString(),
                    actorId = actorId,
                    actorLabel = actorLabel,
                    kind = SharedStateKinds.MOOD,
                    value = it,
                    presetKey = moodPresetKey?.trim()?.takeIf(String::isNotEmpty),
                    intensity = moodIntensity,
                    emoji = moodEmoji?.trim()?.takeIf(String::isNotEmpty),
                    startedAt = now,
                    durationMs = effectiveDuration,
                    expectedEndAt = expectedEndAt,
                    visibleToPartner = visibleToPartner,
                    affectsContext = affectsContext,
                    source = source,
                    sourceRef = sourceRef,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }

        normalizedMoment?.let {
            dao.applyEdit(
                SharedStateEventEntity(
                    id = Uuid.random().toString(),
                    actorId = actorId,
                    actorLabel = actorLabel,
                    kind = SharedStateKinds.MOMENT,
                    value = it,
                    startedAt = now,
                    durationMs = effectiveDuration,
                    expectedEndAt = expectedEndAt,
                    visibleToPartner = visibleToPartner,
                    affectsContext = affectsContext,
                    source = source,
                    sourceRef = sourceRef,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }

        // When both values are absent before this edit, only the edited state exists.
        // When another state is active, applyEdit retimes it to the same expectedEndAt.
        return snapshot(actorId, now)
    }

    suspend fun end(
        actorId: String,
        kind: String? = null,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ): Int {
        dao.expireDue(actorId, now)
        return if (kind == null) {
            dao.endAllActive(actorId, endedAt = now, updatedAt = now)
        } else {
            require(kind in SharedStateKinds.ALL) { "Unknown shared state kind: $kind" }
            dao.endActiveKind(actorId, kind, endedAt = now, updatedAt = now)
        }
    }

    suspend fun contextForAssistant(
        assistantActorId: String,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ): String {
        val states = dao.getContextStates(
            actorIds = listOf(INFOSET_USER_ACTOR_ID, assistantActorId).distinct(),
            now = now,
        )
            .groupBy { it.actorId to it.kind }
            .mapNotNull { (_, rows) -> rows.maxByOrNull { it.startedAt } }
            .filter { it.actorId == assistantActorId || it.visibleToPartner }
            .sortedWith(
                compareBy<SharedStateEventEntity> {
                    if (it.actorId == INFOSET_USER_ACTOR_ID) 0 else 1
                }.thenBy { it.actorId }.thenBy { it.kind }
            )

        if (states.isEmpty()) return ""
        return buildString {
            append("[Shared State]")
            states.groupBy { it.actorId }.forEach { (_, actorStates) ->
                val actor = actorStates.first()
                append("\n- ")
                append(actor.actorLabel)
                actorStates.firstOrNull { it.kind == SharedStateKinds.MOOD }?.let { mood ->
                    append("; mood=")
                    mood.emoji?.let {
                        append(it)
                        append(' ')
                    }
                    append(mood.value)
                    mood.intensity?.let {
                        append(" (intensity=")
                        append(it)
                        append(')')
                    }
                }
                actorStates.firstOrNull { it.kind == SharedStateKinds.MOMENT }?.let { moment ->
                    append("; moment=")
                    append(moment.value)
                }
                val end = actorStates.maxOfOrNull { it.expectedEndAt }
                end?.let {
                    append("; until=")
                    append(Instant.fromEpochMilliseconds(it))
                }
            }
        }
    }
}

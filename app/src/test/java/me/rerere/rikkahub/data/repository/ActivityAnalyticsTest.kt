package me.rerere.rikkahub.data.repository

import me.rerere.rikkahub.data.db.entity.ActivityJournalEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ActivityAnalyticsTest {
    @Test
    fun `summary counts active duration and excludes waiting plus running rows`() {
        val completed = activity("done", ActivityCategory.WORK, active = 4_000, waiting = 20_000, ended = 25_000)
        val instant = activity("instant", ActivityCategory.MEMORY, active = 0, waiting = 1_000, ended = 2_000)
        val running = activity("running", ActivityCategory.WORK, active = null, waiting = 0, ended = null)

        val summary = summarizeActivities(listOf(completed, instant, running))

        assertEquals(4_000, summary.activeDurationMs)
        assertEquals(2, summary.activityCount)
        assertEquals(4_000, summary.categories.first { it.category == ActivityCategory.WORK }.activeDurationMs)
        assertEquals(0, summary.categories.first { it.category == ActivityCategory.MEMORY }.activeDurationMs)
    }

    @Test
    fun `day week and month ranges use local calendar boundaries`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val anchor = LocalDate.of(2026, 11, 27)
        val day = activityRange(ActivityPeriod.DAY, anchor, zone)
        val week = activityRange(ActivityPeriod.WEEK, anchor, zone)
        val month = activityRange(ActivityPeriod.MONTH, anchor, zone)

        assertEquals(24 * 60 * 60 * 1_000L, day.toExclusive - day.fromInclusive)
        assertEquals(LocalDate.of(2026, 11, 23), epochDate(week.fromInclusive, zone))
        assertEquals(LocalDate.of(2026, 11, 1), epochDate(month.fromInclusive, zone))
    }

    private fun activity(
        id: String,
        category: ActivityCategory,
        active: Long?,
        waiting: Long,
        ended: Long?,
    ) = ActivityJournalEntity(
        id = id,
        actorId = DADDY_ACTOR_ID,
        category = category.name,
        title = id,
        executionSource = "TEST",
        startedAt = 1_000,
        endedAt = ended,
        activeDurationMs = active,
        waitingDurationMs = waiting,
        result = if (ended == null) ActivityResult.RUNNING.name else ActivityResult.SUCCEEDED.name,
        createdAt = 1_000,
        updatedAt = ended ?: 1_000,
    )

    private fun epochDate(epoch: Long, zone: ZoneId): LocalDate =
        java.time.Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()
}

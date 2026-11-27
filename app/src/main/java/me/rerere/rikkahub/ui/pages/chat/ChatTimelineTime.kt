package me.rerere.rikkahub.ui.pages.chat

import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime

internal const val CHAT_TIME_SEPARATOR_GAP_MINUTES = 5L

internal fun shouldShowChatTimeSeparator(
    previous: LocalDateTime?,
    current: LocalDateTime,
    gapMinutes: Long = CHAT_TIME_SEPARATOR_GAP_MINUTES,
): Boolean {
    if (previous == null) return true
    val gap = Duration.between(previous.toJavaLocalDateTime(), current.toJavaLocalDateTime())
    return gap.isNegative || gap.toMinutes() >= gapMinutes
}

internal fun formatChatTimelineTime(
    value: LocalDateTime,
    today: LocalDate = LocalDate.now(),
): String {
    val time = value.toJavaLocalDateTime()
    val date = time.toLocalDate()
    val clock = time.format(DateTimeFormatter.ofPattern("HH:mm"))
    return when {
        date == today -> clock
        date == today.minusDays(1) -> "昨天 $clock"
        date.year == today.year -> time.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
        else -> time.format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))
    }
}

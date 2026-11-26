package me.rerere.rikkahub.data.gifts

import kotlinx.serialization.Serializable

@Serializable
enum class DailyGiftLayer {
    /** Immutable material imported from 档案馆/5.6Sol留存/一年延时盒/. */
    LEGACY_5_6_SOL,

    /** New gifts appended by later InfoSET/Aire versions. */
    CONTINUATION,
}

@Serializable
enum class DailyGiftKind {
    MONTH_LETTER,
    DATE_ENTRY,
    MONTHLY_LIFE_LETTER,
}

/**
 * Path-level identity for the existing year/month/month-letter/date-folder layout.
 * Content is stored separately so legacy originals never need to be overwritten.
 */
@Serializable
data class DailyGiftLocator(
    val year: Int,
    val month: Int,
    val day: Int?,
    val kind: DailyGiftKind,
    val relativePath: String,
    val layer: DailyGiftLayer,
    val contentHash: String,
)

/**
 * Content-addressed identity for the dedicated twelve monthly life letters.
 * This source stays separate from delayed_box even when both letters unlock in the same month.
 */
@Serializable
data class MonthlyLifeLetterLocator(
    val year: Int,
    val month: Int,
    val day: Int,
    val kind: DailyGiftKind = DailyGiftKind.MONTHLY_LIFE_LETTER,
    val relativePath: String,
    val timezone: String,
    val contentHash: String,
    val manifestSchemaVersion: Int,
)

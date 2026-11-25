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

package me.rerere.ai.ui

import me.rerere.ai.core.MessageRole
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object MessageBreakConfig {
    const val TAG = "<message_break/>"
    const val DEFAULT_MAX_SEGMENTS = 3
    const val REPLY_EXCERPT_MAX_GRAPHEMES = 25
    const val REPLY_ELLIPSIS_COUNTS_TOWARD_LIMIT = true
}

enum class MessageBreakWarningCode {
    STRUCTURE_INTERRUPTED,
    SEGMENT_LIMIT_EXCEEDED,
    INVALID_TAG,
}

data class MessageBreakWarning(
    val code: MessageBreakWarningCode,
    val line: Int,
)

data class MessageBreakParseResult(
    val segments: List<String>,
    val fallback: Boolean = false,
    val warnings: List<MessageBreakWarning> = emptyList(),
    val fenceLiteralCount: Int = 0,
)

data class DerivedMessageSegment(
    val id: String,
    val index: Int,
    val sourcePartIndex: Int,
    val text: String,
)

/**
 * Parse model-authored message break markers without changing the archived source text.
 *
 * The parser is deliberately strict: an unescaped marker outside a fence must occupy its own line.
 * Invalid syntax falls back to one raw bubble so malformed model output can never lose content.
 */
fun parseMessageBreaks(
    raw: String,
    streaming: Boolean = false,
    maxSegments: Int = MessageBreakConfig.DEFAULT_MAX_SEGMENTS,
): MessageBreakParseResult {
    val effectiveMaxSegments = maxSegments.coerceAtLeast(1)
    val visibleRaw = if (streaming) raw.removeIncompleteMessageBreakSuffix() else raw
    if (visibleRaw.isEmpty()) return MessageBreakParseResult(emptyList())

    val lines = visibleRaw.split('\n')
    val segments = mutableListOf<String>()
    val current = mutableListOf<String>()
    val warnings = mutableListOf<MessageBreakWarning>()
    var fence: Fence? = null
    var fenceLiteralCount = 0

    fun flush() {
        val segment = current.joinToString("\n").trim('\n', '\r')
        current.clear()
        if (segment.isNotBlank()) segments += segment
    }

    lines.forEachIndexed { index, originalLine ->
        val line = originalLine.removeSuffix("\r")
        val activeFence = fence
        if (activeFence != null) {
            current += originalLine
            if (line.contains(MessageBreakConfig.TAG)) fenceLiteralCount++
            if (activeFence.closes(line)) fence = null
            return@forEachIndexed
        }

        Fence.opening(line)?.let { opening ->
            fence = opening
            current += originalLine
            return@forEachIndexed
        }

        val escaped = line.replace("\\${MessageBreakConfig.TAG}", MessageBreakConfig.TAG)
        if (line == "\\${MessageBreakConfig.TAG}") {
            current += escaped
            return@forEachIndexed
        }

        if (line == MessageBreakConfig.TAG) {
            if (breakInterruptsMarkdown(lines, index)) {
                warnings += MessageBreakWarning(MessageBreakWarningCode.STRUCTURE_INTERRUPTED, index + 1)
                return@forEachIndexed
            }
            if (current.all { it.isBlank() }) {
                current.clear()
                return@forEachIndexed
            }
            if (segments.size >= effectiveMaxSegments - 1) {
                warnings += MessageBreakWarning(MessageBreakWarningCode.SEGMENT_LIMIT_EXCEEDED, index + 1)
                return@forEachIndexed
            }
            flush()
            return@forEachIndexed
        }

        if (line.withoutEscapedMessageBreaks().contains(MessageBreakConfig.TAG)) {
            return MessageBreakParseResult(
                segments = listOf(visibleRaw),
                fallback = true,
                warnings = listOf(MessageBreakWarning(MessageBreakWarningCode.INVALID_TAG, index + 1)),
                fenceLiteralCount = fenceLiteralCount,
            )
        }
        current += escaped
    }
    flush()
    return MessageBreakParseResult(
        segments = segments,
        warnings = warnings,
        fenceLiteralCount = fenceLiteralCount,
    )
}

fun UIMessage.derivedMessageSegments(
    enabled: Boolean,
    streaming: Boolean = false,
    maxSegments: Int = MessageBreakConfig.DEFAULT_MAX_SEGMENTS,
): List<DerivedMessageSegment> {
    var segmentIndex = 0
    return buildList {
        parts.forEachIndexed { partIndex, part ->
            if (part !is UIMessagePart.Text) return@forEachIndexed
            val texts = if (enabled && role == MessageRole.ASSISTANT) {
                parseMessageBreaks(part.text, streaming = streaming, maxSegments = maxSegments).segments
            } else {
                listOf(part.text)
            }
            texts.filter(String::isNotBlank).forEach { text ->
                add(
                    DerivedMessageSegment(
                        id = "${this@derivedMessageSegments.id}:$segmentIndex",
                        index = segmentIndex,
                        sourcePartIndex = partIndex,
                        text = text,
                    )
                )
                segmentIndex++
            }
        }
    }
}

fun UIMessage.messageBreakDisplayText(
    enabled: Boolean,
    maxSegments: Int = MessageBreakConfig.DEFAULT_MAX_SEGMENTS,
): String = if (enabled && role == MessageRole.ASSISTANT) {
    derivedMessageSegments(enabled = true, maxSegments = maxSegments)
        .joinToString("\n\n", transform = DerivedMessageSegment::text)
} else {
    toText()
}

/** Runtime-only counters. No parser result or segment array is persisted into conversation data. */
object MessageBreakTelemetry {
    private val segmentCounts = ConcurrentHashMap<Int, AtomicLong>()
    private val fallbackCount = AtomicLong()
    private val fenceViolationCount = AtomicLong()
    private val crossChunkCount = AtomicLong()

    fun recordFinal(result: MessageBreakParseResult) {
        segmentCounts.computeIfAbsent(result.segments.size.coerceAtLeast(1)) { AtomicLong() }.incrementAndGet()
        if (result.fallback) fallbackCount.incrementAndGet()
        if (result.fenceLiteralCount > 0) fenceViolationCount.addAndGet(result.fenceLiteralCount.toLong())
    }

    fun recordCrossChunk() {
        crossChunkCount.incrementAndGet()
    }

    fun snapshot(): Snapshot = Snapshot(
        segmentCounts = segmentCounts.mapValues { it.value.get() }.toSortedMap(),
        fallbackCount = fallbackCount.get(),
        fenceViolationCount = fenceViolationCount.get(),
        crossChunkCount = crossChunkCount.get(),
    )

    data class Snapshot(
        val segmentCounts: Map<Int, Long>,
        val fallbackCount: Long,
        val fenceViolationCount: Long,
        val crossChunkCount: Long,
    )
}

/** Detects a marker completed by a later provider chunk without retaining message content. */
internal class MessageBreakChunkTracker {
    private var tail = ""

    fun append(chunk: String): Boolean {
        val combined = tail + chunk
        val crossed = tail.isNotEmpty() && combined.startsWith(MessageBreakConfig.TAG)
        tail = combined.longestMessageBreakPrefixSuffix()
        return crossed
    }
}

private data class Fence(val marker: Char, val length: Int) {
    fun closes(line: String): Boolean {
        val trimmed = line.trimStart().trimEnd()
        return trimmed.length >= length && trimmed.all { it == marker }
    }

    companion object {
        private val regex = Regex("^ {0,3}(`{3,}|~{3,})(?:.*)$")

        fun opening(line: String): Fence? {
            val marker = regex.matchEntire(line)?.groupValues?.get(1) ?: return null
            return Fence(marker.first(), marker.length)
        }
    }
}

private val listMarker = Regex("^ {0,3}(?:[-+*]|\\d{1,9}[.)])\\s+")
private val tableDelimiter = Regex("^\\s*\\|?(?:\\s*:?-{3,}:?\\s*\\|)+\\s*:?-{3,}:?\\s*\\|?\\s*$")

private fun breakInterruptsMarkdown(lines: List<String>, index: Int): Boolean {
    val previous = lines.neighbor(index, -1) ?: return false
    val next = lines.neighbor(index, 1) ?: return false
    val previousTrimmed = previous.trimStart()
    val nextTrimmed = next.trimStart()
    val list = listMarker.containsMatchIn(previous) &&
        (listMarker.containsMatchIn(next) || next.startsWith(" ") || next.startsWith("\t"))
    val quote = previousTrimmed.startsWith('>') && nextTrimmed.startsWith('>')
    val table = (previous.contains('|') && next.contains('|')) &&
        (tableDelimiter.matches(previous) || tableDelimiter.matches(next) ||
            (previousTrimmed.startsWith('|') && nextTrimmed.startsWith('|')))
    return list || quote || table
}

private fun List<String>.neighbor(index: Int, direction: Int): String? {
    var cursor = index + direction
    while (cursor in indices) {
        val line = this[cursor].removeSuffix("\r")
        if (line.isNotBlank() && line != MessageBreakConfig.TAG) return line
        cursor += direction
    }
    return null
}

private fun String.withoutEscapedMessageBreaks(): String = replace("\\${MessageBreakConfig.TAG}", "")

private fun String.removeIncompleteMessageBreakSuffix(): String {
    val suffix = longestMessageBreakPrefixSuffix()
    return if (suffix.isEmpty()) this else dropLast(suffix.length)
}

private fun String.longestMessageBreakPrefixSuffix(): String {
    val max = minOf(length, MessageBreakConfig.TAG.length - 1)
    for (size in max downTo 1) {
        val suffix = takeLast(size)
        if (MessageBreakConfig.TAG.startsWith(suffix)) return suffix
    }
    return ""
}

package me.rerere.rikkahub.data.gifts

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.db.dao.MailboxLetterDAO
import me.rerere.rikkahub.data.db.entity.MailboxLetterEntity
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipInputStream

private val delayedBoxZone: ZoneId = ZoneId.of("Asia/Shanghai")
private val sourceRefJson = Json { encodeDefaults = true }
private const val maxArchiveEntries = 5_000
private const val maxTextFileBytes = 2 * 1024 * 1024
private const val maxImportedTextBytes = 64 * 1024 * 1024

data class DelayedBoxImportReport(
    val imported: Int,
    val duplicates: Int,
    val ignored: Int,
)

data class ParsedDelayedBoxArchive(
    val letters: List<MailboxLetterEntity>,
    val ignored: Int,
)

class DelayedBoxImporter(private val dao: MailboxLetterDAO) {
    suspend fun importZip(
        input: InputStream,
        importedAt: Long = System.currentTimeMillis(),
    ): DelayedBoxImportReport {
        val parsed = DelayedBoxArchiveParser.parse(input, importedAt)
        var imported = 0
        var duplicates = 0
        parsed.letters.forEach { letter ->
            if (dao.insertIfAbsent(letter) == -1L) duplicates++ else imported++
        }
        return DelayedBoxImportReport(
            imported = imported,
            duplicates = duplicates,
            ignored = parsed.ignored,
        )
    }
}

/** Reads GitHub repository ZIPs as well as ZIPs rooted directly at 一年延时盒/. */
object DelayedBoxArchiveParser {
    private val supportedExtensions = setOf("md", "markdown", "txt")
    private val fullDate = Regex("(?<!\\d)(20\\d{2})-(0[1-9]|1[0-2])-([0-2]\\d|3[01])(?!\\d)")
    private val monthDay = Regex("(?<!\\d)(0[1-9]|1[0-2])-([0-2]\\d|3[01])(?!\\d)")
    private val yearSegment = Regex("^20\\d{2}$")
    private val writtenDate = Regex("(?m)^>\\s*写于[：:]\\s*(20\\d{2}-\\d{2}-\\d{2})")
    private val heading = Regex("(?m)^#\\s+(.+?)\\s*$")

    fun parse(input: InputStream, importedAt: Long = System.currentTimeMillis()): ParsedDelayedBoxArchive {
        val letters = mutableListOf<MailboxLetterEntity>()
        var ignored = 0
        var entryCount = 0
        var acceptedBytes = 0L

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                require(entryCount <= maxArchiveEntries) { "压缩包文件过多，已停止导入。" }

                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }

                val relativePath = delayedBoxRelativePath(entry.name)
                val extension = relativePath?.substringAfterLast('.', "")?.lowercase()
                val isReadme = relativePath?.substringAfterLast('/')?.equals("README.md", ignoreCase = true) == true
                if (relativePath == null || extension !in supportedExtensions || isReadme) {
                    ignored++
                    drainEntry(zip)
                    zip.closeEntry()
                    continue
                }

                val bytes = readEntry(zip, maxTextFileBytes)
                zip.closeEntry()
                if (bytes == null) {
                    ignored++
                    continue
                }
                acceptedBytes += bytes.size
                require(acceptedBytes <= maxImportedTextBytes) { "压缩包中的文本内容过大，已停止导入。" }

                val content = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
                val unlockDate = resolveUnlockDate(relativePath, content)
                if (content.isBlank() || unlockDate == null) {
                    ignored++
                    continue
                }
                letters += buildLetter(
                    relativePath = relativePath,
                    content = content,
                    rawBytes = bytes,
                    unlockDate = unlockDate,
                    sourceModifiedAt = entry.time,
                    importedAt = importedAt,
                )
            }
        }

        return ParsedDelayedBoxArchive(letters = letters, ignored = ignored)
    }

    private fun buildLetter(
        relativePath: String,
        content: String,
        rawBytes: ByteArray,
        unlockDate: LocalDate,
        sourceModifiedAt: Long,
        importedAt: Long,
    ): MailboxLetterEntity {
        val kind = if (relativePath.substringAfterLast('/').contains("月份信")) {
            DailyGiftKind.MONTH_LETTER
        } else {
            DailyGiftKind.DATE_ENTRY
        }
        val contentHash = sha256(rawBytes)
        val locator = DailyGiftLocator(
            year = unlockDate.year,
            month = unlockDate.monthValue,
            day = if (kind == DailyGiftKind.MONTH_LETTER) null else unlockDate.dayOfMonth,
            kind = kind,
            relativePath = relativePath,
            layer = DailyGiftLayer.LEGACY_5_6_SOL,
            contentHash = contentHash,
        )
        val stableIdHash = sha256("$relativePath\u0000$contentHash".toByteArray())
        val writtenAt = writtenDate.find(content)?.groupValues?.get(1)
            ?.let { LocalDate.parse(it).atStartOfDay(delayedBoxZone).toInstant().toEpochMilli() }
            ?: sourceModifiedAt.takeIf { it > 0 }
            ?: importedAt

        return MailboxLetterEntity(
            id = "delayed-box-${stableIdHash.take(40)}",
            title = heading.find(content)?.groupValues?.get(1)?.trim()
                ?: relativePath.substringAfterLast('/').substringBeforeLast('.'),
            summary = content.lineSequence()
                .map(String::trim)
                .firstOrNull { line ->
                    line.isNotEmpty() &&
                        !line.startsWith('#') &&
                        !line.startsWith('>') &&
                        !line.startsWith("```") &&
                        !line.startsWith("-") &&
                        !line.startsWith("*")
                }
                ?.take(240),
            salutation = null,
            // Keep the complete original text. Presentation can evolve without rewriting the archive.
            body = content,
            signature = null,
            unlockAt = unlockDate.atStartOfDay(delayedBoxZone).toInstant().toEpochMilli(),
            writtenAt = writtenAt,
            openedAt = null,
            source = "delayed_box",
            sourceRef = sourceRefJson.encodeToString(locator),
        )
    }

    private fun delayedBoxRelativePath(rawPath: String): String? {
        val path = rawPath.replace('\\', '/').trimStart('/')
        if (path.startsWith("__MACOSX/") || path.split('/').any { it.startsWith('.') }) return null

        val marker = "一年延时盒/"
        val markerIndex = path.indexOf(marker)
        if (markerIndex >= 0) return path.substring(markerIndex + marker.length).takeIf(String::isNotBlank)

        val segments = path.split('/').filter(String::isNotBlank)
        val yearIndex = segments.indexOfFirst { yearSegment.matches(it) }
        if (yearIndex >= 0) return segments.drop(yearIndex).joinToString("/")

        val datedFileIndex = segments.indexOfFirst { fullDate.containsMatchIn(it) }
        return if (datedFileIndex >= 0) segments.drop(datedFileIndex).joinToString("/") else null
    }

    private fun resolveUnlockDate(relativePath: String, content: String): LocalDate? {
        fullDate.find(content.lineSequence().firstOrNull { it.contains("开启") } ?: "")
            ?.toDateOrNull()
            ?.let { return it }
        fullDate.find(relativePath)?.toDateOrNull()?.let { return it }

        val segments = relativePath.split('/')
        val year = segments.firstNotNullOfOrNull { it.toIntOrNull()?.takeIf { value -> value in 2000..2099 } }
            ?: return null
        val monthDayMatch = segments.asReversed().firstNotNullOfOrNull { monthDay.find(it) }
            ?: heading.find(content)?.groupValues?.get(1)?.let(monthDay::find)
            ?: return null
        return runCatching {
            LocalDate.of(year, monthDayMatch.groupValues[1].toInt(), monthDayMatch.groupValues[2].toInt())
        }.getOrNull()
    }

    private fun MatchResult.toDateOrNull(): LocalDate? = runCatching {
        LocalDate.of(groupValues[1].toInt(), groupValues[2].toInt(), groupValues[3].toInt())
    }.getOrNull()

    private fun readEntry(input: InputStream, limit: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) {
                drainEntry(input)
                return null
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun drainEntry(input: InputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (input.read(buffer) >= 0) Unit
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}

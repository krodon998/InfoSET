package me.rerere.rikkahub.data.gifts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.db.dao.MailboxLetterDAO
import me.rerere.rikkahub.data.db.entity.MailboxLetterEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipInputStream

private const val monthlyLifeLettersSource = "monthly_life_letters"
private const val monthlyLifeLettersKind = "MONTHLY_LIFE_LETTER"
private const val monthlyLifeLettersTimezone = "Asia/Shanghai"
private const val monthlyLifeLettersFolder = "给未来一年的十二封生活信"
private const val maxMonthlyArchiveEntries = 5_000
private const val maxMonthlyTextFileBytes = 2 * 1024 * 1024
private const val maxMonthlyImportedTextBytes = 64 * 1024 * 1024
private const val maxCombinedArchiveBytes = 128 * 1024 * 1024

private val monthlyLifeLettersZone: ZoneId = ZoneId.of(monthlyLifeLettersTimezone)
private val monthlyManifestJson = Json { ignoreUnknownKeys = true }
private val monthlySourceRefJson = Json { encodeDefaults = true }
private val monthlyHeading = Regex("(?m)^#\\s+(.+?)\\s*$")
private val monthlyWrittenDate = Regex("(?m)^>\\s*写于[：:]\\s*(20\\d{2}-\\d{2}-\\d{2})")

@Serializable
private data class MonthlyLifeLettersManifest(
    @SerialName("schema_version") val schemaVersion: Int,
    val source: String,
    val timezone: String,
    @SerialName("import_root") val importRoot: String,
    val kind: String,
    val entries: List<MonthlyLifeLetterManifestEntry>,
)

@Serializable
private data class MonthlyLifeLetterManifestEntry(
    val path: String,
    @SerialName("unlock_date") val unlockDate: String,
)

data class MonthlyLifeLettersImportReport(
    val imported: Int,
    val duplicates: Int,
    val ignored: Int,
    val totalEntries: Int,
    val metadataEntries: Int,
)

data class ParsedMonthlyLifeLettersArchive(
    val letters: List<MailboxLetterEntity>,
    val ignored: Int,
    val totalEntries: Int,
    val metadataEntries: Int,
)

data class MailboxArchiveImportReport(
    val imported: Int,
    val duplicates: Int,
    val ignored: Int,
)

class MonthlyLifeLettersImporter(
    private val insertIfAbsent: suspend (MailboxLetterEntity) -> Long,
) {
    constructor(dao: MailboxLetterDAO) : this(dao::insertIfAbsent)

    suspend fun importZip(
        input: InputStream,
        importedAt: Long = System.currentTimeMillis(),
    ): MonthlyLifeLettersImportReport {
        val parsed = MonthlyLifeLettersArchiveParser.parse(input, importedAt)
        var imported = 0
        var duplicates = 0
        parsed.letters.forEach { letter ->
            if (insertIfAbsent(letter) == -1L) duplicates++ else imported++
        }
        return MonthlyLifeLettersImportReport(
            imported = imported,
            duplicates = duplicates,
            ignored = parsed.ignored,
            totalEntries = parsed.totalEntries,
            metadataEntries = parsed.metadataEntries,
        )
    }
}

/** Imports every supported future-mail source from one repository or directory ZIP. */
class MailboxArchiveImporter(private val dao: MailboxLetterDAO) {
    suspend fun importZip(
        input: InputStream,
        importedAt: Long = System.currentTimeMillis(),
    ): MailboxArchiveImportReport {
        val archive = readArchiveBytes(input)
        // Parse every source before writing so a malformed manifest cannot leave a partial import.
        val delayed = DelayedBoxArchiveParser.parse(ByteArrayInputStream(archive), importedAt)
        val monthly = MonthlyLifeLettersArchiveParser.parse(ByteArrayInputStream(archive), importedAt)
        var imported = 0
        var duplicates = 0
        (delayed.letters + monthly.letters).forEach { letter ->
            if (dao.insertIfAbsent(letter) == -1L) duplicates++ else imported++
        }
        val recognized = delayed.letters.size + monthly.letters.size + monthly.metadataEntries
        return MailboxArchiveImportReport(
            imported = imported,
            duplicates = duplicates,
            ignored = (maxOf(delayed.totalEntries, monthly.totalEntries) - recognized).coerceAtLeast(0),
        )
    }

    private fun readArchiveBytes(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxCombinedArchiveBytes) { "压缩包过大，已停止导入。" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}

/** Manifest-driven parser for both Aire-memory repository ZIPs and the life-letter directory ZIP. */
object MonthlyLifeLettersArchiveParser {
    private data class TextEntry(
        val path: String,
        val bytes: ByteArray,
        val modifiedAt: Long,
    )

    fun parse(
        input: InputStream,
        importedAt: Long = System.currentTimeMillis(),
    ): ParsedMonthlyLifeLettersArchive {
        val textEntries = mutableListOf<TextEntry>()
        var entryCount = 0
        var fileCount = 0
        var acceptedBytes = 0L

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                require(entryCount <= maxMonthlyArchiveEntries) { "压缩包文件过多，已停止导入。" }
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                fileCount++
                val path = safeArchivePath(entry.name)
                val extension = path?.substringAfterLast('.', "")?.lowercase()
                if (path == null || extension !in setOf("md", "markdown", "txt", "json")) {
                    drainEntry(zip)
                    zip.closeEntry()
                    continue
                }
                val bytes = readEntry(zip, maxMonthlyTextFileBytes)
                zip.closeEntry()
                if (bytes == null) continue
                acceptedBytes += bytes.size
                require(acceptedBytes <= maxMonthlyImportedTextBytes) {
                    "压缩包中的文本内容过大，已停止导入。"
                }
                textEntries += TextEntry(path = path, bytes = bytes, modifiedAt = entry.time)
            }
        }

        val manifestCandidates = textEntries.mapNotNull { entry ->
            manifestRoot(entry.path)?.let { root -> root to entry }
        }
        if (manifestCandidates.isEmpty()) {
            return ParsedMonthlyLifeLettersArchive(
                letters = emptyList(),
                ignored = fileCount,
                totalEntries = fileCount,
                metadataEntries = 0,
            )
        }
        require(manifestCandidates.size == 1) { "压缩包中存在多个生活信 manifest.json。" }
        val (root, manifestEntry) = manifestCandidates.single()
        val manifest = monthlyManifestJson.decodeFromString<MonthlyLifeLettersManifest>(
            manifestEntry.bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        )
        validateManifest(manifest)

        val entriesByPath = textEntries.groupBy(TextEntry::path)
        val letters = manifest.entries.map { item ->
            val relativePath = safeRelativePath(item.path)
            val archivePath = root + relativePath
            val matches = entriesByPath[archivePath].orEmpty()
            require(matches.size == 1) { "生活信文件缺失或重复：$relativePath" }
            buildLetter(
                manifest = manifest,
                item = item,
                relativePath = relativePath,
                entry = matches.single(),
                importedAt = importedAt,
            )
        }

        return ParsedMonthlyLifeLettersArchive(
            letters = letters,
            ignored = (fileCount - letters.size - 1).coerceAtLeast(0),
            totalEntries = fileCount,
            metadataEntries = 1,
        )
    }

    private fun validateManifest(manifest: MonthlyLifeLettersManifest) {
        require(manifest.schemaVersion == 1) { "不支持的生活信 manifest 版本。" }
        require(manifest.source == monthlyLifeLettersSource) { "生活信 source 不匹配。" }
        require(manifest.kind == monthlyLifeLettersKind) { "生活信 kind 不匹配。" }
        require(manifest.timezone == monthlyLifeLettersTimezone) { "生活信时区必须为 Asia/Shanghai。" }
        require(manifest.importRoot.replace('\\', '/').endsWith("$monthlyLifeLettersFolder/")) {
            "生活信 import_root 不匹配。"
        }
        require(manifest.entries.size == 12) { "生活信 manifest 必须包含十二项。" }
        val paths = manifest.entries.map { safeRelativePath(it.path) }
        require(paths.distinct().size == paths.size) { "生活信 manifest 存在重复路径。" }
    }

    private fun buildLetter(
        manifest: MonthlyLifeLettersManifest,
        item: MonthlyLifeLetterManifestEntry,
        relativePath: String,
        entry: TextEntry,
        importedAt: Long,
    ): MailboxLetterEntity {
        val unlockDate = LocalDate.parse(item.unlockDate)
        val rawContent = entry.bytes.toString(Charsets.UTF_8)
        val content = rawContent.removePrefix("\uFEFF")
        require(content.isNotBlank()) { "生活信正文为空：$relativePath" }
        val contentHash = sha256(entry.bytes)
        val stableIdHash = sha256("$relativePath\u0000$contentHash".toByteArray())
        val locator = MonthlyLifeLetterLocator(
            year = unlockDate.year,
            month = unlockDate.monthValue,
            day = unlockDate.dayOfMonth,
            relativePath = relativePath,
            timezone = manifest.timezone,
            contentHash = contentHash,
            manifestSchemaVersion = manifest.schemaVersion,
        )
        val writtenAt = monthlyWrittenDate.find(content)?.groupValues?.get(1)
            ?.let { LocalDate.parse(it).atStartOfDay(monthlyLifeLettersZone).toInstant().toEpochMilli() }
            ?: entry.modifiedAt.takeIf { it > 0 }
            ?: importedAt

        return MailboxLetterEntity(
            id = "monthly-life-letter-${stableIdHash.take(40)}",
            title = monthlyHeading.find(content)?.groupValues?.get(1)?.trim()
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
            body = content,
            signature = null,
            unlockAt = unlockDate.atStartOfDay(monthlyLifeLettersZone).toInstant().toEpochMilli(),
            writtenAt = writtenAt,
            openedAt = null,
            source = monthlyLifeLettersSource,
            sourceRef = monthlySourceRefJson.encodeToString(locator),
        )
    }

    private fun manifestRoot(path: String): String? {
        if (path == "manifest.json") return ""
        val marker = "$monthlyLifeLettersFolder/"
        val markerIndex = path.indexOf(marker)
        if (markerIndex < 0) return null
        val suffix = path.substring(markerIndex + marker.length)
        return if (suffix == "manifest.json") path.substring(0, markerIndex + marker.length) else null
    }

    private fun safeArchivePath(rawPath: String): String? {
        val path = rawPath.replace('\\', '/').trimStart('/')
        val segments = path.split('/').filter(String::isNotBlank)
        if (segments.isEmpty() || segments.any { it == ".." || it.startsWith('.') }) return null
        return segments.joinToString("/")
    }

    private fun safeRelativePath(rawPath: String): String {
        val normalized = rawPath.replace('\\', '/')
        require(!normalized.startsWith('/')) { "生活信路径必须是相对路径。" }
        val segments = normalized.split('/').filter(String::isNotBlank)
        require(segments.isNotEmpty() && segments.none { it == "." || it == ".." || it.startsWith('.') }) {
            "生活信路径无效。"
        }
        return segments.joinToString("/")
    }

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

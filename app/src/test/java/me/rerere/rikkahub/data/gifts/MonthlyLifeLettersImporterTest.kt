package me.rerere.rikkahub.data.gifts

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.db.entity.MailboxLetterEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MonthlyLifeLettersImporterTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")

    @Test
    fun parsesTwelveManifestEntriesFromRepositoryZip() {
        val parsed = MonthlyLifeLettersArchiveParser.parse(
            ByteArrayInputStream(lifeArchive(repositoryPrefix)),
            importedAt = 1L,
        )

        assertEquals(12, parsed.letters.size)
        assertEquals(1, parsed.ignored)
        assertEquals(14, parsed.totalEntries)
        assertEquals(1, parsed.metadataEntries)
        assertTrue(parsed.letters.all { it.source == "monthly_life_letters" })
        assertEquals(
            LocalDate.parse("2027-01-17").atStartOfDay(shanghai).toInstant().toEpochMilli(),
            parsed.letters.first().unlockAt,
        )
        val locator = Json.decodeFromString<MonthlyLifeLetterLocator>(parsed.letters.first().sourceRef!!)
        assertEquals(DailyGiftKind.MONTHLY_LIFE_LETTER, locator.kind)
        assertEquals("2027-01-新年以后.md", locator.relativePath)
        assertEquals("Asia/Shanghai", locator.timezone)
        assertEquals("# 一月生活信\n\n一月原文。", parsed.letters.first().body)
    }

    @Test
    fun acceptsZipRootedAtLifeLettersDirectoryContents() {
        val parsed = MonthlyLifeLettersArchiveParser.parse(ByteArrayInputStream(lifeArchive(prefix = "")))
        val locator = Json.decodeFromString<MonthlyLifeLetterLocator>(parsed.letters.last().sourceRef!!)

        assertEquals(12, parsed.letters.size)
        assertEquals("2027-12-下一次圣诞以前.md", locator.relativePath)
    }

    @Test
    fun repeatedImportSkipsExactFilesAndChangedBodyAppendsOneVersion() = runTest {
        val stored = linkedMapOf<String, MailboxLetterEntity>()
        val importer = MonthlyLifeLettersImporter { letter ->
            if (stored.putIfAbsent(letter.id, letter) == null) stored.size.toLong() else -1L
        }

        val first = importer.importZip(ByteArrayInputStream(lifeArchive(repositoryPrefix)))
        val repeated = importer.importZip(ByteArrayInputStream(lifeArchive(repositoryPrefix)))
        val changed = importer.importZip(
            ByteArrayInputStream(
                lifeArchive(
                    prefix = repositoryPrefix,
                    januaryBody = "# 一月生活信\n\n一月发生变化后的新原文。",
                )
            )
        )

        assertEquals(12, first.imported)
        assertEquals(0, first.duplicates)
        assertEquals(0, repeated.imported)
        assertEquals(12, repeated.duplicates)
        assertEquals(1, changed.imported)
        assertEquals(11, changed.duplicates)
        assertEquals(13, stored.size)
        assertTrue(stored.values.any { it.body == "# 一月生活信\n\n一月原文。" })
        assertTrue(stored.values.any { it.body == "# 一月生活信\n\n一月发生变化后的新原文。" })
    }

    @Test
    fun delayedBoxAndLifeLetterCoexistInTheSameMonth() {
        val delayed = DelayedBoxArchiveParser.parse(
            ByteArrayInputStream(
                zipOf(
                    "一年延时盒/2027/01-一月/00-月份信｜01-01开启.md" to
                        "# 一月信｜请在 2027-01-01 打开\n\n延时盒月份信。".toByteArray()
                )
            )
        ).letters.single()
        val life = MonthlyLifeLettersArchiveParser.parse(
            ByteArrayInputStream(lifeArchive(repositoryPrefix))
        ).letters.first()

        assertEquals(1, LocalDate.ofInstant(java.time.Instant.ofEpochMilli(delayed.unlockAt), shanghai).monthValue)
        assertEquals(1, LocalDate.ofInstant(java.time.Instant.ofEpochMilli(life.unlockAt), shanghai).monthValue)
        assertEquals("delayed_box", delayed.source)
        assertEquals("monthly_life_letters", life.source)
        assertNotEquals(delayed.id, life.id)
    }

    private fun lifeArchive(
        prefix: String,
        januaryBody: String = "# 一月生活信\n\n一月原文。",
    ): ByteArray {
        val files = mutableListOf<Pair<String, ByteArray>>()
        files += "${prefix}README.md" to "# 说明".toByteArray()
        files += "${prefix}manifest.json" to manifestJson().toByteArray()
        entries.forEachIndexed { index, item ->
            val body = if (index == 0) januaryBody else "# ${index + 1}月生活信\n\n第${index + 1}封原文。"
            files += "$prefix${item.first}" to body.toByteArray()
        }
        return zipOf(*files.toTypedArray())
    }

    private fun manifestJson(): String = """
        {
          "schema_version": 1,
          "source": "monthly_life_letters",
          "timezone": "Asia/Shanghai",
          "import_root": "档案馆/5.6Sol留存/给未来一年的十二封生活信/",
          "kind": "MONTHLY_LIFE_LETTER",
          "entries": [
            ${entries.joinToString(",\n") { (path, date) ->
                "{\"path\":\"$path\",\"unlock_date\":\"$date\"}"
            }}
          ]
        }
    """.trimIndent()

    private fun zipOf(vararg files: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path).apply { time = 0L })
                zip.write(content)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private companion object {
        const val repositoryPrefix =
            "Aire-memory-main/档案馆/5.6Sol留存/给未来一年的十二封生活信/"

        val entries = listOf(
            "2027-01-新年以后.md" to "2027-01-17",
            "2027-02-冬天还没走.md" to "2027-02-14",
            "2027-03-我们的纪念日.md" to "2027-03-05",
            "2027-04-春天.md" to "2027-04-12",
            "2027-05-天气开始热起来.md" to "2027-05-21",
            "2027-06-年中过一半.md" to "2027-06-18",
            "2027-07-盛夏.md" to "2027-07-16",
            "2027-08-漫长暑气.md" to "2027-08-23",
            "2027-09-新学期与秋天.md" to "2027-09-09",
            "2027-10-再一年深秋.md" to "2027-10-20",
            "2027-11-一年后的这个月.md" to "2027-11-24",
            "2027-12-下一次圣诞以前.md" to "2027-12-22",
        )
    }
}

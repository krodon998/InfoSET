package me.rerere.rikkahub.data.gifts

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
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

class DelayedBoxImporterTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")

    @Test
    fun parsesLegacyMonthLetterAndDailyFolderFromRepositoryZip() {
        val archive = zipOf(
            "Aire-memory-main/档案馆/5.6Sol留存/一年延时盒/README.md" to "# 说明",
            "Aire-memory-main/档案馆/5.6Sol留存/一年延时盒/2026-12-23｜第一只盒子.md" to """
                # 2026-12-23｜第一只盒子

                > 开启日期：2026-12-23

                第一只礼物。
            """.trimIndent(),
            "Aire-memory-main/档案馆/5.6Sol留存/一年延时盒/2027/01-一月/00-月份信｜01-01开启.md" to """
                # 一月信｜请在 2027-01-01 打开

                > 写于：2026-11-25
                > 开启日：2027-01-01

                宝宝，新年快乐。
            """.trimIndent(),
            "Aire-memory-main/档案馆/5.6Sol留存/一年延时盒/2027/01-一月/01-02/礼物.md" to """
                # 01-02｜小礼物

                今天也有一枚亲亲。
            """.trimIndent(),
            "Aire-memory-main/档案馆/5.6Sol留存/一年延时盒/2027/01-一月/01-02/照片.png" to "binary",
        )

        val parsed = DelayedBoxArchiveParser.parse(ByteArrayInputStream(archive), importedAt = 1L)

        assertEquals(3, parsed.letters.size)
        assertEquals(2, parsed.ignored)
        assertEquals(
            LocalDate.parse("2026-12-23").atStartOfDay(shanghai).toInstant().toEpochMilli(),
            parsed.letters[0].unlockAt,
        )
        assertEquals(
            LocalDate.parse("2027-01-01").atStartOfDay(shanghai).toInstant().toEpochMilli(),
            parsed.letters[1].unlockAt,
        )
        assertEquals(
            LocalDate.parse("2027-01-02").atStartOfDay(shanghai).toInstant().toEpochMilli(),
            parsed.letters[2].unlockAt,
        )
        assertTrue(parsed.letters.all { it.source == "delayed_box" })

        val monthLocator = Json.decodeFromString<DailyGiftLocator>(parsed.letters[1].sourceRef!!)
        assertEquals(DailyGiftKind.MONTH_LETTER, monthLocator.kind)
        assertEquals(DailyGiftLayer.LEGACY_5_6_SOL, monthLocator.layer)
        assertEquals("2027/01-一月/00-月份信｜01-01开启.md", monthLocator.relativePath)
        assertEquals(null, monthLocator.day)
    }

    @Test
    fun contentAddressedIdentityRepeatsExactlyAndAppendsChangedOriginal() {
        val path = "一年延时盒/2027/01-一月/01-03/礼物.md"
        val first = DelayedBoxArchiveParser.parse(
            ByteArrayInputStream(zipOf(path to "# 01-03｜礼物\n\n第一版原文。")),
        ).letters.single()
        val repeated = DelayedBoxArchiveParser.parse(
            ByteArrayInputStream(zipOf(path to "# 01-03｜礼物\n\n第一版原文。")),
        ).letters.single()
        val appended = DelayedBoxArchiveParser.parse(
            ByteArrayInputStream(zipOf(path to "# 01-03｜礼物\n\n后来追加的新版本。")),
        ).letters.single()

        assertEquals(first.id, repeated.id)
        assertEquals(first.sourceRef, repeated.sourceRef)
        assertNotEquals(first.id, appended.id)
        assertNotEquals(first.sourceRef, appended.sourceRef)
        assertEquals("# 01-03｜礼物\n\n第一版原文。", first.body)
    }

    @Test
    fun acceptsArchiveRootedAtYearAndIgnoresUndatedText() {
        val archive = zipOf(
            "2027/01-一月/01-04/礼物.md" to "# 01-04｜礼物\n\n第四天。",
            "随手记.md" to "这不是延时盒。",
        )

        val parsed = DelayedBoxArchiveParser.parse(ByteArrayInputStream(archive))

        assertEquals(1, parsed.letters.size)
        assertEquals(1, parsed.ignored)
        assertEquals("01-04｜礼物", parsed.letters.single().title)
    }

    private fun zipOf(vararg files: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path).apply { time = 0L })
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}

package me.rerere.rikkahub.ui.pages.setting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingCatalogTest {
    @Test
    fun everyEntryHasUniqueStableIdentityAndDestination() {
        val entries = SettingCatalog.entries

        assertTrue(entries.size >= 140)
        assertEquals(entries.size, entries.map { it.id }.distinct().size)
        assertTrue(entries.all { it.id.isNotBlank() })
        assertTrue(entries.all { it.breadcrumbRes.isNotEmpty() })
    }

    @Test
    fun everyVisibleTitleCanBeFound() {
        SettingCatalog.entries.forEach { expected ->
            val results = SettingCatalog.search("needle") { resourceId ->
                if (resourceId == expected.titleRes) "needle" else ""
            }

            assertTrue("Missing ${expected.id}", expected in results)
        }
    }

    @Test
    fun nestedAndAliasSearchesReachTheirOwningPages() {
        val emptyResolver: (Int) -> String = { "" }

        assertEquals(
            SettingDestination.INTERFACE,
            SettingCatalog.search("latex", emptyResolver).first().destination,
        )
        assertEquals(
            SettingDestination.NETWORK,
            SettingCatalog.search("socks5", emptyResolver).first().destination,
        )
        assertEquals(
            SettingDestination.BACKUP,
            SettingCatalog.search("整屋备份", emptyResolver).first().destination,
        )
        assertEquals(
            SettingDestination.EXTENSIONS,
            SettingCatalog.search("工作区", emptyResolver).first().destination,
        )
        assertEquals(
            SettingDestination.AIRE_INTELLIGENCE,
            SettingCatalog.search("Aire 与智能", emptyResolver).first().destination,
        )
        assertEquals(
            SettingDestination.APPS_SERVICES,
            SettingCatalog.search("App 与服务", emptyResolver).first().destination,
        )
    }

    @Test
    fun multipleTokensMustMatchTheSameSettingEntry() {
        val results = SettingCatalog.search("导入 备份") { "" }

        assertTrue(results.any { it.id == "backup.local_import" })
        assertTrue(results.none { it.id == "backup.local_export" })
    }
}

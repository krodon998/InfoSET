package me.rerere.rikkahub.ui.pages.setting

import me.rerere.rikkahub.data.datastore.AutomaticUpdates
import me.rerere.rikkahub.data.datastore.DockMaterial
import me.rerere.rikkahub.data.datastore.FolderCoverSize
import me.rerere.rikkahub.data.datastore.FolderGrid
import me.rerere.rikkahub.data.datastore.HomeGrid
import me.rerere.rikkahub.data.datastore.InfoOsSettings
import me.rerere.rikkahub.data.datastore.InfoOsDesktopFolder
import me.rerere.rikkahub.data.datastore.NewAppPlacement
import me.rerere.rikkahub.data.db.entity.ManagedFileEntity
import me.rerere.rikkahub.ui.theme.isWithinDarkSchedule
import me.rerere.rikkahub.ui.pages.desktop.shouldAttemptBiometric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InfoOsSettingsTest {
    @Test
    fun questionnaireDefaultsAreStable() {
        val settings = InfoOsSettings()

        assertEquals(AutomaticUpdates.WIFI_ONLY, settings.automaticUpdates)
        assertEquals(HomeGrid.FOUR_BY_SEVEN, settings.homeGrid)
        assertTrue(settings.desktopFolders.isEmpty())
        val folder = InfoOsDesktopFolder(id = "folder", name = "衡准")
        assertEquals(FolderCoverSize.ONE_BY_ONE, folder.coverSize)
        assertEquals(FolderGrid.THREE_BY_THREE, folder.expandedGrid)
        assertEquals(NewAppPlacement.HOME_AND_DRAWER, settings.newAppPlacement)
        assertFalse(settings.dockVisible)
        assertEquals(DockMaterial.LIQUID_GLASS, settings.dockMaterial)
    }

    @Test
    fun passcodeHashUsesSaltAndIsDeterministic() {
        val salt = ByteArray(16) { it.toByte() }
        assertEquals(hashPasscode("123456", salt), hashPasscode("123456", salt))
        assertNotEquals(hashPasscode("123456", salt), hashPasscode("654321", salt))
        assertNotEquals(hashPasscode("123456", salt), hashPasscode("123456", ByteArray(16)))
    }

    @Test
    fun appearanceTimeRoundTripsAndRejectsInvalidValues() {
        assertEquals(7 * 60 + 5, parseMinutes("07:05"))
        assertEquals("07:05", formatMinutes(7 * 60 + 5))
        assertEquals(null, parseMinutes("24:00"))
        assertEquals(null, parseMinutes("12:60"))
    }

    @Test
    fun overnightDarkScheduleCrossesMidnight() {
        assertTrue(isWithinDarkSchedule(23 * 60, 22 * 60, 7 * 60))
        assertTrue(isWithinDarkSchedule(6 * 60, 22 * 60, 7 * 60))
        assertFalse(isWithinDarkSchedule(12 * 60, 22 * 60, 7 * 60))
    }

    @Test
    fun biometricRequiresEveryRuntimePrecondition() {
        assertTrue(shouldAttemptBiometric(true, 35, true, true, true))
        assertFalse(shouldAttemptBiometric(false, 35, true, true, true))
        assertFalse(shouldAttemptBiometric(true, 27, true, true, true))
        assertFalse(shouldAttemptBiometric(true, 35, false, true, true))
        assertFalse(shouldAttemptBiometric(true, 35, true, false, true))
        assertFalse(shouldAttemptBiometric(true, 35, true, true, false))
    }

    @Test
    fun desktopLayoutSummaryExplainsDockRow() {
        assertEquals("4 × (6 + 1)", desktopLayoutSummary(HomeGrid.FOUR_BY_SIX, true))
        assertEquals("4 × (8 - 1)", desktopLayoutSummary(HomeGrid.FOUR_BY_SEVEN, false))
    }

    @Test
    fun avatarHistoryExcludesCurrentAndUsesLastUsedOrder() {
        fun avatar(id: Long, updatedAt: Long) = ManagedFileEntity(
            id = id,
            folder = "avatars",
            relativePath = "avatars/$id.jpg",
            displayName = "$id.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 1,
            createdAt = id,
            updatedAt = updatedAt,
        )
        val files = listOf(avatar(1, 10), avatar(2, 30), avatar(3, 20))

        val ordered = orderPastAvatars(
            files = files,
            currentUrl = "file://avatars/2.jpg",
            resolveUrl = { "file://${it.relativePath}" },
        )

        assertEquals(listOf(3L, 1L), ordered.map { it.id })
    }
}

package me.rerere.rikkahub.data.sync

import me.rerere.rikkahub.data.files.FileFolders
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupAttachmentFoldersTest {
    @Test
    fun `sticker library remains part of file backups`() {
        assertTrue(FileFolders.STICKERS in BackupManager.ATTACHMENT_FOLDERS)
    }
}

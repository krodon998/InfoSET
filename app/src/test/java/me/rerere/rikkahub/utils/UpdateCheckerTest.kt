package me.rerere.rikkahub.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun `maps a GitHub release to InfoSET update data`() {
        val info = decodeGitHubUpdateInfo(
            """
            {
              "tag_name": "v0.2.0",
              "published_at": "2026-11-25T14:17:38Z",
              "body": "New timeline foundation.",
              "assets": [
                {
                  "name": "InfoSET-0.2.0-universal.apk",
                  "browser_download_url": "https://github.com/krodon998/InfoSET/releases/download/v0.2.0/InfoSET-0.2.0-universal.apk",
                  "size": 60817408
                },
                {
                  "name": "source.zip",
                  "browser_download_url": "https://example.invalid/source.zip",
                  "size": 1024
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals("0.2.0", info.version)
        assertEquals("2026-11-25T14:17:38Z", info.publishedAt)
        assertEquals("New timeline foundation.", info.changelog)
        assertEquals(1, info.downloads.size)
        assertEquals("InfoSET-0.2.0-universal.apk", info.downloads.single().name)
        assertEquals("58.0 MB", info.downloads.single().size)
    }
}

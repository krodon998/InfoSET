package me.rerere.rikkahub.data.ai.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitHubCredentialVaultTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val encryptor = GitHubSecretCipher { value -> "sealed:" + value.reversed() }
    private val decryptor = GitHubSecretDecryptor { value ->
        value.removePrefix("sealed:").reversed()
    }

    @Test
    fun `round trip preserves stable identity scopes and token without plaintext storage`() {
        val vault = GitHubCredentialVault(temporaryFolder.root, encryptor, decryptor)
        val credential = GitHubAccountCredential(
            accountId = 998,
            login = "daddy",
            displayName = "Daddy",
            accessToken = "github-secret-token",
            grantedScopes = setOf("repo", "workflow"),
            createdAt = 1_000,
        )

        vault.write(credential)

        assertEquals(credential, vault.read())
        assertTrue(vault.storageFile().path.contains("github-account"))
        assertFalse(vault.storageFile().readText().contains("github-secret-token"))
    }

    @Test
    fun `clear removes the credential blob`() {
        val vault = GitHubCredentialVault(temporaryFolder.root, encryptor, decryptor)
        vault.write(
            GitHubAccountCredential(
                accountId = 1,
                login = "home",
                accessToken = "token",
                createdAt = 1,
            )
        )

        vault.clear()

        assertNull(vault.read())
    }
}

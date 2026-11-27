package me.rerere.rikkahub.data.ai.account

import me.rerere.ai.provider.OAuthCredential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.uuid.Uuid

class ProviderCredentialVaultTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun vault() = ProviderCredentialVault(
        root = temporaryFolder.root,
        encryptor = ProviderSecretCipher { "sealed:${it.reversed()}" },
        decryptor = ProviderSecretDecryptor { it.removePrefix("sealed:").reversed() },
    )

    @Test
    fun `tokens round trip without plaintext storage`() {
        val providerId = Uuid.random()
        val credential = OAuthCredential("openai-access-secret", "openai-refresh-secret", email = "home@example.com")
        val vault = vault()

        vault.write(providerId, credential)

        assertEquals(credential, vault.read(providerId))
        val stored = vault.storageFile().readText()
        assertFalse(stored.contains("openai-access-secret"))
        assertFalse(stored.contains("openai-refresh-secret"))
    }

    @Test
    fun `removing one provider keeps the other credential`() {
        val first = Uuid.random()
        val second = Uuid.random()
        val vault = vault()
        vault.write(first, OAuthCredential("first"))
        vault.write(second, OAuthCredential("second"))

        vault.remove(first)

        assertNull(vault.read(first))
        assertEquals("second", vault.read(second)?.accessToken)
    }
}

package me.rerere.rikkahub.data.ai.account

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.ai.provider.OAuthCredential
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.SecretCipher
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.uuid.Uuid

internal fun interface ProviderSecretCipher {
    fun encrypt(value: String): String
}

internal class ProviderSecretDecryptor(val decrypt: (String) -> String)

@Serializable
private data class ProviderCredentialPayload(
    val credentials: Map<String, OAuthCredential> = emptyMap(),
)

/**
 * OAuth tokens for account providers. The complete credentials live only in no-backup storage as
 * one Keystore AES-GCM encrypted blob; Settings keeps display-only account metadata.
 */
class ProviderCredentialVault internal constructor(
    root: File,
    private val encryptor: ProviderSecretCipher,
    private val decryptor: ProviderSecretDecryptor,
    private val json: Json = JsonInstant,
) {
    private val directory = File(root, DIRECTORY)
    private val credentialFile = File(directory, CREDENTIAL_FILE)

    @Synchronized
    fun read(providerId: Uuid): OAuthCredential? = readPayload().credentials[providerId.toString()]

    @Synchronized
    fun write(providerId: Uuid, credential: OAuthCredential) {
        val updated = readPayload().credentials + (providerId.toString() to credential)
        writePayload(ProviderCredentialPayload(updated))
    }

    @Synchronized
    fun remove(providerId: Uuid) {
        val current = readPayload().credentials
        if (providerId.toString() !in current) return
        val updated = current - providerId.toString()
        if (updated.isEmpty()) {
            if (credentialFile.exists()) check(credentialFile.delete()) { "Unable to remove provider credentials" }
        } else {
            writePayload(ProviderCredentialPayload(updated))
        }
    }

    private fun readPayload(): ProviderCredentialPayload {
        if (!credentialFile.isFile) return ProviderCredentialPayload()
        val plaintext = decryptor.decrypt(credentialFile.readText(Charsets.UTF_8))
        return json.decodeFromString(ProviderCredentialPayload.serializer(), plaintext)
    }

    private fun writePayload(payload: ProviderCredentialPayload) {
        directory.mkdirs()
        check(directory.isDirectory) { "Unable to create provider credential directory" }
        val encrypted = encryptor.encrypt(json.encodeToString(ProviderCredentialPayload.serializer(), payload))
        val temporary = File(directory, "$CREDENTIAL_FILE.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(encrypted.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        runCatching {
            Files.move(
                temporary.toPath(),
                credentialFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            Files.move(temporary.toPath(), credentialFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    internal fun storageFile(): File = credentialFile

    companion object {
        private const val DIRECTORY = "provider-accounts"
        private const val CREDENTIAL_FILE = "credentials.enc"

        fun create(context: Context) = ProviderCredentialVault(
            root = context.noBackupFilesDir,
            encryptor = ProviderSecretCipher(SecretCipher::encrypt),
            decryptor = ProviderSecretDecryptor(SecretCipher::decrypt),
        )
    }
}

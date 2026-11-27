package me.rerere.rikkahub.data.ai.account

import android.content.Context
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.SecretCipher
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun interface GitHubSecretCipher {
    fun encrypt(value: String): String
}

internal class GitHubSecretDecryptor(
    val decrypt: (String) -> String,
)

/**
 * Stores the complete GitHub credential in no-backup storage as one Keystore-encrypted blob.
 * Tokens never enter Settings JSON, Room, whole-house exports, logs, or Android cloud backup.
 */
class GitHubCredentialVault internal constructor(
    root: File,
    private val encryptor: GitHubSecretCipher,
    private val decryptor: GitHubSecretDecryptor,
    private val json: Json = JsonInstant,
) {
    private val directory = File(root, DIRECTORY)
    private val credentialFile = File(directory, CREDENTIAL_FILE)

    fun read(): GitHubAccountCredential? {
        if (!credentialFile.isFile) return null
        val plaintext = decryptor.decrypt(credentialFile.readText(Charsets.UTF_8))
        return json.decodeFromString(GitHubAccountCredential.serializer(), plaintext)
    }

    fun write(credential: GitHubAccountCredential) {
        directory.mkdirs()
        check(directory.isDirectory) { "Unable to create GitHub credential directory" }
        val encrypted = encryptor.encrypt(
            json.encodeToString(GitHubAccountCredential.serializer(), credential)
        )
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
            Files.move(
                temporary.toPath(),
                credentialFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    fun clear() {
        if (credentialFile.exists()) {
            check(credentialFile.delete()) { "Unable to remove GitHub credential" }
        }
    }

    internal fun storageFile(): File = credentialFile

    companion object {
        private const val DIRECTORY = "github-account"
        private const val CREDENTIAL_FILE = "credential.enc"

        fun create(context: Context) = GitHubCredentialVault(
            root = context.noBackupFilesDir,
            encryptor = GitHubSecretCipher(SecretCipher::encrypt),
            decryptor = GitHubSecretDecryptor(SecretCipher::decrypt),
        )
    }
}

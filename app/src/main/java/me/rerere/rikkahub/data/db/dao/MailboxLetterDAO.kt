package me.rerere.rikkahub.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.MailboxLetterEntity

/**
 * 未抵达信件的安全投影。查询层严格只携带标识与抵达时间，避免正文提前进入界面层。
 */
data class UpcomingLetterProjection(
    val id: String,
    @ColumnInfo("unlock_at") val unlockAt: Long,
)

data class ReadyLetterProjection(
    val id: String,
    val title: String,
    val summary: String?,
    @ColumnInfo("unlock_at") val unlockAt: Long,
)

data class ArchivedLetterProjection(
    val id: String,
    val title: String,
    val summary: String?,
    @ColumnInfo("unlock_at") val unlockAt: Long,
    @ColumnInfo("opened_at") val openedAt: Long,
)

@Dao
interface MailboxLetterDAO {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(letter: MailboxLetterEntity)

    /**
     * Append-only imports use IGNORE so importing the same archive again is idempotent.
     * A changed source file receives a different content-addressed id and is appended.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(letter: MailboxLetterEntity): Long

    @Query(
        "SELECT id, unlock_at FROM mailbox_letters " +
            "WHERE opened_at IS NULL AND unlock_at > :now ORDER BY unlock_at, id"
    )
    fun observeUpcoming(now: Long): Flow<List<UpcomingLetterProjection>>

    @Query(
        "SELECT id, title, summary, unlock_at FROM mailbox_letters " +
            "WHERE opened_at IS NULL AND unlock_at <= :now ORDER BY unlock_at, id"
    )
    fun observeReady(now: Long): Flow<List<ReadyLetterProjection>>

    @Query(
        "SELECT id, title, summary, unlock_at, opened_at FROM mailbox_letters " +
            "WHERE opened_at IS NOT NULL ORDER BY opened_at DESC"
    )
    fun observeArchived(): Flow<List<ArchivedLetterProjection>>

    @Query(
        "SELECT * FROM mailbox_letters " +
            "WHERE id = :id AND unlock_at <= :now LIMIT 1"
    )
    suspend fun getUnlocked(id: String, now: Long): MailboxLetterEntity?

    @Query(
        "UPDATE mailbox_letters SET opened_at = :openedAt " +
            "WHERE id = :id AND opened_at IS NULL AND unlock_at <= :openedAt"
    )
    suspend fun markOpened(id: String, openedAt: Long): Int

    @Transaction
    suspend fun open(id: String, now: Long): MailboxLetterEntity? {
        val letter = getUnlocked(id, now) ?: return null
        if (letter.openedAt == null) markOpened(id, now)
        return letter.copy(openedAt = letter.openedAt ?: now)
    }
}

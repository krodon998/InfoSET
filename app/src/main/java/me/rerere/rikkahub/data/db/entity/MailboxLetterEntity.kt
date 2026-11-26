package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一封只追加、不覆写正文的慢信。
 *
 * 未到期查询只能投影 [id] 与 [unlockAt]，正文不会跨过 DAO 边界。
 */
@Entity(
    tableName = "mailbox_letters",
    indices = [
        Index(value = ["unlock_at"]),
        Index(value = ["opened_at"]),
        Index(value = ["source", "source_ref"]),
    ]
)
data class MailboxLetterEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val summary: String?,
    val salutation: String?,
    val body: String,
    val signature: String?,
    @ColumnInfo("unlock_at")
    val unlockAt: Long,
    @ColumnInfo("written_at")
    val writtenAt: Long,
    @ColumnInfo("opened_at")
    val openedAt: Long?,
    /** ai / delayed_box / monthly_life_letters / import；原始来源用于未来追溯。 */
    val source: String,
    @ColumnInfo("source_ref")
    val sourceRef: String?,
)

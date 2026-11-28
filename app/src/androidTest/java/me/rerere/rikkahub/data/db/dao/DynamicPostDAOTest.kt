package me.rerere.rikkahub.data.db.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.entity.DynamicPostInteractionTypes
import me.rerere.rikkahub.data.db.entity.DynamicPostStatuses
import me.rerere.rikkahub.data.repository.DynamicPostObjectDraft
import me.rerere.rikkahub.data.repository.DynamicPostRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DynamicPostDAOTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: DynamicPostDAO
    private lateinit var repository: DynamicPostRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.dynamicPostDao()
        repository = DynamicPostRepository(dao)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun scheduledPostPublishesOnceAtActualTime() = runBlocking {
        val draft = repository.createDraft(
            id = "scheduled",
            authorId = "assistant:aire",
            authorLabel = "Aire",
            body = "稍后发",
            now = 100,
        )
        repository.updatePending(
            postId = draft.id,
            actorId = "assistant:aire",
            body = "稍后发",
            objects = emptyList(),
            scheduledAt = 500,
            timeZoneId = "Asia/Shanghai",
            now = 200,
        )
        val rescheduled = repository.updatePending(
            postId = draft.id,
            actorId = "assistant:aire",
            body = "改过正文但保留预约",
            objects = null,
            scheduledAt = 500,
            timeZoneId = "America/Chicago",
            now = 250,
        )
        assertEquals("Asia/Shanghai", rescheduled.originalTimeZoneId)
        assertEquals("改过正文但保留预约", rescheduled.body)
        assertTrue(repository.observeTimeline().first().isEmpty())
        assertFalse(repository.publishScheduledIfDue(draft.id, now = 499))
        assertTrue(repository.publishScheduledIfDue(draft.id, now = 600))
        assertFalse(repository.publishScheduledIfDue(draft.id, now = 601))

        val published = requireNotNull(repository.getPost(draft.id))
        assertEquals(DynamicPostStatuses.PUBLISHED, published.status)
        assertEquals(500L, published.scheduledAt)
        assertEquals(600L, published.publishedAt)
        assertEquals(draft.id, repository.observeTimeline().first().single().id)

        val cancelDraft = repository.createDraft(
            id = "cancel-schedule",
            authorId = "assistant:aire",
            authorLabel = "Aire",
            body = "取消预约",
            now = 700,
        )
        repository.updatePending(
            postId = cancelDraft.id,
            actorId = "assistant:aire",
            body = cancelDraft.body,
            scheduledAt = 900,
            timeZoneId = "Asia/Shanghai",
            now = 750,
        )
        val cancelled = repository.updatePending(
            postId = cancelDraft.id,
            actorId = "assistant:aire",
            body = cancelDraft.body,
            scheduledAt = null,
            now = 800,
        )
        assertEquals(DynamicPostStatuses.DRAFT, cancelled.status)
        assertEquals("Asia/Shanghai", cancelled.originalTimeZoneId)
        assertNull(cancelled.scheduledAt)
    }

    @Test
    fun publishedBodyEditsButObjectsFreezeAndArchiveDeleteStayDistinct() = runBlocking {
        val post = repository.publishNow(
            id = "post",
            authorId = "user:self",
            authorLabel = "隐",
            body = "原文",
            objects = listOf(DynamicPostObjectDraft("PHOTO", "media:1")),
            now = 100,
        )
        repository.editPublishedBody(post.id, "user:self", "改过的正文", now = 110)
        assertEquals("改过的正文", repository.getPost(post.id)?.body)
        assertEquals("media:1", repository.observeObjects(post.id).first().single().ref)
        assertTrue(runCatching {
            repository.editPublishedBody(post.id, "assistant:aire", "越权", now = 111)
        }.isFailure)

        repository.addComment(post.id, "assistant:aire", "Aire", "评论", now = 120)
        repository.archive(post.id, "user:self", now = 130)
        assertTrue(repository.observeTimeline().first().isEmpty())
        assertEquals(1, repository.observeInteractions(post.id).first().size)
        repository.restoreArchive(post.id, "user:self", now = 140)
        assertEquals(post.id, repository.observeTimeline().first().single().id)

        val deleted = repository.deletePost(post.id, "user:self", now = 150)
        assertEquals(DynamicPostStatuses.DELETED, deleted.status)
        assertNull(deleted.body)
        assertTrue(repository.observeTimeline().first().isEmpty())
        assertTrue(repository.observeObjects(post.id).first().isEmpty())
        assertTrue(repository.observeInteractions(post.id).first().isEmpty())
    }

    @Test
    fun oneReactionFavoriteAndImmutableThreadUseStableIds() = runBlocking {
        val post = repository.publishNow(
            id = "thread",
            authorId = "assistant:aire",
            authorLabel = "Aire",
            body = "今天",
            now = 100,
        )
        repository.setReaction(post.id, "user:self", "隐", "LIKE", now = 110)
        repository.setReaction(post.id, "user:self", "隐", "HEART", now = 111)
        val reaction = dao.getReaction(post.id, "user:self")
        assertEquals("HEART", reaction?.value)
        assertEquals(
            1,
            repository.observeInteractions(post.id).first()
                .count { it.type == DynamicPostInteractionTypes.REACTION },
        )
        repository.setReaction(post.id, "user:self", "隐", null, now = 112)
        assertNull(dao.getReaction(post.id, "user:self"))

        repository.setFavorite(post.id, "user:self", "隐", true, now = 113)
        assertTrue(dao.hasFavorite(post.id, "user:self"))
        repository.setFavorite(post.id, "user:self", "隐", false, now = 114)
        assertFalse(dao.hasFavorite(post.id, "user:self"))

        val comment = repository.addComment(
            post.id, "user:self", "隐", "第一层", now = 120, id = "comment"
        )
        val reply = repository.reply(
            post.id, comment.id, "assistant:aire", "Aire", "第二层", now = 121, id = "reply"
        )
        repository.deleteComment(comment.id, "user:self", now = 130)

        val rows = repository.observeInteractions(post.id).first()
        val deletedComment = rows.single { it.id == comment.id }
        val keptReply = rows.single { it.id == reply.id }
        assertNull(deletedComment.value)
        assertEquals(130L, deletedComment.deletedAt)
        assertEquals(comment.id, keptReply.parentInteractionId)
        assertEquals("第二层", keptReply.value)
        assertTrue(runCatching {
            repository.reply(post.id, comment.id, "assistant:aire", "Aire", "不能再接", now = 131)
        }.isFailure)
    }
}

package me.rerere.rikkahub.data.db.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.repository.LIFE_INTERACTION_FAVORITE
import me.rerere.rikkahub.data.repository.LIFE_INTERACTION_LIKE
import me.rerere.rikkahub.data.repository.LifeSurfaceObjectDraft
import me.rerere.rikkahub.data.repository.LifeSurfaceRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LifeSurfaceDAOTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: LifeSurfaceRepository
    private lateinit var dao: LifeSurfaceDAO

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.lifeSurfaceDao()
        repository = LifeSurfaceRepository(dao)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun scheduledMomentAndImportedFragmentPreserveCanonicalSource() = runBlocking {
        repository.createMoment(
            id = "scheduled",
            authorId = "assistant:aire",
            authorLabel = "Aire",
            body = "稍后再出现",
            objects = listOf(
                LifeSurfaceObjectDraft("SONG", "spotify:track:test", "一首歌"),
                LifeSurfaceObjectDraft("MOOD", "mood:test"),
            ),
            publishAt = 500,
        )
        assertTrue(repository.observeMoments(now = 499).first().isEmpty())
        assertEquals("scheduled", repository.observeMoments(now = 500).first().single().id)
        assertEquals(
            listOf("SONG", "MOOD"),
            repository.observeObjects("scheduled").first().map { it.kind },
        )

        val imported = repository.importMomentFragment(
            id = "imported",
            authorId = "user:self",
            authorLabel = "隐",
            originalText = "旧日生活片段原文。",
            originalCreatedAt = 100,
            sourceKind = "AIRE_MEMORY_ARCHIVE",
            sourceRef = "archive/old-diary#42",
        )
        repository.addComment(
            itemId = imported.id,
            actorId = "assistant:aire",
            actorLabel = "Aire",
            body = "后来追加的评论",
            createdAt = 200,
        )

        val afterInteraction = dao.getItem(imported.id)!!
        assertEquals("旧日生活片段原文。", afterInteraction.originalText)
        assertEquals(100L, afterInteraction.originalCreatedAt)
        assertEquals("AIRE_MEMORY_ARCHIVE", afterInteraction.sourceKind)
        assertEquals("archive/old-diary#42", afterInteraction.sourceRef)
        assertEquals("旧日生活片段原文。", afterInteraction.body)
        assertEquals("后来追加的评论", repository.observeInteractions(imported.id).first().single().body)
    }

    @Test
    fun boardStateReactionsRepliesAndExpiryStayIndependent() = runBlocking {
        val board = repository.leaveBoardMessage(
            id = "board",
            authorId = "assistant:aire",
            authorLabel = "Aire",
            targetActorId = "user:self",
            targetActorLabel = "隐",
            body = "记得回来看看。",
            publishAt = 100,
            expiresAt = 300,
        )
        assertEquals(board.id, repository.observeBoard("user:self", now = 150).first().single().id)

        repository.setBoardPinned(board.id, pinned = true, now = 160)
        repository.markBoardRead(board.id, now = 170)
        repository.markBoardResponded(board.id, now = 180)
        val marked = dao.getItem(board.id)!!
        assertEquals(160L, marked.pinnedAt)
        assertEquals(170L, marked.readAt)
        assertEquals(180L, marked.respondedAt)

        repository.setLike(board.id, "user:self", "隐", enabled = true, now = 190)
        repository.setFavorite(board.id, "user:self", "隐", enabled = true, now = 191)
        repository.setFavorite(board.id, "user:self", "隐", enabled = false, now = 192)
        val comment = repository.addComment(
            itemId = board.id,
            actorId = "user:self",
            actorLabel = "隐",
            body = "看到了",
            createdAt = 200,
        )
        val reply = repository.reply(
            itemId = board.id,
            parentInteractionId = comment.id,
            actorId = "assistant:aire",
            actorLabel = "Aire",
            body = "嗯。",
            createdAt = 210,
        )

        val interactions = repository.observeInteractions(board.id).first()
        assertTrue(interactions.any { it.type == LIFE_INTERACTION_LIKE })
        assertFalse(interactions.any { it.type == LIFE_INTERACTION_FAVORITE })
        assertEquals(comment.id, interactions.single { it.id == reply.id }.parentInteractionId)

        assertTrue(repository.observeBoard("user:self", now = 301).first().isEmpty())
        val expiredReaction = runCatching {
            repository.setLike(board.id, "user:self", "隐", enabled = false, now = 301)
        }
        assertTrue(expiredReaction.isFailure)
    }
}

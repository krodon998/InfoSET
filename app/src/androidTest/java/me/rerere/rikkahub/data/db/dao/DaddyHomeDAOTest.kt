package me.rerere.rikkahub.data.db.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.entity.ActivityJournalEntity
import me.rerere.rikkahub.data.db.entity.ActorCapabilityGrantEntity
import me.rerere.rikkahub.data.db.entity.GitHubRepositoryBindingEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaddyHomeDAOTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: DaddyHomeDAO

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.daddyHomeDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun grantRevocationAndSparseActivityRoundTrip() = runBlocking {
        val grant = ActorCapabilityGrantEntity(
            id = "grant",
            actorId = "daddy",
            capability = "GITHUB_REPOSITORY_READ",
            scopeType = "REPOSITORY",
            scopeValue = "krodon998/InfoSET",
            grantedBy = "user:hidden",
            executionSource = "TEST",
            createdAt = 10,
        )
        dao.upsertGrant(grant)
        assertEquals(grant.id, dao.findActiveGrant("daddy", grant.capability, grant.scopeType, grant.scopeValue, 20)?.id)
        assertEquals(1, dao.revokeGrant("daddy", grant.capability, grant.scopeType, grant.scopeValue, 30, "user:hidden"))
        assertNull(dao.findActiveGrant("daddy", grant.capability, grant.scopeType, grant.scopeValue, 40))

        val activity = ActivityJournalEntity(
            id = "activity",
            actorId = "daddy",
            category = "WORK",
            title = "Inspect repository",
            executionSource = "TEST",
            executionOrganizationRef = null,
            startedAt = 100,
            endedAt = 200,
            activeDurationMs = 25,
            waitingDurationMs = 75,
            result = "SUCCEEDED",
            createdAt = 100,
            updatedAt = 200,
        )
        dao.upsertActivity(activity)
        val restored = dao.observeActivities("daddy", 0, 1_000).first().single()
        assertEquals(25L, restored.activeDurationMs)
        assertEquals(75L, restored.waitingDurationMs)
        assertNull(restored.executionOrganizationRef)
        assertNull(restored.outputRefsJson)

        val binding = GitHubRepositoryBindingEntity(
            id = "binding",
            accountId = 998,
            actorId = "daddy",
            owner = "krodon998",
            repository = "InfoSET",
            createdAt = 300,
            updatedAt = 300,
        )
        dao.upsertRepositoryBinding(binding)
        assertEquals(
            binding.id,
            dao.findRepositoryBinding("daddy", 998, "KRODON998", "infoset")?.id,
        )
        assertNull(dao.findRepositoryBinding("daddy", 999, "krodon998", "InfoSET"))
    }
}

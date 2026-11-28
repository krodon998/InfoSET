package me.rerere.rikkahub.data.sync

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseFactory
import me.rerere.rikkahub.data.db.entity.MailboxLetterEntity
import me.rerere.rikkahub.data.db.entity.ActivityJournalEntity
import me.rerere.rikkahub.data.db.entity.ActorCapabilityGrantEntity
import me.rerere.rikkahub.data.db.entity.DynamicPostEntity
import me.rerere.rikkahub.data.db.entity.DynamicPostInteractionEntity
import me.rerere.rikkahub.data.db.entity.DynamicPostInteractionTypes
import me.rerere.rikkahub.data.db.entity.DynamicPostObjectEntity
import me.rerere.rikkahub.data.db.entity.DynamicPostStatuses
import me.rerere.rikkahub.data.db.entity.MemoryAtomEntity
import me.rerere.rikkahub.data.db.entity.MemoryRecallCandidateEntity
import me.rerere.rikkahub.data.db.entity.MemoryRecallTraceEntity
import me.rerere.rikkahub.data.db.entity.SharedStateEventEntity
import me.rerere.rikkahub.data.db.entity.SharedStateKinds
import me.rerere.rikkahub.data.model.MemoryAccessMode
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class BackupManagerTest {
    private lateinit var directory: File
    private lateinit var context: Context
    private lateinit var liveDatabase: AppDatabase
    private lateinit var manager: BackupManager

    @Before fun setUp() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        directory = Files.createTempDirectory(app.cacheDir.toPath(), "backup-manager-test-").toFile()
        context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(directory, "no-backup").apply { mkdirs() }
            override fun getDatabasePath(name: String): File = if (File(name).isAbsolute) File(name)
                else File(directory, "databases/$name").also { it.parentFile!!.mkdirs() }
        }
        liveDatabase = AppDatabaseFactory.create(context)
        liveDatabase.openHelper.writableDatabase.execSQL("CREATE TABLE backup_probe (text TEXT)")
        liveDatabase.openHelper.writableDatabase.execSQL("INSERT INTO backup_probe VALUES ('live')")
        manager = BackupManager(context, liveDatabase, GlobalContext.get().get<SettingsStore>(), JsonInstant)
    }

    @After fun tearDown() {
        liveDatabase.close()
        directory.deleteRecursively()
    }

    @Test fun oldArchiveIgnoresShmAndLeavesLiveDatabaseUntouchedUntilStartup() = runBlocking {
        val source = File(directory, "source")
        val archive = File(directory, "legacy.zip")
        withRoom(source) { room ->
            val db = room.openHelper.writableDatabase
            db.execSQL("CREATE TABLE backup_probe (text TEXT)")
            DatabaseBackup.checkpoint(db)
            db.query("PRAGMA wal_autocheckpoint=0").use { assertTrue(it.moveToFirst()) }
            db.execSQL("INSERT INTO backup_probe VALUES ('archived')")
            ZipOutputStream(archive.outputStream()).use { zip ->
                addFile(zip, DatabaseBackup.ARCHIVE_DATABASE, source)
                addFile(zip, DatabaseBackup.WAL, File(source.path + "-wal"))
                zip.putNextEntry(ZipEntry(DatabaseBackup.SHM))
                zip.write("deliberately invalid SHM".toByteArray())
                zip.closeEntry()
            }
        }
        manager.stageRestore(archive, includeDatabase = true, includeFiles = false)
        assertEquals("live", probe(liveDatabase))
        val staged = File(context.noBackupFilesDir, "backup-restore/pending/payload/database/rikka_hub")
        assertTrue(staged.isFile)
        assertFalse(File(staged.path + "-wal").exists())
        assertFalse(File(staged.path + "-shm").exists())
        liveDatabase.close()
        BackupManager.applyPendingRestore(context, JsonInstant)
        liveDatabase = AppDatabaseFactory.create(context)
        assertEquals("archived", probe(liveDatabase))
        ZipFile(archive).use { assertTrue(it.getEntry(DatabaseBackup.SHM) != null) }
    }

    @Test fun newArchiveContainsStandaloneDatabaseAndNoWalOrShm() = runBlocking {
        val archive = manager.createBackup(includeDatabase = true, includeFiles = false)
        ZipFile(archive).use { zip ->
            assertTrue(zip.getEntry("settings.json") != null)
            assertTrue(zip.getEntry(DatabaseBackup.ARCHIVE_DATABASE) != null)
            assertEquals(null, zip.getEntry(DatabaseBackup.WAL))
            assertEquals(null, zip.getEntry(DatabaseBackup.SHM))
        }
        manager.stageRestore(archive, includeDatabase = true, includeFiles = false)
        assertEquals("live", probe(liveDatabase))
    }

    @Test fun launchCountStaysInSettingsJsonThroughBackupAndStaging() = runBlocking {
        val archive = manager.createBackup(includeDatabase = false, includeFiles = false)
        val archived = ZipFile(archive).use { zip ->
            launchCountOf(zip.getInputStream(zip.getEntry("settings.json")).readBytes().decodeToString())
        }
        assertTrue(archived != null)
        manager.stageRestore(archive, includeDatabase = false, includeFiles = false)
        val staged = File(context.noBackupFilesDir, "backup-restore/pending/settings.json").readText()
        assertEquals(archived, launchCountOf(staged))
    }

    @Test fun monthlyLifeLetterStaysLockedAndSurvivesBackupRestore() = runBlocking {
        val unlockAt = LocalDate.parse("2027-03-05")
            .atStartOfDay(ZoneId.of("Asia/Shanghai"))
            .toInstant()
            .toEpochMilli()
        val letter = MailboxLetterEntity(
            id = "monthly-life-letter-backup-probe",
            title = "锁定标题",
            summary = "锁定摘要",
            salutation = null,
            body = "完整且不可覆盖的生活信正文。",
            signature = null,
            unlockAt = unlockAt,
            writtenAt = 1L,
            openedAt = null,
            source = "monthly_life_letters",
            sourceRef = "{\"kind\":\"MONTHLY_LIFE_LETTER\"}",
        )
        val dao = liveDatabase.mailboxLetterDao()
        dao.insert(letter)
        val upcoming = dao.observeUpcoming(unlockAt - 1).first().single()
        assertEquals(letter.id, upcoming.id)
        assertEquals(null, dao.getUnlocked(letter.id, unlockAt - 1))

        val archive = manager.createBackup(includeDatabase = true, includeFiles = false)
        manager.stageRestore(archive, includeDatabase = true, includeFiles = false)
        liveDatabase.close()
        assertTrue(BackupManager.applyPendingRestore(context, JsonInstant))
        liveDatabase = AppDatabaseFactory.create(context)

        val restoredDao = liveDatabase.mailboxLetterDao()
        assertEquals(null, restoredDao.getUnlocked(letter.id, unlockAt - 1))
        val restored = restoredDao.getUnlocked(letter.id, unlockAt)!!
        assertEquals("monthly_life_letters", restored.source)
        assertEquals("完整且不可覆盖的生活信正文。", restored.body)
        assertEquals(letter.sourceRef, restored.sourceRef)
    }

    @Test fun atomicMemoryKeepsStableIdentityAndNullableEventTimeThroughRestore() = runBlocking {
        val atom = MemoryAtomEntity(
            id = "memory-stable-across-restore",
            memoryId = "assistant",
            statement = "A sparse memory survives a whole-house move",
            occurredAt = null,
            importedAt = 1234,
            surfaceCount = 2,
            lastSurfacedAt = 1500,
            sourceKind = "MARKDOWN",
            sourceRef = "/profile.md",
            createdAt = 1234,
            updatedAt = 1234,
        )
        val trace = MemoryRecallTraceEntity(
            id = "trace-stable-across-restore",
            memoryId = atom.memoryId,
            conversationId = "conversation",
            query = "whole-house move",
            createdAt = 2000,
            tokenBudget = 128,
            injectedContent = atom.statement,
            injectedTokens = 12,
            destination = "ACTIVE_RECALL",
            mode = MemoryAccessMode.ACTIVE_RECALL.name,
            feedback = "HELPFUL",
        )
        val candidate = MemoryRecallCandidateEntity(
            traceId = trace.id,
            atomId = atom.id,
            rank = 1,
            statement = atom.statement,
            sourceSummary = atom.sourceRef,
            totalScore = 0.9,
            lexicalScore = 0.8,
            activationScore = 1.0,
            importanceScore = 0.5,
            injected = true,
            tokenCost = 12,
        )
        val memoryDao = liveDatabase.memoryAtomDao()
        memoryDao.upsertAtom(atom)
        memoryDao.recordRecall(trace, listOf(candidate), listOf(atom.id))

        val archive = manager.createBackup(includeDatabase = true, includeFiles = false)
        manager.stageRestore(archive, includeDatabase = true, includeFiles = false)
        liveDatabase.close()
        assertTrue(BackupManager.applyPendingRestore(context, JsonInstant))
        liveDatabase = AppDatabaseFactory.create(context)

        val restoredDao = liveDatabase.memoryAtomDao()
        val restored = restoredDao.getAtom(atom.id)!!
        assertEquals(atom.id, restored.id)
        assertEquals(atom.statement, restored.statement)
        assertEquals(atom.importedAt, restored.importedAt)
        assertEquals(null, restored.occurredAt)
        assertEquals(1, restored.recallCount)
        assertEquals(2, restored.surfaceCount)
        assertEquals(1500L, restored.lastSurfacedAt)
        assertEquals(trace.id, restoredDao.observeRecentTraces(atom.memoryId).first().single().id)
        assertEquals(MemoryAccessMode.ACTIVE_RECALL.name, restoredDao.getTrace(trace.id)!!.mode)
        assertEquals(candidate.atomId, restoredDao.getCandidates(trace.id).single().atomId)
    }

    @Test fun daddyGrantAndSparseActivitySurviveWholeHouseRestore() = runBlocking {
        val dao = liveDatabase.daddyHomeDao()
        val grant = ActorCapabilityGrantEntity(
            id = "grant-across-restore",
            actorId = "daddy",
            capability = "MEMORY_READ",
            scopeType = "GLOBAL",
            scopeValue = "*",
            grantedBy = "user:hidden",
            executionSource = "INFOSET_UI",
            createdAt = 1_000,
        )
        val activity = ActivityJournalEntity(
            id = "activity-across-restore",
            actorId = "daddy",
            category = "MEMORY",
            title = "Replay a private recall chain",
            executionSource = "INFOSET",
            executionOrganizationRef = null,
            billingSource = null,
            startedAt = 2_000,
            endedAt = 2_500,
            activeDurationMs = 100,
            waitingDurationMs = 400,
            result = "SUCCEEDED",
            createdAt = 2_000,
            updatedAt = 2_500,
        )
        dao.upsertGrant(grant)
        dao.upsertActivity(activity)

        val archive = manager.createBackup(includeDatabase = true, includeFiles = false)
        manager.stageRestore(archive, includeDatabase = true, includeFiles = false)
        liveDatabase.close()
        assertTrue(BackupManager.applyPendingRestore(context, JsonInstant))
        liveDatabase = AppDatabaseFactory.create(context)

        val restoredDao = liveDatabase.daddyHomeDao()
        val restoredGrant = restoredDao.observeGrants("daddy").first().single()
        val restoredActivity = restoredDao.observeActivities("daddy", 0, 10_000).first().single()
        assertEquals(grant.id, restoredGrant.id)
        assertEquals(null, restoredGrant.expiresAt)
        assertEquals(activity.id, restoredActivity.id)
        assertEquals(100L, restoredActivity.activeDurationMs)
        assertEquals(400L, restoredActivity.waitingDurationMs)
        assertEquals(null, restoredActivity.executionOrganizationRef)
        assertEquals(null, restoredActivity.outputRefsJson)
    }

    @Test fun sharedStateHistoryAndVisibilitySurviveWholeHouseRestore() = runBlocking {
        val mood = SharedStateEventEntity(
            id = "mood-across-restore",
            actorId = "user:self",
            actorLabel = "隐",
            kind = SharedStateKinds.MOOD,
            value = "困困的",
            presetKey = "sleepy",
            intensity = 0.7,
            emoji = "🌙",
            startedAt = 1_000,
            durationMs = 8_000,
            expectedEndAt = 9_000,
            visibleToPartner = true,
            affectsContext = false,
            source = "USER_UI",
            sourceRef = "test",
            createdAt = 1_000,
            updatedAt = 1_000,
        )
        val moment = SharedStateEventEntity(
            id = "moment-across-restore",
            actorId = "user:self",
            actorLabel = "隐",
            kind = SharedStateKinds.MOMENT,
            value = "在床上窝着",
            startedAt = 1_000,
            durationMs = 8_000,
            expectedEndAt = 9_000,
            visibleToPartner = true,
            affectsContext = true,
            source = "USER_UI",
            sourceRef = "test",
            createdAt = 1_000,
            updatedAt = 1_000,
        )
        val dao = liveDatabase.sharedStateDao()
        dao.insert(mood)
        dao.insert(moment)

        val archive = manager.createBackup(includeDatabase = true, includeFiles = false)
        manager.stageRestore(archive, includeDatabase = true, includeFiles = false)
        liveDatabase.close()
        assertTrue(BackupManager.applyPendingRestore(context, JsonInstant))
        liveDatabase = AppDatabaseFactory.create(context)

        val restoredDao = liveDatabase.sharedStateDao()
        val restoredMood = restoredDao.getActive(mood.actorId, SharedStateKinds.MOOD, 2_000)!!
        val restoredMoment = restoredDao.getActive(moment.actorId, SharedStateKinds.MOMENT, 2_000)!!
        assertEquals(mood.id, restoredMood.id)
        assertEquals("困困的", restoredMood.value)
        assertEquals("sleepy", restoredMood.presetKey)
        assertEquals(0.7, restoredMood.intensity!!, 0.0)
        assertEquals("🌙", restoredMood.emoji)
        assertTrue(restoredMood.visibleToPartner)
        assertFalse(restoredMood.affectsContext)
        assertEquals(8_000L, restoredMood.durationMs)
        assertEquals(9_000L, restoredMood.expectedEndAt)
        assertEquals(moment.id, restoredMoment.id)
        assertEquals("在床上窝着", restoredMoment.value)
        assertTrue(restoredMoment.affectsContext)
    }

    @Test fun dynamicPostsAndThreadsSurviveWholeHouseRestore() = runBlocking {
        val dao = liveDatabase.dynamicPostDao()
        val post = DynamicPostEntity(
            id = "post-across-restore",
            authorId = "assistant:aire",
            authorLabel = "Aire",
            body = "给未来的动态",
            status = DynamicPostStatuses.PUBLISHED,
            scheduledAt = 9_000,
            publishedAt = 10_000,
            sourceKind = "ASSISTANT_TOOL",
            sourceRef = "conversation:test",
            createdAt = 1_000,
            updatedAt = 1_000,
        )
        val objectRef = DynamicPostObjectEntity(
            id = "post-object-across-restore",
            postId = post.id,
            kind = "PHOTO",
            ref = "media:stable",
            label = "一张照片",
            position = 0,
            createdAt = 1_000,
        )
        val comment = DynamicPostInteractionEntity(
            id = "post-comment-across-restore",
            postId = post.id,
            actorId = "user:self",
            actorLabel = "隐",
            type = DynamicPostInteractionTypes.COMMENT,
            value = "等你发出来。",
            source = "USER_UI",
            createdAt = 1_100,
            updatedAt = 1_100,
        )
        dao.insertPostWithObjects(post, listOf(objectRef))
        dao.insertInteraction(comment)

        val archive = manager.createBackup(includeDatabase = true, includeFiles = false)
        manager.stageRestore(archive, includeDatabase = true, includeFiles = false)
        liveDatabase.close()
        assertTrue(BackupManager.applyPendingRestore(context, JsonInstant))
        liveDatabase = AppDatabaseFactory.create(context)

        val restoredDao = liveDatabase.dynamicPostDao()
        val restoredPost = restoredDao.getPost(post.id)!!
        val restoredObject = restoredDao.observeObjects(post.id).first().single()
        val restoredComment = restoredDao.observeInteractions(post.id).first().single()
        assertEquals(post.id, restoredPost.id)
        assertEquals(DynamicPostStatuses.PUBLISHED, restoredPost.status)
        assertEquals(9_000L, restoredPost.scheduledAt)
        assertEquals(10_000L, restoredPost.publishedAt)
        assertEquals("media:stable", restoredObject.ref)
        assertEquals(comment.id, restoredComment.id)
        assertEquals("等你发出来。", restoredComment.value)
    }

    @Test fun githubCredentialNoBackupBlobNeverEntersWholeHouseArchive() = runBlocking {
        val credential = File(context.noBackupFilesDir, "github-account/credential.enc")
        credential.parentFile!!.mkdirs()
        credential.writeText("sealed-token-material")

        val archive = manager.createBackup(includeDatabase = true, includeFiles = true)

        ZipFile(archive).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            assertFalse(names.any { it.contains("github-account") || it.endsWith("credential.enc") })
            assertFalse(names.filterNot { zip.getEntry(it).isDirectory }.any { name ->
                zip.getInputStream(zip.getEntry(name)).use { input ->
                    input.readBytes().decodeToString().contains("sealed-token-material")
                }
            })
        }
    }

    @Test fun archiveWithoutLaunchCountRestoresItAsZero() = runBlocking {
        val current = manager.createBackup(includeDatabase = false, includeFiles = false)
        val settingsJson = ZipFile(current).use { zip ->
            zip.getInputStream(zip.getEntry("settings.json")).readBytes().decodeToString()
        }
        val archive = File(directory, "no-launch-count.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("settings.json"))
            zip.write(JsonObject(JsonInstant.parseToJsonElement(settingsJson).jsonObject - "launchCount").toString().toByteArray())
            zip.closeEntry()
        }
        manager.stageRestore(archive, includeDatabase = false, includeFiles = false)
        val staged = File(context.noBackupFilesDir, "backup-restore/pending/settings.json").readText()
        assertEquals(0, launchCountOf(staged))
    }

    private fun launchCountOf(settingsJson: String): Int? =
        JsonInstant.parseToJsonElement(settingsJson).jsonObject["launchCount"]?.jsonPrimitive?.int

    @Test fun mediaCreationFilesKeepTheirDirectoriesAndSkipUnfinishedDownloads() = runBlocking {
        val session = File(context.filesDir, "media_creation/session")
        File(session, "record").mkdirs()
        File(session, "draft").mkdirs()
        File(session, "record/out_0.png").writeText("image")
        File(session, "record/out_1.mp4.part").writeText("half a video")
        File(session, "draft/asset.jpg").writeText("asset")
        val archive = manager.createBackup(includeDatabase = false, includeFiles = true)
        ZipFile(archive).use { zip ->
            assertTrue(zip.getEntry("media_creation/session/record/out_0.png") != null)
            assertTrue(zip.getEntry("media_creation/session/draft/asset.jpg") != null)
            assertEquals(null, zip.getEntry("media_creation/session/record/out_1.mp4.part"))
        }
        session.deleteRecursively()
        manager.stageRestore(archive, includeDatabase = false, includeFiles = true)
        assertFalse(session.exists())
        // Applying the staged settings would write to the real settings store of the app under test.
        assertTrue(File(context.noBackupFilesDir, "backup-restore/pending/settings.json").delete())
        assertTrue(BackupManager.applyPendingRestore(context, JsonInstant))
        assertEquals("image", File(session, "record/out_0.png").readText())
        assertEquals("asset", File(session, "draft/asset.jpg").readText())
        assertFalse(File(session, "record/out_1.mp4.part").exists())
    }

    @Test fun unsupportedSchemaIsRejectedBeforePublishing() = runBlocking {
        val future = File(directory, "future")
        withRoom(future) {
            it.openHelper.writableDatabase.version = 9999
        }
        val archive = File(directory, "future.zip")
        ZipOutputStream(archive.outputStream()).use { addFile(it, DatabaseBackup.ARCHIVE_DATABASE, future) }
        var failed = false
        try {
            manager.stageRestore(archive, includeDatabase = true, includeFiles = false)
        } catch (_: Exception) {
            failed = true
        }
        assertTrue(failed)
        assertFalse(File(context.noBackupFilesDir, "backup-restore/pending").exists())
        assertEquals("live", probe(liveDatabase))
    }

    private fun probe(database: AppDatabase): String = database.openHelper.readableDatabase
        .query("SELECT text FROM backup_probe").use { check(it.moveToFirst()); it.getString(0) }

    private fun addFile(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun <T> withRoom(file: File, block: (AppDatabase) -> T): T {
        val room = AppDatabaseFactory.create(context, file.path)
        return try {
            block(room)
        } finally {
            room.close()
        }
    }
}

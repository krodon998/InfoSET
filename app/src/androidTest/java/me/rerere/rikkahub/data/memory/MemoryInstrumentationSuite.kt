package me.rerere.rikkahub.data.memory

import me.rerere.rikkahub.data.db.dao.MemoryAtomDAOTest
import me.rerere.rikkahub.data.db.dao.LifeSurfaceDAOTest
import me.rerere.rikkahub.data.db.dao.MoodDAOTest
import me.rerere.rikkahub.data.db.dao.DaddyHomeDAOTest
import me.rerere.rikkahub.data.db.migrations.Migration_34_37_Test
import me.rerere.rikkahub.data.db.migrations.Migration_38_39_Test
import me.rerere.rikkahub.data.db.migrations.Migration_39_40_Test
import me.rerere.rikkahub.data.db.migrations.Migration_40_41_Test
import me.rerere.rikkahub.data.sync.BackupManagerTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    MemoryAtomDAOTest::class,
    MoodDAOTest::class,
    LifeSurfaceDAOTest::class,
    DaddyHomeDAOTest::class,
    Migration_34_37_Test::class,
    Migration_38_39_Test::class,
    Migration_39_40_Test::class,
    Migration_40_41_Test::class,
    BackupManagerTest::class,
)
class MemoryInstrumentationSuite

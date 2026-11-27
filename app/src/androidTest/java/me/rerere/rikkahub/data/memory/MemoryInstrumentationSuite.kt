package me.rerere.rikkahub.data.memory

import me.rerere.rikkahub.data.db.dao.MemoryAtomDAOTest
import me.rerere.rikkahub.data.db.migrations.Migration_34_37_Test
import me.rerere.rikkahub.data.sync.BackupManagerTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    MemoryAtomDAOTest::class,
    Migration_34_37_Test::class,
    BackupManagerTest::class,
)
class MemoryInstrumentationSuite

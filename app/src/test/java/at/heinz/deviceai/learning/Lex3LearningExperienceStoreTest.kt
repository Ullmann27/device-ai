package at.heinz.deviceai.learning

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

/** All histories here are synthetic and written only to per-test temporary directories. */
class Lex3LearningExperienceStoreTest {
    private fun record(index: Int) = LearningExperienceFactory.create(
        LearningExperienceType.USER_FACT, LearningProvenance.SYNTHETIC_FIXTURE,
        LearningTrustClass.OWNER_CONFIRMED, LearningStatus.CONFIRMED,
        "Synthetic history record $index", now = index * 86_400_000L)

    private fun inDirectory(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("lex3-fixture-").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }

    @Test fun protected257thRecordSurvivesRestartWithoutLosingLegacyBytes() = inDirectory { dir ->
        val store = FileLearningExperienceStore(dir)
        store.append(record(0))
        store.appendMissing((1..255).map(::record))
        val primary = dir.resolve("experiences-v2.bin").readBytes()
        val backup = dir.resolve("experiences-v2.lkg").readBytes()
        assertTrue(store.append(record(256)))
        assertEquals((0..256).map(::record), FileLearningExperienceStore(dir).all())
        assertArrayEquals(primary, dir.resolve("experiences-v3/legacy-v2.bin").readBytes())
        assertArrayEquals(backup, dir.resolve("experiences-v2.lkg").readBytes())
        assertThrows(Exception::class.java) {
            FileLearningExperienceStore.inspectSnapshot(dir.resolve("experiences-v2.bin").readBytes())
        }
    }

    @Test fun thousandDaysAtFourRecordsPerDayRetainEveryRecordAndRevocation() = inDirectory { dir ->
        val history = (0 until 4000).map(::record)
        assertEquals(4000, FileLearningExperienceStore(dir).appendMissing(history))
        val store = FileLearningExperienceStore(dir)
        assertTrue(store.updateStatus(history[100].id, LearningStatus.REVOKED, 4000L * 86_400_000L))
        val reopened = FileLearningExperienceStore(dir)
        assertEquals(4000, reopened.stats().total)
        assertEquals(1, reopened.stats().revoked)
        assertFalse(reopened.replay(LearningExperienceType.USER_FACT,128).any { it.id == history[100].id })
        assertEquals(history.last().id, reopened.all().last().id)
        assertEquals(128, reopened.replay(LearningExperienceType.USER_FACT,128).size)
        assertEquals(history.first(), reopened.all().first())
    }

    @Test fun migrationDoesNotResurrectRevokedRecordWhenOldInputIsReappended() = inDirectory { dir ->
        val old = record(0)
        val store = FileLearningExperienceStore(dir)
        store.appendMissing((0..255).map(::record))
        store.updateStatus(old.id,LearningStatus.REVOKED,30_000_000_000L)
        store.append(record(256))
        assertFalse(FileLearningExperienceStore(dir).append(old))
        assertEquals(LearningStatus.REVOKED, FileLearningExperienceStore(dir).all().first().status)
    }
}

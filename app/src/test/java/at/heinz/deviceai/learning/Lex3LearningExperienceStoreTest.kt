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

    @Test fun missingOrCorruptCommittedSegmentsNeverFallBackToLegacy() = inDirectory { dir ->
        val store=FileLearningExperienceStore(dir)
        store.appendMissing((0..255).map(::record))
        store.updateStatus(record(0).id,LearningStatus.REVOKED,30_000_000_000L)
        store.append(record(256))
        val segment=dir.resolve("experiences-v3").listFiles()!!.first { it.extension=="seg" }
        val original=segment.readBytes()
        segment.writeBytes(original.copyOf().also { it[it.size/2]=(it[it.size/2].toInt() xor 1).toByte() })
        assertThrows(Exception::class.java) { FileLearningExperienceStore(dir).all() }
        assertThrows(Exception::class.java) { store.append(record(999)) }
        segment.writeBytes(original)
        assertEquals(1,store.stats().revoked)
        assertTrue(segment.delete())
        assertThrows(Exception::class.java) { FileLearningExperienceStore(dir).all() }
    }

    @Test fun missingOrDamagedManifestCannotReopenOriginalSnapshot() = inDirectory { dir ->
        val store=FileLearningExperienceStore(dir)
        store.appendMissing((0..255).map(::record));store.append(record(256))
        val primary=dir.resolve("experiences-v2.bin")
        val bytes=primary.readBytes()
        primary.writeBytes(bytes.copyOf().also { it[12]=(it[12].toInt() xor 1).toByte() })
        assertThrows(Exception::class.java) { FileLearningExperienceStore(dir).all() }
        primary.delete()
        assertThrows(Exception::class.java) { FileLearningExperienceStore(dir).all() }
        assertThrows(Exception::class.java) { FileLearningExperienceStore(dir).append(record(700)) }
    }

    @Test fun cachedFingerprintStillDetectsSameLengthSegmentTampering() = inDirectory { dir ->
        val store=FileLearningExperienceStore(dir)
        store.appendMissing((0..256).map(::record))
        val first=store.snapshotIfChanged(null)
        assertEquals(257,first.second!!.size)
        assertNull(store.snapshotIfChanged(first.first).second)
        val segment=dir.resolve("experiences-v3").listFiles()!!.first { it.extension=="seg" }
        val mtime=segment.lastModified()
        segment.writeBytes(segment.readBytes().also { it[30]=(it[30].toInt() xor 1).toByte() })
        segment.setLastModified(mtime)
        assertThrows(Exception::class.java) { store.snapshotIfChanged(first.first) }
    }

    @Test fun restartAfterInterruptedMigrationKeepsOriginalThenSafelyRetries() = inDirectory { dir ->
        val store=FileLearningExperienceStore(dir)
        store.appendMissing((0..255).map(::record))
        val original=dir.resolve("experiences-v2.bin").readBytes()
        assertThrows(java.io.IOException::class.java) {
            Lex3LearningExperienceStore(dir).save((0..256).map(::record),original) {
                throw java.io.IOException("Injected process interruption before commit")
            }
        }
        assertArrayEquals(original,dir.resolve("experiences-v2.bin").readBytes())
        assertEquals(256,FileLearningExperienceStore(dir).all().size)
        assertTrue(FileLearningExperienceStore(dir).append(record(256)))
        assertEquals(257,FileLearningExperienceStore(dir).all().size)
        assertArrayEquals(original,dir.resolve("experiences-v3/legacy-v2.bin").readBytes())
    }

    @Test fun interruptedLaterWriteCannotUndoCommittedRevocation() = inDirectory { dir ->
        val store=FileLearningExperienceStore(dir)
        store.appendMissing((0..256).map(::record))
        store.updateStatus(record(0).id,LearningStatus.REVOKED,30_000_000_000L)
        val manifest=dir.resolve("experiences-v2.bin").readBytes()
        assertThrows(java.io.IOException::class.java) {
            Lex3LearningExperienceStore(dir).save((0..257).map(::record),manifest) {
                throw java.io.IOException("Injected pre-commit interruption")
            }
        }
        val restored=FileLearningExperienceStore(dir)
        assertEquals(257,restored.all().size)
        assertEquals(1,restored.stats().revoked)
        assertFalse(restored.append(record(0)))
        assertTrue(restored.append(record(258)))
        assertEquals(258,restored.all().size)
        assertEquals(9,dir.resolve("experiences-v3").listFiles()!!.count { it.extension=="seg" })
    }

    @Test fun hardCapacityRefusesOverflowWithoutChangingAnyCommittedRecord() = inDirectory { dir ->
        val store=FileLearningExperienceStore(dir)
        store.appendMissing((0 until 4096).map(::record))
        val before=dir.resolve("experiences-v2.bin").readBytes()
        assertThrows(IllegalStateException::class.java) { store.append(record(4096)) }
        assertArrayEquals(before,dir.resolve("experiences-v2.bin").readBytes())
        assertEquals(4096,FileLearningExperienceStore(dir).all().size)
    }

    @Test fun largeUnicodeRecordsMigrateBeforeLegacyByteLimitWithoutTruncation() = inDirectory { dir ->
        val records=(0 until 200).map { index ->
            LearningExperienceFactory.create(LearningExperienceType.USER_FACT,
                LearningProvenance.SYNTHETIC_FIXTURE,LearningTrustClass.OWNER_CONFIRMED,
                LearningStatus.CONFIRMED,"界".repeat(2040)+index,now=index.toLong())
        }
        val store=FileLearningExperienceStore(dir)
        assertEquals(200,store.appendMissing(records))
        assertTrue(Lex3LearningExperienceStore.isManifest(dir.resolve("experiences-v2.bin").readBytes()))
        assertEquals(records,FileLearningExperienceStore(dir).all())
    }

    @Test fun symlinksForSegmentDirectoryFilesAndLockAreRejected() = inDirectory { dir ->
        val outside=Files.createTempDirectory("lex3-outside-").toFile()
        try {
            Files.createSymbolicLink(dir.resolve("experiences.lock").toPath(),outside.resolve("lock").toPath())
            assertThrows(Exception::class.java) { FileLearningExperienceStore(dir).append(record(0)) }
            Files.delete(dir.resolve("experiences.lock").toPath())
            Files.createSymbolicLink(dir.resolve("experiences-v3").toPath(),outside.toPath())
            assertThrows(Exception::class.java) { FileLearningExperienceStore(dir).appendMissing((0..256).map(::record)) }
            Files.delete(dir.resolve("experiences-v3").toPath())
            FileLearningExperienceStore(dir).appendMissing((0..256).map(::record))
            val segment=dir.resolve("experiences-v3").listFiles()!!.first { it.extension=="seg" }
            val target=outside.resolve(segment.name)
            target.writeBytes(segment.readBytes());segment.delete()
            Files.createSymbolicLink(segment.toPath(),target.toPath())
            assertThrows(Exception::class.java) { FileLearningExperienceStore(dir).all() }
        } finally { outside.deleteRecursively() }
    }

    @Test fun independentProcessesCannotLoseConcurrentAppendsOrRevocation() = inDirectory { dir ->
        FileLearningExperienceStore(dir).appendMissing((0..256).map(::record))
        fun worker(start:Int)=ProcessBuilder(System.getProperty("java.home")+"/bin/java","-Xmx128m",
            "-cp",System.getProperty("java.class.path"),"at.heinz.deviceai.learning.Lex3ProcessProbe",
            dir.absolutePath,start.toString()).redirectErrorStream(true).start()
        val one=worker(300);val two=worker(500)
        for(process in listOf(one,two)) {
            assertTrue(process.waitFor(30,java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(process.inputStream.bufferedReader().readText(),0,process.exitValue())
        }
        val store=FileLearningExperienceStore(dir)
        assertEquals(457,store.all().size)
        assertEquals(1,store.stats().revoked)
    }

    @Test fun seededHoldoutInterleavesRestartDuplicateStatusAndReplay() = inDirectory { dir ->
        val random=java.util.Random(617L)
        val expected=(0..256).map(::record).associateBy { it.id }.toMutableMap()
        FileLearningExperienceStore(dir).appendMissing(expected.values.toList().shuffled(kotlin.random.Random(91)))
        repeat(24) { step ->
            val store=FileLearningExperienceStore(dir)
            val item=record(300+step)
            assertTrue(store.append(item));expected[item.id]=item
            val old=record(random.nextInt(257))
            val status=if(step%2==0)LearningStatus.REVOKED else LearningStatus.REJECTED
            val time=40_000_000_000L+step
            assertTrue(store.updateStatus(old.id,status,time))
            expected[old.id]=expected.getValue(old.id).copy(status=status,updatedAt=time)
            assertFalse(store.append(old))
            val restored=FileLearningExperienceStore(dir)
            assertEquals(expected,restored.all().associateBy { it.id })
            assertTrue(restored.replay(LearningExperienceType.USER_FACT,128).none {
                it.status in setOf(LearningStatus.REVOKED,LearningStatus.REJECTED)
            })
        }
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

/** Two real JVMs exercise the on-disk transaction lease, using only synthetic data. */
internal object Lex3ProcessProbe {
    @JvmStatic fun main(args:Array<String>) {
        val store=FileLearningExperienceStore(File(args[0]))
        val start=args[1].toInt()
        fun record(index:Int)=LearningExperienceFactory.create(LearningExperienceType.USER_FACT,
            LearningProvenance.SYNTHETIC_FIXTURE,LearningTrustClass.OWNER_CONFIRMED,
            LearningStatus.CONFIRMED,"Synthetic history record $index",now=index*86_400_000L)
        store.appendMissing((start until start+100).map(::record))
        check(store.updateStatus(record(0).id,LearningStatus.REVOKED,50_000_000_000L))
    }
}

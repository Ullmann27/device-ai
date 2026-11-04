package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** M2.12: persisted recovery report; backup/quarantine IO failures never block loading. */
class LedgerRecoveryDurabilityTest {
    private fun withDir(block:(File)->Unit) {
        val dir=Files.createTempDirectory("lex4-durable").toFile()
        try { block(dir) } finally { FileLearningExperienceStore.ioFaultForTest=null;dir.deleteRecursively();FileLearningExperienceStore.resetCachesForTest() }
    }
    private fun fact(i:Int)=LearningExperienceFactory.create(LearningExperienceType.USER_FACT,
        LearningProvenance.USER_CONFIRMED,LearningTrustClass.OWNER_CONFIRMED,LearningStatus.CONFIRMED,"Fakt $i",now=i.toLong())
    private val File.ledger get()=File(this,"experiences-v2.bin")
    private fun damageLastRecord(dir:File) { val b=dir.ledger.readBytes();b[b.size-1]=(b[b.size-1].toInt() xor 0x55).toByte();dir.ledger.writeBytes(b) }

    @Test fun recoveryReportSurvivesRestartAndHoldsNoContent() = withDir { dir ->
        val s=FileLearningExperienceStore(dir); s.appendMissing((0 until 3).map(::fact)); s.append(fact(3))
        damageLastRecord(dir); FileLearningExperienceStore.resetCachesForTest()
        val first=FileLearningExperienceStore(dir).also { it.all() }.lastRecovery()!!
        FileLearningExperienceStore.resetCachesForTest() // simulated process restart
        val again=FileLearningExperienceStore(dir).lastRecovery()!!
        assertEquals(first,again)
        val persisted=dir.listFiles()!!.first { it.name.startsWith("experiences-v2.recovery") }.readText()
        assertFalse("report file must not contain ledger content",persisted.contains("Fakt"))
    }

    @Test fun quarantineWriteFailureStillLoadsAndIsReported() = withDir { dir ->
        val s=FileLearningExperienceStore(dir); s.appendMissing((0 until 3).map(::fact)); s.append(fact(3))
        damageLastRecord(dir); FileLearningExperienceStore.resetCachesForTest()
        FileLearningExperienceStore.ioFaultForTest={ f -> if("quarantine" in f.name) throw IOException("disk full") }
        val reopened=FileLearningExperienceStore(dir)
        assertEquals((0 until 3).map(::fact),reopened.all())
        val r=reopened.lastRecovery()!!
        assertNull(r.quarantineFile); assertTrue(r.sideEffectFailures.any { "quarantine" in it })
        val sizeBefore=dir.ledger.length()
        assertThrows("writes refuse to drop unquarantined evidence",IOException::class.java) { reopened.append(fact(9)) }
        assertEquals(sizeBefore,dir.ledger.length())
        FileLearningExperienceStore.ioFaultForTest=null
        assertTrue(reopened.append(fact(9)))
        FileLearningExperienceStore.resetCachesForTest()
        assertEquals((0 until 3).map(::fact)+fact(9),FileLearningExperienceStore(dir).all())
    }

    @Test fun backupWriteFailureNeverBlocksOpen() = withDir { dir ->
        FileLearningExperienceStore(dir).appendMissing((0 until 3).map(::fact))
        FileLearningExperienceStore.resetCachesForTest()
        FileLearningExperienceStore.ioFaultForTest={ f -> if(".bak" in f.name) throw IOException("read-only") }
        assertEquals(3,FileLearningExperienceStore(dir).all().size)
        assertFalse(File(dir,"experiences-v2.bak1").exists())
    }
}

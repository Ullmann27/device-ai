package at.heinz.deviceai.learning

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** M2.10: LEX4 recovery (valid chain prefix + quarantine + report) and rolling 2-generation backups. */
class LedgerRecoveryTest {
    private fun withDir(block:(File)->Unit) {
        val dir=Files.createTempDirectory("lex4-recovery").toFile()
        try { block(dir) } finally { dir.deleteRecursively();FileLearningExperienceStore.resetCachesForTest() }
    }
    private fun fact(i:Int)=LearningExperienceFactory.create(LearningExperienceType.USER_FACT,
        LearningProvenance.USER_CONFIRMED,LearningTrustClass.OWNER_CONFIRMED,LearningStatus.CONFIRMED,"Fakt $i",now=i.toLong())
    private val File.ledger get()=File(this,"experiences-v2.bin")
    private fun flip(f:File,at:Long) { val b=f.readBytes();b[at.toInt()]=(b[at.toInt()].toInt() xor 0x55).toByte();f.writeBytes(b) }

    @Test fun corruptRecordInTheMiddleKeepsPrefixQuarantinesRestAndStaysWritable() = withDir { dir ->
        val store=FileLearningExperienceStore(dir)
        store.appendMissing((0 until 10).map(::fact)); store.append(fact(10))
        val cut=dir.ledger.length()
        (11 until 15).forEach { store.append(fact(it)) }
        val tail=dir.ledger.readBytes().copyOfRange(cut.toInt(),dir.ledger.length().toInt())
        flip(dir.ledger,cut+12)
        FileLearningExperienceStore.resetCachesForTest()
        val reopened=FileLearningExperienceStore(dir)
        assertEquals((0..10).map(::fact),reopened.all())
        val r=assertNotNull(reopened.lastRecovery()).let { reopened.lastRecovery()!! }
        assertEquals(cut,r.corruptOffset); assertEquals(4,r.discardedFrames); assertEquals(3,r.unverifiedFramesAfterCorruption)
        assertArrayEquals("quarantine keeps the removed bytes unchanged",tail.copyOf().also { it[12]=(it[12].toInt() xor 0x55).toByte() },r.quarantineFile!!.readBytes())
        assertTrue(r.quarantineFile!!.name.matches(Regex("experiences-v2\\.quarantine-\\d{8}T\\d{9}.*\\.bin")))
        assertTrue(reopened.append(fact(99)))
        FileLearningExperienceStore.resetCachesForTest()
        assertEquals((0..10).map(::fact)+fact(99),FileLearningExperienceStore(dir).all())
    }

    @Test fun corruptLastRecordDropsOnlyThatRecord() = withDir { dir ->
        val store=FileLearningExperienceStore(dir)
        store.appendMissing((0 until 5).map(::fact)); store.append(fact(5))
        flip(dir.ledger,dir.ledger.length()-1)
        FileLearningExperienceStore.resetCachesForTest()
        val reopened=FileLearningExperienceStore(dir)
        assertEquals((0 until 5).map(::fact),reopened.all())
        assertEquals(1,reopened.lastRecovery()!!.discardedFrames); assertEquals(0,reopened.lastRecovery()!!.unverifiedFramesAfterCorruption)
    }

    @Test fun corruptHeadIsRestoredFromNewestValidBackupAndWritable() = withDir { dir ->
        val store=FileLearningExperienceStore(dir)
        store.appendMissing((0 until 8).map(::fact))
        FileLearningExperienceStore.resetCachesForTest(); assertEquals(8,FileLearningExperienceStore(dir).all().size) // verified open -> backup
        val original=dir.ledger.readBytes()
        flip(dir.ledger,1)
        FileLearningExperienceStore.resetCachesForTest()
        val reopened=FileLearningExperienceStore(dir)
        assertEquals((0 until 8).map(::fact),reopened.all())
        val r=reopened.lastRecovery()!!
        assertEquals("experiences-v2.bak1",r.restoredFromBackup!!.name)
        assertArrayEquals(original.copyOf().also { it[1]=(it[1].toInt() xor 0x55).toByte() },r.quarantineFile!!.readBytes())
        assertTrue(reopened.append(fact(8)))
        FileLearningExperienceStore.resetCachesForTest()
        assertEquals((0..8).map(::fact),FileLearningExperienceStore(dir).all())
    }

    @Test fun rotationKeepsExactlyTwoGenerations() = withDir { dir ->
        val store=FileLearningExperienceStore(dir)
        val generations=(1..3).map { g -> store.append(fact(g)); FileLearningExperienceStore.resetCachesForTest()
            FileLearningExperienceStore(dir).all(); dir.ledger.readBytes() }
        assertArrayEquals(generations[2],File(dir,"experiences-v2.bak1").readBytes())
        assertArrayEquals(generations[1],File(dir,"experiences-v2.bak2").readBytes())
        assertFalse(File(dir,"experiences-v2.bak3").exists())
        assertEquals(2,dir.listFiles()!!.count { it.name.startsWith("experiences-v2.bak") })
    }

    @Test fun lottoHypothesisStaysUnconfirmedAfterRecovery() = withDir { dir ->
        val core=ContinuumLearningCore(dir)
        core.confirmOwnerFact("Ich wohne in Steyr."); core.confirmOwnerFact("Ich heiße Heinz.")
        val l=File(dir,"learning-core-v2/experiences-v2.bin"); flip(l,l.length()-1)
        FileLearningExperienceStore.resetCachesForTest()
        val again=ContinuumLearningCore(dir)
        again.confirmOwnerFact("Merke dir: Wenn ich im Lotto gewinne, kaufe ich ein Haus.")
        val all=FileLearningExperienceStore(File(dir,"learning-core-v2")).all()
        assertTrue(all.filter { it.status==LearningStatus.CONFIRMED }.none { "Lotto" in it.content })
        assertTrue(all.any { "Lotto" in it.content })
    }
}

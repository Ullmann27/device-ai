package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** M2.10 (Heinz, 04.11. 10:07): restored owner facts stay inactive until the owner reconfirms them. */
class RecoveryOwnerReconfirmationTest {
    private fun withDir(block:(File)->Unit) {
        val dir=Files.createTempDirectory("m210-reconfirm").toFile()
        try { block(dir) } finally { dir.deleteRecursively();FileLearningExperienceStore.resetCachesForTest() }
    }
    private fun ledger(dir:File)=File(dir,"learning-core-v2/experiences-v2.bin")
    private fun flip(f:File,at:Long) { val b=f.readBytes();b[at.toInt()]=(b[at.toInt()].toInt() xor 0x55).toByte();f.writeBytes(b) }
    private fun restart(dir:File):ContinuumLearningCore { FileLearningExperienceStore.resetCachesForTest();return ContinuumLearningCore(dir) }
    private fun owner(source:OwnerConfirmationSource=OwnerConfirmationSource.OWNER_PILL,yes:Boolean=true)=
        OwnerReconfirmationProof(source,yes,"turn-1",1L)
    private fun active(core:ContinuumLearningCore)=core.activeOwnerFacts().map { it.content }.toSet()

    @Test fun restoredFactsAreInactiveUntilOwnerReconfirms() = withDir { dir ->
        val core=ContinuumLearningCore(dir)
        core.confirmOwnerFact("Ich wohne in Steyr."); core.confirmOwnerFact("Ich heiße Heinz."); core.confirmOwnerFact("Ich trinke Tee.")
        flip(ledger(dir),ledger(dir).length()-1)
        val again=restart(dir)
        assertEquals(emptySet<String>(),active(again))
        assertEquals(2,again.pendingOwnerReconfirmation().size)
        assertEquals(0,ContinuumMemoryStatsReader(dir).read().confirmed)
        assertEquals(OwnerFactChange.HELD_UNCONFIRMED,again.confirmOwnerFact("Ich wohne in Steyr."))
        assertEquals(emptySet<String>(),active(restart(dir))) // persisted across a restart
        val after=restart(dir)
        assertEquals(2,after.reconfirmRecoveredOwnerFacts(owner()).size)
        assertEquals(setOf("Ich wohne in Steyr.","Ich heiße Heinz."),active(after))
        assertEquals(2,ContinuumMemoryStatsReader(dir).read().confirmed)
        assertEquals(emptySet<String>(),restart(dir).pendingOwnerReconfirmation())
    }

    @Test fun reconfirmationWithoutOwnerProofIsRejected() = withDir { dir ->
        val core=ContinuumLearningCore(dir)
        core.confirmOwnerFact("Ich wohne in Steyr."); core.confirmOwnerFact("Ich heiße Heinz.")
        flip(ledger(dir),ledger(dir).length()-1)
        val again=restart(dir)
        listOf<OwnerReconfirmationProof?>(null,owner(OwnerConfirmationSource.MODEL_TOOL),owner(OwnerConfirmationSource.FOREIGN_CONTENT),
            owner(yes=false),OwnerReconfirmationProof(OwnerConfirmationSource.OWNER_YES,true," ",1L)).forEach { proof ->
            try { again.reconfirmRecoveredOwnerFacts(proof);fail("accepted $proof") } catch(_:SecurityException) {}
        }
        assertEquals(emptySet<String>(),active(again))
        assertEquals(1,again.pendingOwnerReconfirmation().size)
    }

    @Test fun damagedRevocationFrameNeverReactivatesRevokedFact() = withDir { dir ->
        for(offsetFromEnd in listOf(1L,40L)) { // chain hash damaged (body readable) / body damaged
            val sub=File(dir,"case$offsetFromEnd")
            val core=ContinuumLearningCore(sub)
            core.confirmOwnerFact("Ich wohne in Steyr."); core.confirmOwnerFact("Ich heiße Heinz."); core.confirmOwnerFact("Ich trinke Tee.")
            core.revokeOwnerFact("Ich trinke Tee.")
            flip(ledger(sub),ledger(sub).length()-offsetFromEnd)
            val again=restart(sub)
            assertFalse("Ich trinke Tee." in active(again))
            again.reconfirmRecoveredOwnerFacts(owner())
            val tee=FileLearningExperienceStore(File(sub,"learning-core-v2")).all().first { it.content=="Ich trinke Tee." }
            assertTrue(again.reconfirmRecoveredOwnerFacts(owner(),setOf(tee.id)).isEmpty())
            assertEquals(setOf("Ich wohne in Steyr.","Ich heiße Heinz."),active(again))
            assertFalse("Ich trinke Tee." in active(restart(sub)))
        }
    }

    @Test fun normalStartWithoutRecoveryChangesNothing() = withDir { dir ->
        val core=ContinuumLearningCore(dir)
        core.confirmOwnerFact("Ich wohne in Steyr."); core.confirmOwnerFact("Ich heiße Heinz.")
        val again=restart(dir)
        assertEquals(emptySet<String>(),again.pendingOwnerReconfirmation())
        assertEquals(setOf("Ich wohne in Steyr.","Ich heiße Heinz."),active(again))
        val store=FileLearningExperienceStore(File(dir,"learning-core-v2"))
        assertEquals(store.all(),store.effective())
        assertFalse(File(dir,"learning-core-v2/experiences-v2.pending-reconfirm.properties").exists())
        assertTrue(again.reconfirmRecoveredOwnerFacts(owner()).isEmpty())
    }
}

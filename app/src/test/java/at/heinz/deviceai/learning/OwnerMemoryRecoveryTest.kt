package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OwnerMemoryRecoveryTest {
    private fun fixture(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("owner-memory-recovery-").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }

    private fun ledger(dir: File) = File(dir, "learning-core-v2/experiences-v2.bin")

    @Test fun automaticRecoveryHoldsRestoredFactsAcrossFreshCoreInstances() = fixture { dir ->
        val core = ContinuumLearningCore(dir)
        core.confirmOwnerFact("Ich wohne in Steyr.")
        core.confirmOwnerFact("Mein Hund heißt Bruno.")
        core.confirmOwnerFact("Ich trinke gerne Tee.")
        val backup = File(dir, "learning-core-v2/experiences-v2.lkg").readBytes()
        val damaged = ledger(dir).readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 0x55).toByte() }
        ledger(dir).writeBytes(damaged)

        val recovered = ContinuumLearningCore.withAutomaticRecovery(dir)
        assertTrue("restored owner facts are inactive", recovered.activeOwnerFacts().isEmpty())
        assertEquals(2, recovered.unconfirmedOwnerStatements().size)
        assertTrue(ContinuumLearningCore.withAutomaticRecovery(dir).activeOwnerFacts().isEmpty())
        assertTrue("strict readers must also honor the recovery barrier", ContinuumLearningCore(dir).activeOwnerFacts().isEmpty())
        assertArrayEquals("the last-known-good source is retained", backup,
            File(dir, "learning-core-v2/experiences-v2.lkg").readBytes())
        assertTrue("the damaged original is retained byte-for-byte",
            File(dir, "learning-core-v2/owner-recovery").walkTopDown()
                .filter { it.isFile && it.name == "primary.bin" }.any { it.readBytes().contentEquals(damaged) })
    }

    @Test fun corruptedRevocationIdentityCannotBecomeOwnerConfirmation() = fixture { dir ->
        val core = ContinuumLearningCore(dir)
        core.confirmOwnerFact("Ich wohne in Steyr.")
        val revoked = "Ich trinke gerne Tee."
        core.confirmOwnerFact(revoked)
        val id = core.activeOwnerFacts().single { it.content == revoked }.id
        core.revokeOwnerFact(revoked)
        val damaged = ledger(dir).readBytes()
        val needle = id.toByteArray(Charsets.UTF_8)
        val start = (0..damaged.size - needle.size).first { at ->
            needle.indices.all { damaged[at + it] == needle[it] }
        }
        repeat(needle.size) { damaged[start + it] = 0x7f }
        ledger(dir).writeBytes(damaged)

        val recovered = ContinuumLearningCore.withAutomaticRecovery(dir)
        assertTrue(recovered.activeOwnerFacts().isEmpty())
        assertEquals(OwnerFactChange.HELD_UNCONFIRMED, recovered.confirmOwnerFact(revoked))
        val recall = ContinuumThinkingMemory(dir, recovered).recall(
            ThinkingStep("damaged-revocation", 0, ThinkingPhase.RECALL, "Was trinke ich?", "Tee"))
        assertTrue("a recovered statement is never confirmed recall", recall.items.none { it.confirmed })
        assertTrue(ContinuumLearningCore.withAutomaticRecovery(dir).activeOwnerFacts().isEmpty())
    }
}

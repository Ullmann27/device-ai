package at.heinz.deviceai.learning

import at.heinz.deviceai.cognitive.IdentitySigner
import at.heinz.deviceai.cognitive.MemoryMutationReason
import at.heinz.deviceai.cognitive.memoryRecordHash
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** M2.3: owner corrections/revocations are signed via the existing ProofCarryingMemory, in a SIDECAR journal.
 * The LEX2 ledger bytes must stay identical with and without signing. Test-only HMAC key (not a secret). */
class SignedOwnerCorrectionJournalTest {
    private class HmacSigner(private val key: ByteArray) : IdentitySigner {
        override fun sign(payload: ByteArray): ByteArray =
            Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(payload) }
        override fun verify(payload: ByteArray, signature: ByteArray) =
            java.security.MessageDigest.isEqual(sign(payload), signature)
    }
    private val signer = HmacSigner("unit-test-only-key".toByteArray())
    private fun withDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("continuum-m2-pcm").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }
    private fun script(core: ContinuumLearningCore, clock: LongArray) {
        clock[0] = 1_000L; core.confirmOwnerFact("Ich wohne in Steyr.")
        clock[0] = 2_000L; core.confirmOwnerFact("Korrektur: Ich wohne in Enns.")
        clock[0] = 3_000L; core.revokeOwnerFact("Ich wohne in Steyr.")
    }

    @Test fun correctionsAndRevocationsFormAVerifiedSignedChain() = withDir { dir ->
        val clock = longArrayOf(0L)
        val core = ContinuumLearningCore(dir, signer) { clock[0] }
        script(core, clock)
        val journal = SignedOwnerCorrectionJournal(File(dir, "learning-core-v2"), signer)
        val entries = journal.entries()
        assertEquals(3, entries.size)
        val correction = entries[1].payload
        assertEquals(MemoryMutationReason.USER_CORRECTION, correction.reason)
        val steyr = core.activeOwnerFacts().none { it.content.contains("Steyr") }
        assertTrue(steyr)
        val steyrId = entries[0].payload.recordId
        assertTrue("correction cites the superseded fact", steyrId in correction.contradictionReferences)
        val revocation = entries[2].payload
        assertEquals(steyrId, revocation.recordId)
        assertEquals(entries[0].payload.afterHash, revocation.beforeHash)
        assertEquals(MemoryMutationReason.USER_CORRECTION, revocation.reason)
        val audit = core.correctionAudit()
        assertEquals(CorrectionAuditStatus.VERIFIED, audit.status)
        assertEquals(3, audit.signedMutations)
    }

    @Test fun theLedgerFormatIsByteIdenticalWithAndWithoutSigning() = withDir { a -> withDir { b ->
        val ca = longArrayOf(0L); val cb = longArrayOf(0L)
        script(ContinuumLearningCore(a, signer) { ca[0] }, ca)
        script(ContinuumLearningCore(b, { cb[0] }), cb)
        assertArrayEquals(File(a, "learning-core-v2/experiences-v2.bin").readBytes(),
            File(b, "learning-core-v2/experiences-v2.bin").readBytes())
        assertTrue(!File(b, "learning-core-v2/${SignedOwnerCorrectionJournal.FILE_NAME}").exists())
    } }

    @Test fun aTamperedJournalIsDetected() = withDir { dir ->
        val clock = longArrayOf(0L)
        val core = ContinuumLearningCore(dir, signer) { clock[0] }
        script(core, clock)
        val f = File(dir, "learning-core-v2/${SignedOwnerCorrectionJournal.FILE_NAME}")
        f.writeText(f.readText().replaceFirst("USER_CORRECTION", "CONSOLIDATION"))
        assertEquals(CorrectionAuditStatus.TAMPERED, core.correctionAudit().status)
    }

    @Test fun aForeignSignerCannotVerifyTheJournal() = withDir { dir ->
        val clock = longArrayOf(0L)
        script(ContinuumLearningCore(dir, signer) { clock[0] }, clock)
        val other = ContinuumLearningCore(dir, HmacSigner("other".toByteArray())) { clock[0] }
        assertEquals(CorrectionAuditStatus.TAMPERED, other.correctionAudit().status)
    }

    @Test fun aLedgerChangeOutsideTheSignedPathIsReported() = withDir { dir ->
        val clock = longArrayOf(0L)
        val signed = ContinuumLearningCore(dir, signer) { clock[0] }
        script(signed, clock)
        clock[0] = 4_000L
        ContinuumLearningCore(dir, { clock[0] }).revokeOwnerFact("Korrektur: Ich wohne in Enns.")
        val audit = signed.correctionAudit()
        assertEquals(CorrectionAuditStatus.UNSIGNED_LEDGER_CHANGE, audit.status)
        assertEquals(1, audit.unsignedRecordIds.size)
    }

    @Test fun heldHypotheticalsAreNeverSignedAsFacts() = withDir { dir ->
        val clock = longArrayOf(1_000L)
        val core = ContinuumLearningCore(dir, signer) { clock[0] }
        core.confirmOwnerFact("Vielleicht ziehe ich nach Barcelona.")
        assertEquals(0, SignedOwnerCorrectionJournal(File(dir, "learning-core-v2"), signer).entries().size)
        val record = SignedOwnerCorrectionJournal.toMemoryRecord(
            ContinuumLearningCore(dir) { 1L }.unconfirmedOwnerStatements().first())
        assertEquals(64, memoryRecordHash(record).length)
    }
}

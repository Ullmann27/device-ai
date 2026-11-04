package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** M2.1: the ledger view is cached by the ledger's CONTENT hash, never by mtime alone, across core instances. */
class ContinuumTemporalMemoryCacheTest {
    private fun withDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("continuum-m2-cache").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }
    private val now = 2_000_000_000_000L

    @Test fun anUnchangedLedgerIsReusedAcrossCoreInstances() = withDir { dir ->
        ContinuumLearningCore(dir) { 1_000L }.confirmOwnerFact("Ich wohne in Wien.")
        val before = ContinuumLearningCore.ledgerViewCacheStats()
        ContinuumLearningCore(dir) { now }.temporalMemoryView()
        ContinuumLearningCore(dir) { now }.temporalMemoryView()
        ContinuumLearningCore(dir) { now }.temporalMemoryView()
        val after = ContinuumLearningCore.ledgerViewCacheStats()
        assertTrue("at least two of three views must be cache hits: $before -> $after", after.first - before.first >= 2)
    }

    @Test fun aRewriteWithTheSameMtimeAndLengthIsStillDetected() = withDir { dir ->
        withDir { other ->
            ContinuumLearningCore(dir) { 1_000L }.confirmOwnerFact("Ich wohne in Wien.")
            ContinuumLearningCore(other) { 1_000L }.confirmOwnerFact("Ich wohne in Graz.")
            val reader = ContinuumLearningCore(dir) { now }
            assertTrue(reader.temporalMemoryView().answer("Wo wohne ich?", now)!!.text.contains("Wien"))
            val ledger = File(dir, "learning-core-v2/experiences-v2.bin")
            val foreign = File(other, "learning-core-v2/experiences-v2.bin")
            val mtime = ledger.lastModified()
            assertEquals("fixture needs equal lengths", ledger.length(), foreign.length())
            foreign.copyTo(ledger, overwrite = true)
            ledger.setLastModified(mtime)
            assertEquals(mtime, ledger.lastModified())
            val text = reader.temporalMemoryView().answer("Wo wohne ich?", now)!!.text
            assertTrue("content hash, not mtime/length, must key the cache: $text", text.contains("Graz"))
        }
    }

    @Test fun aStatusFlipBetweenViewsIsSeenByAnotherInstance() = withDir { dir ->
        val writer = ContinuumLearningCore(dir) { 1_000L }
        writer.confirmOwnerFact("Ich wohne in Wien.")
        val reader = ContinuumLearningCore(dir) { now }
        assertTrue(reader.temporalMemoryView().answer("Wo wohne ich?", now)!!.text.contains("Wien"))
        writer.revokeOwnerFact("Ich wohne in Wien.")
        assertEquals(TemporalAnswerKind.NO_EVIDENCE, reader.temporalMemoryView().answer("Wo wohne ich?", now)!!.kind)
    }

    @Test fun workingMemoryNeverLeaksIntoTheCachedLedgerView() = withDir { dir ->
        val core = ContinuumLearningCore(dir) { 1_000L }
        core.confirmOwnerFact("Ich wohne in Wien.")
        val turns = listOf(WorkingTurn("Ich wohne jetzt in Bregenz.", now - 1))
        assertTrue(core.temporalMemoryView(turns).answer("Wo wohne ich?", now)!!.text.contains("Bregenz"))
        assertTrue(core.temporalMemoryView().answer("Wo wohne ich?", now)!!.text.contains("Wien"))
    }

    @Test fun aRevocationRacingTheTurnStillFailsClosedWithTheCache() = withDir { dir ->
        var clock = 1_000L
        val core = ContinuumLearningCore(dir) { clock }
        core.confirmOwnerFact("Ich wohne in Wien.")
        clock = now
        ContinuumBrainSettings(dir).set(ContinuumBrainMode.ON)
        val baseline = ContinuumChatOutcome("b", "e", "t", false, ContinuumBaselineSource.NONE)
        // warm the cache first, so the race is tested on the cached path
        ContinuumTemporalMemorySpecialist.decide(dir, core, "Wo wohne ich?", baseline, nowMillis = now)
        ContinuumTemporalMemorySpecialist.beforeRevocationCheck = { ContinuumLearningCore(dir) { now }.revokeAllOwnerFacts() }
        try {
            val r = ContinuumTemporalMemorySpecialist.decide(dir, core, "Wo wohne ich?", baseline, nowMillis = now)
            assertEquals(ContinuumBrainStatus.FALLBACK_REVOKED, r.status)
            assertFalse(r.outcome.answer.contains("Wien"))
        } finally { ContinuumTemporalMemorySpecialist.beforeRevocationCheck = null }
    }
}

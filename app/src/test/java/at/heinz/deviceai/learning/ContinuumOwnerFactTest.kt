package at.heinz.deviceai.learning

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ContinuumOwnerFactTest {
    private fun withCore(block: (ContinuumLearningCore, File) -> Unit) {
        val dir = Files.createTempDirectory("continuum-owner-facts").toFile()
        var now = 1_000L
        try { block(ContinuumLearningCore(dir) { now++ }, dir) } finally { dir.deleteRecursively() }
    }

    private fun ledger(dir: File) = File(dir, "learning-core-v2/experiences-v2.bin")

    @Test fun rememberingARevokedFactAgainMakesItUsableAgain() = withCore { core, _ ->
        val fact = "Sternenreise ist mein privates Forschungsprojekt"
        assertEquals(OwnerFactChange.ADDED, core.confirmOwnerFact(fact))
        assertEquals(OwnerFactChange.ALREADY_ACTIVE, core.confirmOwnerFact(fact))
        assertEquals(1, core.revokeOwnerFact(fact))
        assertFalse(core.replyFromMemory("Erzähl mir von Sternenreise").grounded)
        // before the fix the content-hash id stayed REVOKED while the app said "Gemerkt"
        assertFalse("plain append keeps a revocation", core.recordOwnerFact(fact))
        assertEquals(OwnerFactChange.REACTIVATED, core.confirmOwnerFact(fact))
        val reply = core.replyFromMemory("Erzähl mir von Sternenreise")
        assertTrue(reply.grounded)
        assertTrue(reply.answer.contains("Sternenreise"))
        val stats = core.status().experiences
        assertEquals(1, stats.userFacts)
        assertEquals(0, stats.revoked)
    }

    @Test fun syncAddsMissingFactsInOneWriteAndNeverUndoesARevocation() = withCore { core, dir ->
        val kept = "Meine bevorzugte Sprache ist Deutsch."
        val revoked = "Mein Hund heißt Bello."
        core.confirmOwnerFact(revoked)
        core.revokeOwnerFact(revoked)
        val before = ledger(dir).readBytes()
        assertEquals(1, core.syncOwnerFacts(listOf(kept, revoked, kept)))
        val afterFirst = ledger(dir).readBytes()
        assertFalse(before.contentEquals(afterFirst))
        assertEquals("nothing new: the file is not rewritten", 0, core.syncOwnerFacts(listOf(revoked, kept)))
        assertArrayEquals(afterFirst, ledger(dir).readBytes())
        val stats = core.status().experiences
        assertEquals(2, stats.userFacts)
        assertEquals("the revoked fact stays revoked", 1, stats.revoked)
        assertFalse(core.replyFromMemory("Wie heißt mein Hund Bello?").answer.contains("Bello."))
    }

    @Test fun syncOfAnEmptyOwnerMemoryChangesNothing() = withCore { core, dir ->
        core.confirmOwnerFact("Mein Projekt heißt Astrolux.")
        val before = ledger(dir).readBytes()
        assertEquals(0, core.syncOwnerFacts(emptyList()))
        assertArrayEquals(before, ledger(dir).readBytes())
        assertEquals(0, core.status().experiences.revoked)
    }
}

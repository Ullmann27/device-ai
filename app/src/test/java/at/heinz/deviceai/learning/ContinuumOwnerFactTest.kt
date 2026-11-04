package at.heinz.deviceai.learning

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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

    @Test fun activeOwnerFactsExcludeRevokedOnes() = withCore { core, _ ->
        core.confirmOwnerFact("Mein Projekt heißt Astrolux.")
        core.confirmOwnerFact("Mein Hund heißt Bello.")
        core.revokeOwnerFact("Mein Hund heißt Bello.")
        assertEquals(listOf("Mein Projekt heißt Astrolux."), core.activeOwnerFacts().map { it.content })
        core.confirmOwnerFact("Mein Hund heißt Bello.")
        assertEquals(2, core.activeOwnerFacts().size)
    }

    @Test fun syncOfAnEmptyOwnerMemoryChangesNothing() = withCore { core, dir ->
        core.confirmOwnerFact("Mein Projekt heißt Astrolux.")
        val before = ledger(dir).readBytes()
        assertEquals(0, core.syncOwnerFacts(emptyList()))
        assertArrayEquals(before, ledger(dir).readBytes())
        assertEquals(0, core.status().experiences.revoked)
    }

    @Test fun sourceAndAuditChurnPreservesOwnerFactsAndRevocationsAcrossRestart() = withCore { core, dir ->
        val kept="Mein Projekt heißt Astrolux."
        val revoked="Mein Hund heißt Bello."
        core.confirmOwnerFact(kept)
        core.confirmOwnerFact(revoked)
        core.revokeOwnerFact(revoked)
        val store=FileLearningExperienceStore(File(dir,"learning-core-v2"))
        store.appendMissing((0..FileLearningExperienceStore.MAX_RECORDS+44).map { i ->
            LearningExperienceFactory.create(
                if(i%2==0) LearningExperienceType.TRAINING_AUDIT else LearningExperienceType.SOURCE_TEXT,
                LearningProvenance.LOCAL_TRAINING,LearningTrustClass.SYSTEM_MEASUREMENT,
                LearningStatus.OBSERVED,"Bounded observation $i",now=10_000L+i)
        })
        val reopened=ContinuumLearningCore(dir)
        assertEquals("sync must still see the revocation tombstone",0,
            reopened.syncOwnerFacts(listOf(kept,revoked)))
        assertEquals(listOf(kept),reopened.activeOwnerFacts().map { it.content })
        assertEquals(1,reopened.status().experiences.revoked)
        assertEquals(FileLearningExperienceStore.MAX_RECORDS,reopened.status().experiences.total)
    }

    @Test fun corruptPrimaryCannotResurrectARevokedFactFromAnOlderBackup() = withCore { core, dir ->
        val fact="Mein Hund heißt Bello."
        core.confirmOwnerFact(fact)
        core.revokeOwnerFact(fact)
        val backup=File(dir,"learning-core-v2/experiences-v2.lkg")
        val beforeBackup=backup.readBytes()
        val damaged=ledger(dir).readBytes().also { it[40]=(it[40].toInt() xor 1).toByte() }
        ledger(dir).writeBytes(damaged)
        val reopened=ContinuumLearningCore(dir)
        assertThrows(IllegalArgumentException::class.java) { reopened.activeOwnerFacts() }
        assertThrows(IllegalArgumentException::class.java) { reopened.replyFromMemory("Wie heißt mein Hund?") }
        assertArrayEquals("retain corrupt evidence for explicit repair",damaged,ledger(dir).readBytes())
        assertArrayEquals("an older backup is diagnostic, not current consent",beforeBackup,backup.readBytes())
    }

    @Test fun missingPrimaryWithAnOlderBackupCannotStartAnEmptyLedger() = withCore { core, dir ->
        val fact="Mein Hund heißt Bello."
        core.confirmOwnerFact(fact)
        core.revokeOwnerFact(fact)
        assertTrue(File(dir,"learning-core-v2/experiences-v2.lkg").isFile)
        assertTrue(ledger(dir).delete())
        val reopened=ContinuumLearningCore(dir)
        assertThrows(IllegalStateException::class.java) { reopened.syncOwnerFacts(listOf(fact)) }
        assertThrows(IllegalStateException::class.java) { reopened.activeOwnerFacts() }
        assertFalse(ledger(dir).exists())
    }
}

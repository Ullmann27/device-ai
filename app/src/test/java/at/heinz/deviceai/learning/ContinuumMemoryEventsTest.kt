package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

/** M2.11: Jarvis-Kern event contract (listener, IDs/hashes only, async dispatch, isolated failures). */
class ContinuumMemoryEventsTest {
    private fun withDir(block:(File, CopyOnWriteArrayList<ContinuumMemoryEvent>, AutoCloseable)->Unit) {
        val dir=Files.createTempDirectory("m2-events").toFile()
        val got=CopyOnWriteArrayList<ContinuumMemoryEvent>()
        val handle=ContinuumMemoryEvents.forApp(dir).addListener { got+=it }
        try { block(dir,got,handle) } finally { handle.close();dir.deleteRecursively() }
    }
    private fun drain(dir:File)=ContinuumMemoryEventHub.awaitIdleForTest(dir)
    private val secrets=listOf("Lotto","Steyr","Zahnarzt","Fotosynthese","Pflanzen","Haus")
    private fun assertNoPlaintext(events:List<ContinuumMemoryEvent>) {
        val dump=events.joinToString("\n") { it.toString() }
        secrets.forEach { assertFalse("plaintext '$it' leaked: $dump",dump.contains(it,ignoreCase=true)) }
    }

    @Test fun forAppIsOneInstancePerCanonicalPath() = withDir { dir,_,_ ->
        assertTrue(ContinuumMemoryEvents.forApp(dir)===ContinuumMemoryEvents.forApp(File(dir,"../"+dir.name)))
    }

    @Test fun storingEmitsMemoryStoredWithHypotheticalForLotto() = withDir { dir,got,_ ->
        val core=ContinuumLearningCore(dir)
        core.confirmOwnerFact("Ich wohne in Steyr.")
        core.confirmOwnerFact("Merke dir: Wenn ich im Lotto gewinne, kaufe ich ein Haus.")
        drain(dir)
        val stored=got.filterIsInstance<MemoryStored>()
        val ledger=FileLearningExperienceStore(File(dir,"learning-core-v2")).all().associateBy { it.id }
        val lotto=ledger.values.first { "Lotto" in it.content }
        val steyr=ledger.values.first { "Steyr" in it.content }
        assertTrue(stored.any { it.entryId==steyr.id && it.status==MemoryEntryStatus.CONFIRMED })
        assertTrue(stored.any { it.entryId==lotto.id && it.status==MemoryEntryStatus.HYPOTHETICAL })
        assertTrue(stored.none { it.entryId==lotto.id && it.status==MemoryEntryStatus.CONFIRMED })
        assertTrue(stored.all { it.entryId in ledger })
        assertNoPlaintext(got)
    }

    @Test fun documentImportEmitsDocumentLearned() = withDir { dir,got,_ ->
        val rec=ContinuumKnowledgeArchive(dir).save(ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT,"Fotosynthese",
            "https://de.wikipedia.org/wiki/Fotosynthese","Wikipedia, CC BY-SA 4.0",
            "Pflanzen wandeln Lichtenergie in chemische Energie um, das nennt man Fotosynthese.")
        drain(dir)
        val doc=got.filterIsInstance<DocumentLearned>().single()
        assertEquals(rec.id,doc.docId); assertEquals("WIKIPEDIA_EXCERPT",doc.kind); assertEquals(1,doc.entriesAdded)
        assertNoPlaintext(got)
    }

    @Test fun searchEmitsRecallHitsWithSaltedHash() = withDir { dir,got,_ ->
        val r=ContinuumMemoryRetriever.default(appDirectory=dir)
        r.index(listOf(MemoryDoc("f1","Mein Zahnarzttermin ist am Donnerstag.",1000,true),MemoryDoc("f2","Ich wohne in Steyr.",1000,true)))
        r.search("Wann ist der Zahnarzt?",5000,3)
        drain(dir)
        val e=got.filterIsInstance<RecallHits>().single()
        assertEquals("f1",e.hits.first().entryId); assertEquals(1,e.hits.first().rank); assertEquals(5000L,e.atEpochMs)
        assertTrue(e.queryHash.matches(Regex("[0-9a-f]{64}")))
        val unsalted=java.security.MessageDigest.getInstance("SHA-256").digest("Wann ist der Zahnarzt?".toByteArray()).joinToString("") { "%02x".format(it) }
        assertTrue(e.queryHash!=unsalted)
        assertNoPlaintext(got)
    }

    @Test fun noEventsAfterClose() = withDir { dir,got,handle ->
        handle.close()
        ContinuumLearningCore(dir).confirmOwnerFact("Ich wohne in Steyr.")
        drain(dir); assertTrue(got.isEmpty())
    }

    @Test fun throwingListenerDoesNotDisturbLedger() = withDir { dir,got,_ ->
        val bad=ContinuumMemoryEvents.forApp(dir).addListener { throw IllegalStateException("boom") }
        try {
            val core=ContinuumLearningCore(dir)
            core.confirmOwnerFact("Ich wohne in Steyr.")
            drain(dir)
            assertEquals(listOf("Ich wohne in Steyr."),core.activeOwnerFacts().map { it.content })
            assertTrue(got.filterIsInstance<MemoryStored>().isNotEmpty())
        } finally { bad.close() }
    }

    @Test fun dispatchNeverRunsOnCallerThread() = withDir { dir,_,_ ->
        val caller=Thread.currentThread(); var seen:Thread?=null
        val h=ContinuumMemoryEvents.forApp(dir).addListener { seen=Thread.currentThread() }
        try { ContinuumLearningCore(dir).confirmOwnerFact("Ich wohne in Steyr."); drain(dir)
            assertTrue(seen!=null && seen!==caller) } finally { h.close() }
    }
}

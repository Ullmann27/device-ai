package at.heinz.deviceai.cockpit

import at.heinz.deviceai.learning.ContinuumLearningCore
import at.heinz.deviceai.learning.ContinuumMemoryEventHub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Collections

/** Exercises the real app-scoped hub and ledger producers through the production adapter. */
class RealMemoryEventSourceTest {
    @get:Rule val folder = TemporaryFolder()

    private class Recorder : MemoryEventSink {
        val statuses = Collections.synchronizedList(mutableListOf<String>())
        val ids = Collections.synchronizedList(mutableListOf<String>())
        override fun documentLearned(docId: String, kind: String, entriesAdded: Int, atEpochMs: Long) {}
        override fun memoryStored(entryId: String, status: String, atEpochMs: Long) {
            ids += entryId
            statuses += status
        }
        override fun recallHits(queryHash: String, count: Int, topScore: Float?, atEpochMs: Long) {}
    }

    @Test fun subscribingDoesNotInventActivityAndActualConfirmationArrives() {
        val sink = Recorder()
        val subscription = MemoryEventSources.forApp(folder.root).subscribe(sink)
        try {
            ContinuumMemoryEventHub.awaitIdleForTest(folder.root)
            assertTrue(sink.statuses.isEmpty())
            ContinuumLearningCore(folder.root).confirmOwnerFact("Ich trinke gern grünen Tee.")
            ContinuumMemoryEventHub.awaitIdleForTest(folder.root)
            assertEquals(listOf("CONFIRMED"), sink.statuses.toList())
            assertTrue(sink.ids.single().startsWith("lx-"))
            assertTrue(sink.ids.none { it.contains("Tee") })
        } finally { subscription.close() }
    }

    @Test fun anActualRevocationIsNotAConfirmedEvent() {
        val sink = Recorder()
        val subscription = MemoryEventSources.forApp(folder.root).subscribe(sink)
        try {
            val core = ContinuumLearningCore(folder.root)
            core.confirmOwnerFact("Ich wohne in Teststadt.")
            core.revokeOwnerFact("Ich wohne in Teststadt.")
            ContinuumMemoryEventHub.awaitIdleForTest(folder.root)
            assertEquals(listOf("CONFIRMED", "REVOKED"), sink.statuses.toList())
        } finally { subscription.close() }
    }

    @Test fun closingTheSourceStopsEventsAndAppDirectoriesRemainIsolated() {
        val sink = Recorder()
        val subscription = MemoryEventSources.forApp(folder.root).subscribe(sink)
        ContinuumLearningCore(folder.newFolder("other-app")).confirmOwnerFact("Ich mag Musik.")
        ContinuumMemoryEventHub.awaitIdleForTest(folder.root)
        assertTrue(sink.statuses.isEmpty())
        subscription.close()
        ContinuumLearningCore(folder.root).confirmOwnerFact("Ich mag Bücher.")
        ContinuumMemoryEventHub.awaitIdleForTest(folder.root)
        assertTrue(sink.statuses.isEmpty())
    }
}

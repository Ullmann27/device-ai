package at.heinz.deviceai.cockpit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Test-only stand-in of Jarvis Gedächtnis' agreed signature (NOT production code; their side implements it).
private sealed interface ContinuumMemoryEvent { val atEpochMs: Long }
private enum class MemoryEntryStatus { CONFIRMED, UNCONFIRMED, HYPOTHETICAL, REVOKED }
private data class RecallHit(val entryId: String, val score: Float, val rank: Int)
private data class DocumentLearned(val docId: String, val kind: String, val entriesAdded: Int, override val atEpochMs: Long) : ContinuumMemoryEvent
private data class MemoryStored(val entryId: String, val status: MemoryEntryStatus, override val atEpochMs: Long) : ContinuumMemoryEvent
private data class RecallHits(val queryHash: String, val hits: List<RecallHit>, override val atEpochMs: Long) : ContinuumMemoryEvent
private fun interface ContinuumMemoryEventListener { fun onMemoryEvent(event: ContinuumMemoryEvent) }
private interface ContinuumMemoryEvents { fun addListener(listener: ContinuumMemoryEventListener): AutoCloseable }

private class FakeMemoryEvents : ContinuumMemoryEvents {
    val listeners = mutableListOf<ContinuumMemoryEventListener>()
    override fun addListener(listener: ContinuumMemoryEventListener): AutoCloseable {
        listeners += listener; return AutoCloseable { listeners -= listener }
    }
    fun fire(e: ContinuumMemoryEvent) = listeners.toList().forEach { it.onMemoryEvent(e) }
}

/** The bridge shape the production adapter will get once ContinuumMemoryEvents exists. */
private fun FakeMemoryEvents.asSource() = MemoryEventSource { sink ->
    addListener { e ->
        when (e) {
            is DocumentLearned -> sink.documentLearned(e.docId, e.kind, e.entriesAdded, e.atEpochMs)
            is MemoryStored -> sink.memoryStored(e.entryId, e.status.name, e.atEpochMs)
            is RecallHits -> sink.recallHits(e.queryHash, e.hits.size, e.hits.minByOrNull { it.rank }?.score, e.atEpochMs)
        }
    }
}

class MemoryEventSourceTest {
    @Test fun fakeSourceEventsArePostedThenMappedToCockpitEvents() {
        val bus = CockpitAnimationBus()
        val seen = mutableListOf<CockpitAnimationEvent>()
        bus.subscribe { seen += it }
        val posted = mutableListOf<Runnable>()
        val fake = FakeMemoryEvents()
        val sub = fake.asSource().subscribe(MemoryEventMapper(bus) { posted += it })

        fake.fire(DocumentLearned("doc-1", "pdf", 4, 10L))
        fake.fire(MemoryStored("e-1", MemoryEntryStatus.UNCONFIRMED, 11L))
        fake.fire(RecallHits("q", listOf(RecallHit("e-2", .4f, 2), RecallHit("e-1", .9f, 1)), 12L))
        fake.fire(RecallHits("q2", emptyList(), 13L))
        assertTrue("nothing reaches renderers before the main-thread post", seen.isEmpty())
        posted.forEach { it.run() }

        assertEquals(CockpitAnimationEvent.DocumentLearned(MEMORY_TURN, 10L, "doc-1", "pdf", 4), seen[0])
        assertEquals(CockpitAnimationEvent.MemoryStored(MEMORY_TURN, 11L, "e-1", "UNCONFIRMED"), seen[1])
        assertEquals(CockpitAnimationEvent.RecallHits(MEMORY_TURN, 12L, "q", 2, .9f), seen[2])
        assertEquals(0, (seen[3] as CockpitAnimationEvent.RecallHits).count)
        assertNull((seen[3] as CockpitAnimationEvent.RecallHits).topScore)
        sub.close()
        assertTrue(fake.listeners.isEmpty())
    }

    @Test fun productionSourceIsHonestlyMissing() {
        assertNull(MemoryEventSources.production)
        assertEquals("Quelle fehlt bis ContinuumMemoryEvents (#371-Linie)", MemoryEventSources.MISSING)
    }
}

package at.heinz.deviceai.cockpit

/**
 * Kern-side adapter for memory events (owner of the interface: Jarvis Kern). Deliberately primitive so the
 * cockpit does not depend on Jarvis Gedächtnis' types before they exist. The agreed source signature is
 * `ContinuumMemoryEvents.forApp(appDirectory).addListener(ContinuumMemoryEventListener): AutoCloseable`
 * (docs/ARCHITEKTUR-JARVIS.md); its bridge only calls the [MemoryEventSink] methods below.
 */
interface MemoryEventSink {
    fun documentLearned(docId: String, kind: String, entriesAdded: Int, atEpochMs: Long)
    fun memoryStored(entryId: String, status: String, atEpochMs: Long)
    fun recallHits(queryHash: String, count: Int, topScore: Float?, atEpochMs: Long)
}

fun interface MemoryEventSource {
    fun subscribe(sink: MemoryEventSink): AutoCloseable
}

object MemoryEventSources {
    const val MISSING = "Quelle fehlt bis ContinuumMemoryEvents (#371-Linie)"
    /** No production source yet; the Activity shows [MISSING] and subscribes nothing. */
    val production: MemoryEventSource? = null
}

/**
 * Maps sink calls to [CockpitAnimationEvent]s. The memory listener may run off the main thread, so every
 * emission goes through [post] (on Android: the main-thread Handler) before renderers touch views.
 */
class MemoryEventMapper(private val bus: CockpitAnimationBus, private val post: (Runnable) -> Unit) : MemoryEventSink {
    override fun documentLearned(docId: String, kind: String, entriesAdded: Int, atEpochMs: Long) {
        if (docId.isBlank()) return
        emit(CockpitAnimationEvent.DocumentLearned(MEMORY_TURN, atEpochMs, docId, kind, entriesAdded.coerceAtLeast(0)))
    }

    override fun memoryStored(entryId: String, status: String, atEpochMs: Long) {
        if (entryId.isBlank()) return
        emit(CockpitAnimationEvent.MemoryStored(MEMORY_TURN, atEpochMs, entryId, status))
    }

    override fun recallHits(queryHash: String, count: Int, topScore: Float?, atEpochMs: Long) {
        emit(CockpitAnimationEvent.RecallHits(MEMORY_TURN, atEpochMs, queryHash, count.coerceAtLeast(0),
            topScore.takeIf { count > 0 }))
    }

    private fun emit(event: CockpitAnimationEvent) = post(Runnable { bus.emit(event) })
}

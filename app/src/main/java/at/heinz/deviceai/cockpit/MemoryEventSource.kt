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
    /**
     * Legacy unscoped lookup remains absent: the application directory must be supplied explicitly.
     * Use [forApp] after the compatible segmented-ledger recovery has passed its safety gate.
     */
    @Deprecated("Use forApp(appDirectory) to bind the existing per-application event hub")
    val production: MemoryEventSource? = null

    /** IDs, states and measured scores from the real ledger/retriever. Subscribing invents no initial activity. */
    fun forApp(appDirectory: java.io.File): MemoryEventSource = MemoryEventSource { sink ->
        at.heinz.deviceai.learning.ContinuumMemoryEvents.forApp(appDirectory).addListener { event ->
            when (event) {
                is at.heinz.deviceai.learning.DocumentLearned ->
                    sink.documentLearned(event.docId, event.kind, event.entriesAdded, event.atEpochMs)
                is at.heinz.deviceai.learning.MemoryStored ->
                    sink.memoryStored(event.entryId, event.status.name, event.atEpochMs)
                is at.heinz.deviceai.learning.RecallHits ->
                    sink.recallHits(event.queryHash, event.hits.size,
                        event.hits.minByOrNull { it.rank }?.score, event.atEpochMs)
            }
        }
    }
}

/**
 * Maps sink calls to [CockpitAnimationEvent]s. The memory hub delivers on its own background thread with an
 * bounded queue, so the callbacks stay trivial: they only swap atomic "latest" values / counters and schedule at
 * most one main-thread flush per [minIntervalMs]. All bus work happens in the flush on the main thread
 * ([postDelayed] = Handler(mainLooper)::postDelayed on Android).
 */
class MemoryEventMapper(
    private val bus: CockpitAnimationBus,
    private val minIntervalMs: Long = 100L,
    private val postDelayed: (Long, Runnable) -> Unit,
) : MemoryEventSink {
    private val document = java.util.concurrent.atomic.AtomicReference<CockpitAnimationEvent.DocumentLearned?>()
    private val stored = java.util.concurrent.atomic.AtomicReference<CockpitAnimationEvent.MemoryStored?>()
    private val storedCount = java.util.concurrent.atomic.AtomicInteger()
    private val recall = java.util.concurrent.atomic.AtomicReference<CockpitAnimationEvent.RecallHits?>()
    private val scheduled = java.util.concurrent.atomic.AtomicBoolean()
    private val detached = java.util.concurrent.atomic.AtomicBoolean()

    /** After detach, a straggler event from the hub (one may arrive after close()) is ignored. */
    fun detach() = detached.set(true)

    override fun documentLearned(docId: String, kind: String, entriesAdded: Int, atEpochMs: Long) {
        if (docId.isBlank()) return
        document.set(CockpitAnimationEvent.DocumentLearned(MEMORY_TURN, atEpochMs, docId, kind, entriesAdded.coerceAtLeast(0)))
        schedule()
    }

    override fun memoryStored(entryId: String, status: String, atEpochMs: Long) {
        if (entryId.isBlank()) return
        stored.set(CockpitAnimationEvent.MemoryStored(MEMORY_TURN, atEpochMs, entryId, status))
        storedCount.incrementAndGet()
        schedule()
    }

    override fun recallHits(queryHash: String, count: Int, topScore: Float?, atEpochMs: Long) {
        recall.set(CockpitAnimationEvent.RecallHits(MEMORY_TURN, atEpochMs, queryHash, count.coerceAtLeast(0),
            topScore.takeIf { count > 0 }))
        schedule()
    }

    private fun schedule() {
        if (detached.get()) return
        if (scheduled.compareAndSet(false, true)) postDelayed(minIntervalMs, Runnable { flush() })
    }

    /** Main thread: emits the coalesced latest state (document, writes with count, recall) in that order. */
    private fun flush() {
        scheduled.set(false)
        if (detached.get()) return
        document.getAndSet(null)?.let(bus::emit)
        val writes = storedCount.getAndSet(0)
        stored.getAndSet(null)?.let { bus.emit(it.copy(count = writes.coerceAtLeast(1))) }
        recall.getAndSet(null)?.let(bus::emit)
    }
}

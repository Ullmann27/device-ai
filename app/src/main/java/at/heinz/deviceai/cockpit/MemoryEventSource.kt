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
    fun forApp(appDirectory: java.io.File): MemoryEventSource =
        ContinuumMemoryEventSource.forApp(appDirectory)
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
    // The latest write and its coalesced count must be replaced/consumed together. Separate
    // atomics let a producer place the next write between the flush's two reads and lose a count.
    private val stored = java.util.concurrent.atomic.AtomicReference<CockpitAnimationEvent.MemoryStored?>()
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
        while (true) {
            val previous = stored.get()
            val count = previous?.count ?: 0
            val next = CockpitAnimationEvent.MemoryStored(MEMORY_TURN, atEpochMs, entryId, status,
                count = if (count == Int.MAX_VALUE) count else count + 1)
            if (stored.compareAndSet(previous, next)) break
        }
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
        emitIfAttached(document.getAndSet(null))
        emitIfAttached(stored.getAndSet(null))
        emitIfAttached(recall.getAndSet(null))
    }

    private fun emitIfAttached(event: CockpitAnimationEvent?) {
        // A renderer can synchronously detach this mapper while handling the preceding kind.
        // Never hold a mapper lock across bus callbacks: cleanup may wait for a hub producer.
        if (event != null && !detached.get()) bus.emit(event)
    }
}

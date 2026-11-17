package at.heinz.deviceai.cockpit

import at.heinz.deviceai.learning.CharNgramHashRetriever
import at.heinz.deviceai.learning.ContinuumMemoryRetriever
import at.heinz.deviceai.learning.RetrievalHit
import java.util.logging.Level
import java.util.logging.Logger

/**
 * M2.14: [CalculationEventSource] fed ONLY by real memory-retrieval score computations (see
 * [CalculationEmittingRetriever]). Steps/result are the exact values of that computation; labels are fixed German
 * terms, never memory text. Delivery: bounded drop-oldest queue (256) on a named daemon thread that exists only while
 * listeners are registered (same pattern as ContinuumMemoryEventHub); [emit] never blocks.
 * Contract: when close() removes the last listener, the queue is dropped, the worker is detached and close() waits
 * (at most [CLOSE_JOIN_MS], never from the worker itself) until that thread has ended; afterwards no dispatch thread exists.
 */
class RetrievalCalculationSource internal constructor(private val threadName: String) : CalculationEventSource {
    constructor() : this("continuum-calc-events-" + Integer.toHexString(System.identityHashCode(Any())).padStart(8, '0').takeLast(8))

    private val lock = Object()
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(CockpitAnimationEvent.CalculationShown) -> Unit>()
    private val queue = ArrayDeque<CockpitAnimationEvent.CalculationShown>()
    private var worker: Thread? = null
    private var inFlight = false
    @Volatile internal var queueCapacity: Int = DEFAULT_QUEUE_CAPACITY
    @Volatile internal var droppedEvents: Long = 0L
        private set

    override fun addListener(l: (CockpitAnimationEvent.CalculationShown) -> Unit): AutoCloseable {
        synchronized(lock) { listeners += l; if (worker == null) startWorker() }
        return AutoCloseable {
            val ending: Thread?
            synchronized(lock) {
                if (!(listeners.remove(l) && listeners.isEmpty())) return@AutoCloseable
                queue.clear(); ending = worker; worker = null; lock.notifyAll()
            }
            if (ending != null && ending !== Thread.currentThread()) ending.join(CLOSE_JOIN_MS)
        }
    }

    fun hasListeners() = listeners.isNotEmpty()

    internal fun emit(event: CockpitAnimationEvent.CalculationShown) {
        if (listeners.isEmpty()) return
        synchronized(lock) {
            if (listeners.isEmpty()) return
            while (queue.size >= queueCapacity.coerceAtLeast(1)) { queue.removeFirst(); droppedEvents++ }
            queue.addLast(event)
            if (worker == null) startWorker()
            lock.notifyAll()
        }
    }

    private fun startWorker() {
        worker = Thread({ run() }, threadName).apply { isDaemon = true; start() }
    }

    private fun run() {
        while (true) {
            val e: CockpitAnimationEvent.CalculationShown
            synchronized(lock) {
                while (queue.isEmpty() && worker === Thread.currentThread()) lock.wait()
                if (worker !== Thread.currentThread()) { lock.notifyAll(); return } // detached by the last close()
                e = queue.removeFirst(); inFlight = true
            }
            try {
                for (l in listeners) try { l(e) } catch (t: Throwable) { LOG.log(Level.WARNING, "calculation listener failed: ${t.javaClass.simpleName}") }
            } finally { synchronized(lock) { inFlight = false; lock.notifyAll() } }
        }
    }

    internal fun queuedForTest(): Int = synchronized(lock) { queue.size }
    internal fun dispatchThreadForTest(): Thread? = synchronized(lock) { worker }
    internal fun awaitIdleForTest(timeoutMs: Long = 5_000) {
        val end = System.currentTimeMillis() + timeoutMs
        synchronized(lock) {
            while ((queue.isNotEmpty() || inFlight) && worker != null) {
                val left = end - System.currentTimeMillis(); check(left > 0) { "calculation queue not idle" }; lock.wait(left)
            }
        }
    }

    companion object {
        const val DEFAULT_QUEUE_CAPACITY = 256
        const val CLOSE_JOIN_MS = 1_000L
        const val EXPRESSION = "score = cos(q, d) × w_zeit"
        private val LOG = Logger.getLogger("ContinuumCalculationEvents")
    }
}

/**
 * Retriever decorator: after each real search with at least one hit, emits one [CockpitAnimationEvent.CalculationShown]
 * for the rank-1 score: steps Kosinus (cosine of char-n-gram TF-IDF vectors), Zeitgewicht (1.0 inside a cued time
 * window, otherwise 0.35; 1.0 without cue), Treffer (number of hits); result = Kosinus × Zeitgewicht = returned score.
 * No search, or a search without hits, emits nothing. Computation only when a listener exists (no extra cost otherwise).
 */
class CalculationEmittingRetriever(private val delegate: CharNgramHashRetriever, private val source: RetrievalCalculationSource) :
    ContinuumMemoryRetriever by delegate {
    override fun search(query: String, nowMillis: Long, k: Int): List<RetrievalHit> {
        if (!source.hasListeners()) return delegate.search(query, nowMillis, k)
        val parts = delegate.scoredParts(query, nowMillis, k)
        parts.firstOrNull()?.let { top ->
            source.emit(CockpitAnimationEvent.CalculationShown(MEMORY_TURN, nowMillis, RetrievalCalculationSource.EXPRESSION,
                listOf(CalcStep("Kosinus", top.cosine), CalcStep("Zeitgewicht", top.timeWeight), CalcStep("Treffer", parts.size.toDouble())),
                top.hit.score, null, ChartKind.DONUT))
        }
        return parts.map { it.hit }
    }
}

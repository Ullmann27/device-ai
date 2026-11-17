package at.heinz.deviceai.learning

import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Level
import java.util.logging.Logger

// ---- Contract fixed by Jarvis Kern (M2.11). Do not change signatures. ----
sealed interface ContinuumMemoryEvent { val atEpochMs: Long }
data class DocumentLearned(val docId: String, val kind: String, val entriesAdded: Int, override val atEpochMs: Long) : ContinuumMemoryEvent
data class MemoryStored(val entryId: String, val status: MemoryEntryStatus, override val atEpochMs: Long) : ContinuumMemoryEvent
data class RecallHits(val queryHash: String, val hits: List<RecallHit>, override val atEpochMs: Long) : ContinuumMemoryEvent
data class RecallHit(val entryId: String, val score: Float, val rank: Int)
enum class MemoryEntryStatus { CONFIRMED, UNCONFIRMED, HYPOTHETICAL, REVOKED }
fun interface ContinuumMemoryEventListener { fun onMemoryEvent(event: ContinuumMemoryEvent) }
interface ContinuumMemoryEvents {
    fun addListener(listener: ContinuumMemoryEventListener): AutoCloseable
    companion object {
        /** One instance per app directory (canonical path is the key). */
        fun forApp(appDirectory: File): ContinuumMemoryEvents = ContinuumMemoryEventHub.forApp(appDirectory)
    }
}
// ---- end of contract ----

/**
 * Producer side. Events carry only IDs/hashes. Dispatch runs on one named daemon thread per app directory that exists
 * only while listeners are registered; the queue is bounded (drop-oldest, counted), so emitters never block.
 * Listener exceptions are caught and logged.
 */
internal class ContinuumMemoryEventHub private constructor(private val appDirectory: File) : ContinuumMemoryEvents {
    private val lock = Object()
    private val listeners = CopyOnWriteArrayList<ContinuumMemoryEventListener>()
    private val queue = ArrayDeque<ContinuumMemoryEvent>()
    private var worker: Thread? = null
    private var inFlight = false
    private val threadName = "continuum-memory-events-" + MessageDigest.getInstance("SHA-256")
        .digest(appDirectory.path.toByteArray(Charsets.UTF_8)).take(4).joinToString("") { "%02x".format(it) }
    private val salt: ByteArray by lazy { loadSalt() }

    /** Max queued events; overflow drops the OLDEST and counts it. Injectable for tests. */
    @Volatile internal var queueCapacity: Int = DEFAULT_QUEUE_CAPACITY
    /** Diagnostics: events dropped because the queue was full. */
    @Volatile internal var droppedEvents: Long = 0L
        private set

    override fun addListener(listener: ContinuumMemoryEventListener): AutoCloseable {
        synchronized(lock) { listeners += listener; ensureWorker() }
        // Last close(): drop queue, detach worker, join it (max CLOSE_JOIN_MS, never from the worker itself).
        return AutoCloseable {
            val ending: Thread?
            synchronized(lock) {
                if (!(listeners.remove(listener) && listeners.isEmpty())) return@AutoCloseable
                queue.clear(); ending = worker; worker = null; lock.notifyAll()
            }
            if (ending != null && ending !== Thread.currentThread()) ending.join(CLOSE_JOIN_MS)
        }
    }

    fun hasListeners() = listeners.isNotEmpty()

    /** Never blocks on listeners: O(1) enqueue under a short lock. */
    fun emit(event: ContinuumMemoryEvent) {
        if (listeners.isEmpty()) return
        synchronized(lock) {
            if (listeners.isEmpty()) return
            while (queue.size >= queueCapacity.coerceAtLeast(1)) { queue.removeFirst(); droppedEvents++ }
            queue.addLast(event)
            ensureWorker()
            lock.notifyAll()
        }
    }

    /** Caller holds [lock]. A detached worker (last close()) has [worker] != itself and exits without dispatching. */
    private fun ensureWorker() {
        if (worker != null) return
        worker = Thread({ runWorker() }, threadName).apply { isDaemon = true; start() }
    }

    private fun runWorker() {
        while (true) {
            val event: ContinuumMemoryEvent
            synchronized(lock) {
                while (queue.isEmpty() && worker === Thread.currentThread()) lock.wait()
                if (worker !== Thread.currentThread()) { lock.notifyAll(); return }
                event = queue.removeFirst(); inFlight = true
            }
            try {
                for (l in listeners) { // re-read at dispatch time: a closed listener receives nothing
                    try { l.onMemoryEvent(event) } catch (t: Throwable) {
                        LOG.log(Level.WARNING, "memory event listener failed: ${t.javaClass.simpleName}")
                    }
                }
            } finally { synchronized(lock) { inFlight = false; lock.notifyAll() } }
        }
    }

    internal fun queuedForTest(): Int = synchronized(lock) { queue.size }
    internal fun dispatchThreadForTest(): Thread? = synchronized(lock) { worker }
    internal fun awaitIdle(timeoutMs: Long) {
        val end = System.currentTimeMillis() + timeoutMs
        synchronized(lock) {
            while ((queue.isNotEmpty() || inFlight) && worker != null) {
                val left = end - System.currentTimeMillis(); check(left > 0) { "event queue not idle" }
                lock.wait(left)
            }
        }
    }

    /** SHA-256 hex of (per-install salt ‖ query). The salt never leaves the app directory. */
    fun queryHash(query: String): String = MessageDigest.getInstance("SHA-256").run {
        update(salt); update(query.toByteArray(Charsets.UTF_8)); digest()
    }.joinToString("") { "%02x".format(it) }

    private fun loadSalt(): ByteArray {
        val f = File(File(appDirectory, "learning-core-v2"), ".memory-event-salt")
        runCatching { f.readBytes().takeIf { it.size == 32 } }.getOrNull()?.let { return it }
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        runCatching { f.parentFile?.mkdirs(); f.writeBytes(fresh) }
            .onFailure { LOG.warning("memory event salt not persisted; using process-local salt") }
        return fresh
    }

    companion object {
        private val LOG = Logger.getLogger("ContinuumMemoryEvents")
        private val HUBS = ConcurrentHashMap<String, ContinuumMemoryEventHub>()
        fun forApp(appDirectory: File): ContinuumMemoryEventHub =
            HUBS.computeIfAbsent(appDirectory.canonicalPath) { ContinuumMemoryEventHub(File(it)) }

        fun statusOf(e: LearningExperience): MemoryEntryStatus = when {
            e.status == LearningStatus.REVOKED || e.status == LearningStatus.REJECTED -> MemoryEntryStatus.REVOKED
            e.annotation == OwnerStatementModality.HELD_PREFIX + StatementModality.HYPOTHETICAL.name -> MemoryEntryStatus.HYPOTHETICAL
            e.status == LearningStatus.CONFIRMED && e.trust == LearningTrustClass.OWNER_CONFIRMED -> MemoryEntryStatus.CONFIRMED
            else -> MemoryEntryStatus.UNCONFIRMED
        }

        const val DEFAULT_QUEUE_CAPACITY = 256
        const val CLOSE_JOIN_MS = 1_000L

        /** Diagnostics without touching the public contract. */
        internal fun droppedEventCount(appDirectory: File): Long = forApp(appDirectory).droppedEvents

        /** Test hook: waits until all events queued so far were dispatched. */
        internal fun awaitIdleForTest(appDirectory: File) = forApp(appDirectory).awaitIdle(5_000)
    }
}

/** Retriever decorator: emits [RecallHits] (IDs, scores, ranks, salted query hash) for every real search. */
class EventEmittingRetriever(private val delegate: ContinuumMemoryRetriever, appDirectory: File) : ContinuumMemoryRetriever by delegate {
    private val hub = ContinuumMemoryEventHub.forApp(appDirectory)
    override fun search(query: String, nowMillis: Long, k: Int): List<RetrievalHit> {
        val hits = delegate.search(query, nowMillis, k)
        if (hub.hasListeners())
            hub.emit(RecallHits(hub.queryHash(query), hits.mapIndexed { i, h -> RecallHit(h.doc.id, h.score.toFloat(), i + 1) }, nowMillis))
        return hits
    }
}

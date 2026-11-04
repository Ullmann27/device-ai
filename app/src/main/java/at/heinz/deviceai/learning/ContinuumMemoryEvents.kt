package at.heinz.deviceai.learning

import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
 * Producer side. Events carry only IDs/hashes. Dispatch runs on one daemon thread per app directory, so emitters
 * (ledger, archive, retriever) never block on listeners; listener exceptions are caught and logged.
 */
internal class ContinuumMemoryEventHub private constructor(private val appDirectory: File) : ContinuumMemoryEvents {
    private val listeners = CopyOnWriteArrayList<ContinuumMemoryEventListener>()
    private val executor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "continuum-memory-events").apply { isDaemon = true } }
    }
    private val salt: ByteArray by lazy { loadSalt() }

    override fun addListener(listener: ContinuumMemoryEventListener): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners.remove(listener) }
    }

    fun hasListeners() = listeners.isNotEmpty()

    fun emit(event: ContinuumMemoryEvent) {
        if (listeners.isEmpty()) return
        runCatching {
            executor.execute {
                for (l in listeners) { // re-read at dispatch time: a closed listener receives nothing
                    try { l.onMemoryEvent(event) } catch (t: Throwable) {
                        LOG.log(Level.WARNING, "memory event listener failed: ${t.javaClass.simpleName}")
                    }
                }
            }
        }.onFailure { LOG.log(Level.WARNING, "memory event dispatch rejected") }
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

        /** Test hook: waits until all events queued so far were dispatched. */
        internal fun awaitIdleForTest(appDirectory: File) {
            val hub = forApp(appDirectory)
            hub.executor.submit {}.get(5, TimeUnit.SECONDS)
        }
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

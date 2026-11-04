package at.heinz.deviceai.cockpit

import at.heinz.deviceai.learning.ContinuumMemoryEvent
import at.heinz.deviceai.learning.ContinuumMemoryEventListener
import at.heinz.deviceai.learning.ContinuumMemoryEvents
import at.heinz.deviceai.learning.DocumentLearned
import at.heinz.deviceai.learning.MemoryStored
import at.heinz.deviceai.learning.RecallHits
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Adapts the existing app-directory memory hub to the cockpit's presentation contract.
 * It creates no memory activity, stores no history and forwards only bounded metadata tokens,
 * salted query hashes, counts and finite nonnegative retrieval scores. Scores are retrieval
 * scores, not a probability or a claim that an owner fact is confirmed.
 *
 * Delivery stays on the hub thread. Use [MemoryEventMapper] for main-thread/coalesced rendering.
 * At screen teardown call `mapper.detach()` before closing the subscription: the mapper owns
 * any flush already posted to Android's main thread.
 */
class ContinuumMemoryEventSource(private val events: ContinuumMemoryEvents) : MemoryEventSource {
    override fun subscribe(sink: MemoryEventSink): AutoCloseable {
        val subscription = Subscription(sink)
        subscription.attach(events.addListener(subscription))
        return subscription
    }

    /** One lifecycle per sink; no shared closed flag can disconnect another screen. */
    private class Subscription(sink: MemoryEventSink) : ContinuumMemoryEventListener, AutoCloseable {
        private val deliveryLock = Any()
        private val closed = AtomicBoolean()
        private var sink: MemoryEventSink? = sink
        private var upstream: AutoCloseable? = null

        fun attach(registration: AutoCloseable) {
            val closeImmediately = synchronized(deliveryLock) {
                if (closed.get()) true else { upstream = registration; false }
            }
            if (closeImmediately) registration.close()
        }

        override fun onMemoryEvent(event: ContinuumMemoryEvent) {
            synchronized(deliveryLock) {
                if (closed.get()) return
                val target = sink ?: return
                when (event) {
                    is DocumentLearned -> if (isToken(event.docId) && isToken(event.kind)) {
                        target.documentLearned(event.docId, event.kind, event.entriesAdded.coerceAtLeast(0), event.atEpochMs)
                    }
                    is MemoryStored -> if (isToken(event.entryId)) {
                        target.memoryStored(event.entryId, event.status.name, event.atEpochMs)
                    }
                    is RecallHits -> if (isQueryHash(event.queryHash)) {
                        val count = event.hits.size.coerceAtLeast(0)
                        // The producer's rank, not score order, identifies the top hit. Work is bounded
                        // even for an unexpectedly large supplied collection; absent rank 1 stays unknown.
                        val topScore = if (count == 0) null else event.hits.asSequence()
                            .take(MAX_RANK_SCAN).firstOrNull { it.rank == 1 }?.score
                            ?.takeIf { it.isFinite() && it >= 0f }
                        target.recallHits(event.queryHash, count, topScore, event.atEpochMs)
                    }
                }
            }
        }

        /**
         * Mark closed before waiting for a concurrent delivery. When this returns, no new sink call
         * can start and a delivery on another thread has completed. Reentrant close from the current
         * callback is supported. Sinks must stay short and must not wait for a thread calling close.
         */
        override fun close() {
            closed.set(true)
            val registration = synchronized(deliveryLock) {
                sink = null
                upstream.also { upstream = null }
            }
            registration?.close()
        }
    }

    companion object {
        private const val MAX_TOKEN_LENGTH = 128
        private const val MAX_RANK_SCAN = 256

        /** Uses exactly the same canonical app-directory hub as the existing producers. */
        fun forApp(appDirectory: File): ContinuumMemoryEventSource =
            ContinuumMemoryEventSource(ContinuumMemoryEvents.forApp(appDirectory))

        private fun isToken(value: String): Boolean = value.length in 1..MAX_TOKEN_LENGTH &&
            value.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' || it == '.' || it == ':' }

        private fun isQueryHash(value: String): Boolean = value.length == 64 &&
            value.all { it in '0'..'9' || it in 'a'..'f' }
    }
}

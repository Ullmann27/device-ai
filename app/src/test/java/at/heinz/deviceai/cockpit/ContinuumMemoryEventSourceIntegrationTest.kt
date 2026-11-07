package at.heinz.deviceai.cockpit

import at.heinz.deviceai.learning.ContinuumMemoryEvent as LearningEvent
import at.heinz.deviceai.learning.ContinuumMemoryEventListener as LearningListener
import at.heinz.deviceai.learning.ContinuumMemoryEvents as LearningEvents
import at.heinz.deviceai.learning.ContinuumMemoryEventHub
import at.heinz.deviceai.learning.ContinuumMemoryRetriever
import at.heinz.deviceai.learning.DocumentLearned as LearnedDocument
import at.heinz.deviceai.learning.MemoryDoc
import at.heinz.deviceai.learning.MemoryEntryStatus as EntryStatus
import at.heinz.deviceai.learning.MemoryStored as StoredMemory
import at.heinz.deviceai.learning.RecallHit as LearningHit
import at.heinz.deviceai.learning.RecallHits as RecalledMemory
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ContinuumMemoryEventSourceIntegrationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun realHubMapsDocumentAndStorageOnlyDuringTheMainThreadFlush() {
        val directory = temporary.newFolder()
        val hub = ContinuumMemoryEventHub.forApp(directory)
        val bus = CockpitAnimationBus()
        val seen = CopyOnWriteArrayList<CockpitAnimationEvent>()
        val threads = CopyOnWriteArrayList<Thread>()
        val posted = CopyOnWriteArrayList<Runnable>()
        val mapper = MemoryEventMapper(bus) { _, runnable -> posted += runnable }
        val renderer = bus.subscribe { seen += it; threads += Thread.currentThread() }
        val subscription = source(hub).subscribe(mapper)
        try {
            hub.emit(LearnedDocument("doc-1", "ARCHIVE_IMPORT", 3, 10L))
            hub.emit(StoredMemory("entry-1", EntryStatus.UNCONFIRMED, 11L))
            hub.emit(StoredMemory("entry-2", EntryStatus.REVOKED, 12L))
            hub.awaitIdle(5_000)
            assertTrue(seen.isEmpty())
            assertEquals(1, posted.size)
            posted.single().run()
            assertEquals(listOf(
                CockpitAnimationEvent.DocumentLearned(MEMORY_TURN, 10L, "doc-1", "ARCHIVE_IMPORT", 3),
                CockpitAnimationEvent.MemoryStored(MEMORY_TURN, 12L, "entry-2", "REVOKED", 2),
            ), seen.toList())
            assertTrue(threads.all { it === Thread.currentThread() })
        } finally { mapper.detach(); subscription.close(); renderer.close() }
    }

    @Test fun realRetrieverEmitsOnlySaltedQueryHashCountAndScoreWithoutOwnerText() {
        val directory = temporary.newFolder()
        val sink = RecordingSink()
        val subscription = factory(directory).subscribe(sink)
        try {
            val retriever = ContinuumMemoryRetriever.default(appDirectory = directory)
            val privateText = "Synthetic secret: Silberfuchs wohnt im Kieferntal."
            val privateQuery = "Wo wohnt Silberfuchs?"
            retriever.index(listOf(MemoryDoc("entry-1", privateText, 10L, true)))
            val actual = retriever.search(privateQuery, 20L, 3)
            ContinuumMemoryEventHub.awaitIdleForTest(directory)
            val event = sink.seen.single() as CockpitAnimationEvent.RecallHits
            assertEquals(1, actual.size)
            assertEquals(1, event.count)
            assertTrue(event.queryHash.matches(Regex("[a-f0-9]{64}")))
            assertEquals(actual.single().score.toFloat(), event.topScore!!, 0.000001f)
            assertFalse(event.toString().contains(privateText))
            assertFalse(event.toString().contains(privateQuery))
            assertFalse(event.toString().contains("Silberfuchs"))
            retriever.search("Quantenbanane", 21L, 3)
            ContinuumMemoryEventHub.awaitIdleForTest(directory)
            val empty = sink.seen.last() as CockpitAnimationEvent.RecallHits
            assertEquals(0, empty.count)
            assertNull(empty.topScore)
        } finally { subscription.close() }
    }

    @Test fun factorySharesCanonicalDirectoryButNeverAnotherAppsEvents() {
        val directory = temporary.newFolder()
        val other = temporary.newFolder()
        val first = RecordingSink()
        val second = RecordingSink()
        val one = factory(File(directory, ".")).subscribe(first)
        val two = factory(other).subscribe(second)
        try {
            ContinuumMemoryEventHub.forApp(directory).emit(StoredMemory("entry-1", EntryStatus.CONFIRMED, 1L))
            ContinuumMemoryEventHub.awaitIdleForTest(directory)
            assertEquals(1, first.seen.size)
            assertTrue(second.seen.isEmpty())
            one.close()
            ContinuumMemoryEventHub.forApp(other).emit(StoredMemory("entry-2", EntryStatus.HYPOTHETICAL, 2L))
            ContinuumMemoryEventHub.awaitIdleForTest(other)
            assertEquals(1, first.seen.size)
            assertEquals("HYPOTHETICAL", (second.seen.single() as CockpitAnimationEvent.MemoryStored).status)
        } finally { one.close(); two.close() }
    }

    @Test fun listenerAlreadyCapturedByRealHubIsSuppressedAfterClose() {
        val directory = temporary.newFolder()
        val hub = ContinuumMemoryEventHub.forApp(directory)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocker = hub.addListener { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        val sink = RecordingSink()
        val subscription = source(hub).subscribe(sink)
        try {
            hub.emit(StoredMemory("entry-1", EntryStatus.CONFIRMED, 1L))
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            subscription.close()
            subscription.close()
            release.countDown()
            hub.awaitIdle(5_000)
            assertTrue("CopyOnWrite listener snapshots must not revive a closed adapter", sink.seen.isEmpty())
        } finally { release.countDown(); subscription.close(); blocker.close() }
    }

    @Test fun closeWaitsForDeliveryAlreadyInProgressAndNoDeliveryStartsAfterItReturns() {
        val upstream = CapturedListenerEvents()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closing = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val seen = AtomicInteger()
        val failure = AtomicReference<Throwable?>()
        val sink = object : RecordingSink() {
            override fun memoryStored(entryId: String, status: String, atEpochMs: Long) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                seen.incrementAndGet()
            }
        }
        val subscription = source(upstream).subscribe(sink)
        val delivery = Thread {
            try { upstream.deliver(StoredMemory("entry-1", EntryStatus.CONFIRMED, 1L)) }
            catch (error: Throwable) { failure.set(error) }
        }
        val closer = Thread { closing.countDown(); subscription.close(); closed.countDown() }
        try {
            delivery.start()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            closer.start()
            assertTrue(closing.await(5, TimeUnit.SECONDS))
            assertFalse("close must not finish with an unguarded sink callback still running", closed.await(100, TimeUnit.MILLISECONDS))
            release.countDown()
            assertTrue(closed.await(5, TimeUnit.SECONDS))
            upstream.deliver(StoredMemory("entry-2", EntryStatus.CONFIRMED, 2L))
            assertEquals(1, seen.get())
            assertEquals(1, upstream.closes.get())
            assertNull(failure.get())
        } finally {
            release.countDown(); delivery.join(5_000); closer.join(5_000); subscription.close()
        }
    }

    @Test fun closeCanBeCalledFromTheSinkAndUnsubscribesExactlyOnce() {
        val upstream = CapturedListenerEvents()
        val seen = AtomicInteger()
        lateinit var subscription: AutoCloseable
        subscription = source(upstream).subscribe(object : RecordingSink() {
            override fun memoryStored(entryId: String, status: String, atEpochMs: Long) {
                seen.incrementAndGet()
                subscription.close()
            }
        })
        upstream.deliver(StoredMemory("entry-1", EntryStatus.CONFIRMED, 1L))
        upstream.deliver(StoredMemory("entry-2", EntryStatus.CONFIRMED, 2L))
        subscription.close()
        assertEquals(1, seen.get())
        assertEquals(1, upstream.closes.get())
    }

    @Test fun throwingVisualSinkCannotStopOtherSubscriptionsOnTheRealHub() {
        val hub = ContinuumMemoryEventHub.forApp(temporary.newFolder())
        val broken = source(hub).subscribe(object : RecordingSink() {
            override fun memoryStored(entryId: String, status: String, atEpochMs: Long) {
                throw IllegalStateException("synthetic sink failure")
            }
        })
        val healthy = RecordingSink()
        val active = source(hub).subscribe(healthy)
        try {
            hub.emit(StoredMemory("entry-1", EntryStatus.CONFIRMED, 1L))
            hub.emit(StoredMemory("entry-2", EntryStatus.REVOKED, 2L))
            hub.awaitIdle(5_000)
            assertEquals(2, healthy.seen.size)
            assertEquals("REVOKED", (healthy.seen.last() as CockpitAnimationEvent.MemoryStored).status)
        } finally { broken.close(); active.close() }
    }

    @Test fun invalidScoresNeverBecomeConfidenceAndRankOneIsUsedInsteadOfTheLargestScore() {
        val upstream = CapturedListenerEvents()
        val sink = RecordingSink()
        val subscription = source(upstream).subscribe(sink)
        try {
            listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -0.5f).forEachIndexed { index, value ->
                upstream.deliver(RecalledMemory("a".repeat(64), listOf(LearningHit("entry-1", value, 1)), index.toLong()))
            }
            assertTrue(sink.seen.all { (it as CockpitAnimationEvent.RecallHits).topScore == null })
            upstream.deliver(RecalledMemory("b".repeat(64), listOf(
                LearningHit("entry-2", 0.9f, 2), LearningHit("entry-1", 0.4f, 1),
            ), 5L))
            assertEquals(0.4f, (sink.seen.last() as CockpitAnimationEvent.RecallHits).topScore!!, 0f)
        } finally { subscription.close() }
    }

    @Test fun malformedTextFieldsAreNotForwardedToPresentationAsIdsOrHashes() {
        val upstream = CapturedListenerEvents()
        val sink = RecordingSink()
        val subscription = source(upstream).subscribe(sink)
        try {
            upstream.deliver(StoredMemory("Private sentence about a synthetic owner", EntryStatus.CONFIRMED, 1L))
            upstream.deliver(LearnedDocument("doc-1", "Private sentence about a synthetic owner", 1, 2L))
            upstream.deliver(RecalledMemory("Private question about a synthetic owner", emptyList(), 3L))
            assertTrue(sink.seen.isEmpty())
        } finally { subscription.close() }
    }

    @Test fun detachingMapperBeforeCloseSuppressesAnAlreadyQueuedMainThreadFlush() {
        val upstream = CapturedListenerEvents()
        val seen = mutableListOf<CockpitAnimationEvent>()
        val posted = mutableListOf<Runnable>()
        val bus = CockpitAnimationBus()
        val renderer = bus.subscribe { seen += it }
        val mapper = MemoryEventMapper(bus) { _, runnable -> posted += runnable }
        val subscription = source(upstream).subscribe(mapper)
        try {
            upstream.deliver(StoredMemory("entry-1", EntryStatus.CONFIRMED, 1L))
            assertEquals(1, posted.size)
            mapper.detach()
            subscription.close()
            posted.single().run()
            assertTrue(seen.isEmpty())
        } finally { mapper.detach(); subscription.close(); renderer.close() }
    }

    // The public boundary is loaded reflectively so RED executes behavioral assertions even before
    // this new adapter exists. No replacement implementation or test event source is activated in the app.
    private fun adapterClass(): Class<*> = try {
        Class.forName("at.heinz.deviceai.cockpit.ContinuumMemoryEventSource")
    } catch (error: ClassNotFoundException) {
        throw AssertionError("The production memory-to-cockpit adapter is missing", error)
    }

    private fun source(events: LearningEvents): MemoryEventSource =
        adapterClass().getConstructor(LearningEvents::class.java).newInstance(events) as MemoryEventSource

    private fun factory(directory: File): MemoryEventSource {
        val type = adapterClass()
        val companion = type.getField("Companion").get(null)
        return companion.javaClass.getMethod("forApp", File::class.java).invoke(companion, directory) as MemoryEventSource
    }

    private open class RecordingSink : MemoryEventSink {
        val seen = CopyOnWriteArrayList<CockpitAnimationEvent>()
        override fun documentLearned(docId: String, kind: String, entriesAdded: Int, atEpochMs: Long) {
            seen += CockpitAnimationEvent.DocumentLearned(MEMORY_TURN, atEpochMs, docId, kind, entriesAdded)
        }
        override fun memoryStored(entryId: String, status: String, atEpochMs: Long) {
            seen += CockpitAnimationEvent.MemoryStored(MEMORY_TURN, atEpochMs, entryId, status)
        }
        override fun recallHits(queryHash: String, count: Int, topScore: Float?, atEpochMs: Long) {
            seen += CockpitAnimationEvent.RecallHits(MEMORY_TURN, atEpochMs, queryHash, count, topScore)
        }
    }

    // Deliberately retains its captured listener to reproduce callbacks racing upstream removal.
    // The ordinary mapping and isolation tests above use the actual production hub/retriever.
    private class CapturedListenerEvents : LearningEvents {
        private lateinit var listener: LearningListener
        val closes = AtomicInteger()
        override fun addListener(listener: LearningListener): AutoCloseable {
            this.listener = listener
            return AutoCloseable { closes.incrementAndGet() }
        }
        fun deliver(event: LearningEvent) = listener.onMemoryEvent(event)
    }
}

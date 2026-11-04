package at.heinz.deviceai.cockpit

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Actual mapper/bus regressions; these tests do not replace either production implementation. */
class MemoryEventMapperLifecycleRegressionTest {
    @Test fun detachingInTheDocumentCallbackDropsTheRemainingKindsInThatFlush() {
        val bus = CockpitAnimationBus()
        val posted = ConcurrentLinkedQueue<Runnable>()
        val mapper = MemoryEventMapper(bus) { _, runnable -> posted.add(runnable) }
        val seen = mutableListOf<CockpitAnimationEvent>()
        val renderer = bus.subscribe { event ->
            seen += event
            if (event is CockpitAnimationEvent.DocumentLearned) mapper.detach()
        }
        try {
            mapper.documentLearned("document-1", "ARCHIVE_IMPORT", 2, 1L)
            mapper.memoryStored("entry-1", "CONFIRMED", 2L)
            mapper.recallHits("a".repeat(64), 1, 0.8f, 3L)
            assertEquals(1, posted.size)

            checkNotNull(posted.poll()).run()

            assertEquals("No later event may start after the first callback detaches the mapper", 1, seen.size)
            assertTrue(seen.single() is CockpitAnimationEvent.DocumentLearned)
            assertTrue(posted.isEmpty())
        } finally {
            mapper.detach()
            renderer.close()
        }
    }

    @Test fun detachingInTheStorageCallbackDropsThePendingRecall() {
        val bus = CockpitAnimationBus()
        val posted = ConcurrentLinkedQueue<Runnable>()
        val mapper = MemoryEventMapper(bus) { _, runnable -> posted.add(runnable) }
        val seen = mutableListOf<CockpitAnimationEvent>()
        val renderer = bus.subscribe { event ->
            seen += event
            if (event is CockpitAnimationEvent.MemoryStored) mapper.detach()
        }
        try {
            mapper.memoryStored("entry-1", "CONFIRMED", 1L)
            mapper.recallHits("b".repeat(64), 1, 0.6f, 2L)

            checkNotNull(posted.poll()).run()

            assertEquals("A detach during the storage callback must suppress the same flush's recall", 1, seen.size)
            assertTrue(seen.single() is CockpitAnimationEvent.MemoryStored)
            assertTrue(posted.isEmpty())
        } finally {
            mapper.detach()
            renderer.close()
        }
    }

    @Test fun aRendererCanWaitForAnotherProducerWithoutHoldingAMapperLock() {
        val bus = CockpitAnimationBus()
        val posted = ConcurrentLinkedQueue<Runnable>()
        val mapper = MemoryEventMapper(bus) { _, runnable -> posted.add(runnable) }
        val produced = CountDownLatch(1)
        val observedInsideCallback = AtomicBoolean()
        val failure = AtomicReference<Throwable?>()
        val seen = mutableListOf<CockpitAnimationEvent.MemoryStored>()
        val producer = Thread({
            try {
                mapper.memoryStored("second", "UNCONFIRMED", 2L)
            } catch (error: Throwable) {
                failure.compareAndSet(null, error)
            } finally {
                produced.countDown()
            }
        }, "memory-mapper-callback-producer").apply { isDaemon = true }
        val renderer = bus.subscribe { event ->
            if (event is CockpitAnimationEvent.MemoryStored) {
                seen += event
                if (event.entryId == "first") {
                    producer.start()
                    observedInsideCallback.set(produced.await(5, TimeUnit.SECONDS))
                }
            }
        }
        try {
            mapper.memoryStored("first", "CONFIRMED", 1L)
            checkNotNull(posted.poll()).run()
            producer.join(5_000L)
            assertFalse("The callback's producer must terminate", producer.isAlive)
            assertNull(failure.get())
            assertTrue("No mapper lock may be held while calling the renderer", observedInsideCallback.get())
            drain(posted)
            assertEquals(listOf("first", "second"), seen.map { it.entryId })
            assertEquals(listOf(1, 1), seen.map { it.count })
        } finally {
            producer.interrupt()
            producer.join(2_000L)
            mapper.detach()
            renderer.close()
        }
    }

    /**
     * A fixed concurrent workload, not a timing assertion. The old split atomics admit count loss
     * or double counting; whether the particular baseline run hits that window must be recorded.
     */
    @Test fun concurrentProducerAndFlushAccountForEveryWriteExactlyOnce() {
        val bus = CockpitAnimationBus()
        val posted = ConcurrentLinkedQueue<Runnable>()
        val mapper = MemoryEventMapper(bus) { _, runnable -> posted.add(runnable) }
        val start = CountDownLatch(1)
        val producerFinished = CountDownLatch(1)
        val stop = AtomicBoolean()
        val failure = AtomicReference<Throwable?>()
        val accountedWrites = AtomicLong()
        val emittedBatches = AtomicInteger()
        val lastEntry = AtomicReference<String?>()
        val renderer = bus.subscribe { event ->
            if (event is CockpitAnimationEvent.MemoryStored) {
                accountedWrites.addAndGet(event.count.toLong())
                emittedBatches.incrementAndGet()
                lastEntry.set(event.entryId)
            }
        }
        val producer = Thread({
            try {
                check(start.await(5, TimeUnit.SECONDS))
                for (index in 0 until WRITES) {
                    check(!stop.get() && !Thread.currentThread().isInterrupted)
                    mapper.memoryStored("entry-$index", "CONFIRMED", index.toLong())
                }
            } catch (error: Throwable) {
                failure.compareAndSet(null, error)
            } finally {
                producerFinished.countDown()
            }
        }, "memory-mapper-write-producer").apply { isDaemon = true }
        val flusher = Thread({
            try {
                check(start.await(5, TimeUnit.SECONDS))
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10L)
                while (producerFinished.count > 0L || posted.isNotEmpty()) {
                    check(!stop.get() && !Thread.currentThread().isInterrupted)
                    check(System.nanoTime() < deadline) { "Concurrent mapper drain exceeded its bounded deadline" }
                    val next = posted.poll()
                    if (next == null) Thread.yield() else next.run()
                }
            } catch (error: Throwable) {
                failure.compareAndSet(null, error)
            }
        }, "memory-mapper-single-flusher").apply { isDaemon = true }
        try {
            producer.start()
            flusher.start()
            start.countDown()
            producer.join(12_000L)
            flusher.join(12_000L)
            assertFalse("The actual producer must terminate", producer.isAlive)
            assertFalse("The single flush thread must terminate", flusher.isAlive)
            assertNull("Worker exceptions must not be hidden by the bus", failure.get())
            assertTrue("The drain must consume every scheduled flush", posted.isEmpty())
            assertTrue("The real mapper must emit at least one batch", emittedBatches.get() > 0)
            assertEquals("Coalescing may change batch size, but must neither lose nor invent writes",
                WRITES.toLong(), accountedWrites.get())
            assertEquals("entry-${WRITES - 1}", lastEntry.get())
        } finally {
            stop.set(true)
            start.countDown()
            producer.interrupt()
            flusher.interrupt()
            producer.join(2_000L)
            flusher.join(2_000L)
            mapper.detach()
            renderer.close()
        }
    }

    private fun drain(posted: ConcurrentLinkedQueue<Runnable>) {
        while (true) {
            val next = posted.poll() ?: return
            next.run()
        }
    }

    private companion object {
        const val WRITES = 80_000
    }
}

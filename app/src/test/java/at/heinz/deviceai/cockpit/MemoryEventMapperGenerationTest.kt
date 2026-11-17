package at.heinz.deviceai.cockpit

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class MemoryEventMapperGenerationTest {
    @Test fun changedGenerationDiscardsEveryBufferedKind() {
        val f = Fixture()
        f.mapper.documentLearned("old-doc", "PDF", 2, 100L)
        f.mapper.memoryStored("old-entry", "CONFIRMED", 101L)
        f.mapper.recallHits("old-query", 2, .8f, 102L)
        f.generation.incrementAndGet()
        f.drain()
        assertTrue(f.seen.isEmpty())
    }

    @Test fun freshRecallSurvivesTheOldKindsInTheSameFlush() {
        val f = Fixture()
        f.mapper.documentLearned("old-doc", "PDF", 2, 100L)
        f.mapper.memoryStored("old-entry", "CONFIRMED", 101L)
        f.mapper.recallHits("old-query", 2, .8f, 102L)
        f.generation.incrementAndGet()
        f.mapper.recallHits("new-query", 3, .9f, 1L)
        f.drain()
        assertEquals(listOf(CockpitAnimationEvent.RecallHits(MEMORY_TURN, 1L, "new-query", 3, .9f)), f.seen)
    }

    @Test fun storedCountsNeverCrossTheStopBoundary() {
        val f = Fixture()
        repeat(4) { f.mapper.memoryStored("old-$it", "CONFIRMED", it.toLong()) }
        f.generation.incrementAndGet()
        f.mapper.memoryStored("fresh-a", "UNCONFIRMED", 10L)
        f.mapper.memoryStored("fresh-b", "HYPOTHETICAL", 11L)
        f.drain()
        assertEquals(listOf(CockpitAnimationEvent.MemoryStored(MEMORY_TURN, 11L, "fresh-b", "HYPOTHETICAL", 2)), f.seen)
    }

    @Test fun generationIsRecheckedAfterEachRendererCallback() {
        val f = Fixture()
        f.bus.subscribe { if (it is CockpitAnimationEvent.DocumentLearned) f.generation.incrementAndGet() }
        f.mapper.documentLearned("doc", "PDF", 2, 100L)
        f.mapper.memoryStored("entry", "CONFIRMED", 101L)
        f.mapper.recallHits("query", 2, .8f, 102L)
        f.drain()
        assertEquals(1, f.seen.size)
        assertTrue(f.seen.single() is CockpitAnimationEvent.DocumentLearned)
    }

    @Test fun aNewKindDuringTheStopCallbackIsPreserved() {
        val f = Fixture()
        f.bus.subscribe {
            if (it is CockpitAnimationEvent.DocumentLearned) {
                f.generation.incrementAndGet()
                f.mapper.recallHits("fresh", 1, .5f, 20L)
            }
        }
        f.mapper.documentLearned("doc", "PDF", 2, 10L)
        f.mapper.memoryStored("old-entry", "CONFIRMED", 11L)
        f.mapper.recallHits("old-query", 2, .8f, 12L)
        f.drain()
        assertEquals(2, f.seen.size)
        assertEquals(CockpitAnimationEvent.RecallHits(MEMORY_TURN, 20L, "fresh", 1, .5f), f.seen.last())
    }

    @Test fun wallClockChangesDoNotChangePresentationEligibility() {
        val f = Fixture()
        f.mapper.documentLearned("doc", "PDF", 2, Long.MAX_VALUE)
        f.mapper.memoryStored("entry", "CONFIRMED", Long.MIN_VALUE)
        f.mapper.recallHits("query", 2, .8f, -1L)
        f.drain()
        assertEquals(3, f.seen.size)
        assertEquals(listOf(Long.MAX_VALUE, Long.MIN_VALUE, -1L), f.seen.map { it.atMillis })
    }

    @Test fun anOldProducerCannotOverwriteAFreshGeneration() {
        val generation = AtomicLong()
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val blocked = AtomicBoolean()
        val failure = AtomicReference<Throwable?>()
        val posted = ConcurrentLinkedQueue<Runnable>()
        val seen = mutableListOf<CockpitAnimationEvent>()
        val bus = CockpitAnimationBus().also { it.subscribe(seen::add) }
        val mapper = MemoryEventMapper(bus, generationProvider = {
            val value = generation.get()
            if (Thread.currentThread().name == "old-memory-generation" && blocked.compareAndSet(false, true)) {
                entered.countDown()
                check(resume.await(5L, TimeUnit.SECONDS))
            }
            value
        }) { _, runnable -> posted.add(runnable) }
        val producer = Thread({
            try { mapper.recallHits("old", 2, .8f, 1L) }
            catch (error: Throwable) { failure.set(error) }
        }, "old-memory-generation")
        try {
            producer.start()
            assertTrue(entered.await(5L, TimeUnit.SECONDS))
            generation.incrementAndGet()
            mapper.recallHits("fresh", 3, .9f, 2L)
            resume.countDown()
            producer.join(5_000L)
            assertFalse(producer.isAlive)
            assertNull(failure.get())
            while (true) (posted.poll() ?: break).run()
            assertEquals(listOf(CockpitAnimationEvent.RecallHits(MEMORY_TURN, 2L, "fresh", 3, .9f)), seen)
        } finally {
            resume.countDown()
            producer.interrupt()
            producer.join(2_000L)
            mapper.detach()
        }
    }

    private class Fixture {
        val generation = AtomicLong()
        val posted = ConcurrentLinkedQueue<Runnable>()
        val seen = mutableListOf<CockpitAnimationEvent>()
        val bus = CockpitAnimationBus().also { it.subscribe(seen::add) }
        val mapper = MemoryEventMapper(bus, generationProvider = generation::get) { _, runnable -> posted.add(runnable) }
        fun drain() { while (true) (posted.poll() ?: break).run() }
    }
}

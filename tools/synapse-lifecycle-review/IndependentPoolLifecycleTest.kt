package at.heinz.deviceai.chief

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Independent frozen controls. Real production pool; no executor/model substitute. */
class IndependentPoolLifecycleTest {
    private fun brief(id: String, deadline: Long = 5_000) = WorkerBrief(id, "bounded test", emptySet(), deadline)
    private fun await(latch: CountDownLatch) = assertTrue("latch deadline", latch.await(3, TimeUnit.SECONDS))
    private fun uninterruptible(latch: CountDownLatch) {
        while (latch.count != 0L) try { latch.await() } catch (_: InterruptedException) { }
    }

    @Test fun shutdownReportsEveryAcceptedQueuedAssignmentExactlyOnce() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val factoryIds = ConcurrentLinkedQueue<String>()
        val reports = ConcurrentLinkedQueue<WorkerReport>()
        val pool = ChiefWorkerPool(1, 3) { b ->
            factoryIds += b.id
            WorkerJob { entered.countDown(); uninterruptible(release); "late answer" }
        }
        try {
            assertTrue(pool.submit(brief("running")) { reports += it }); await(entered)
            for (id in listOf("queued-a", "queued-b", "queued-c"))
                assertTrue(pool.submit(brief(id)) { reports += it })
            pool.shutdown(); pool.shutdown()
            release.countDown()
            assertTrue("shutdown must drain accepted assignment accounting", pool.awaitIdle(3_000))
            assertEquals(listOf("queued-a", "queued-b", "queued-c", "running"), reports.map { it.id }.sorted())
            assertTrue(reports.all { it.status == WorkerStatus.CANCELLED })
            assertEquals(listOf("running"), factoryIds.toList())
            assertTrue(pool.active().isEmpty())
        } finally { release.countDown(); pool.shutdown() }
    }

    @Test fun cancellationDuringFactoryPublicationNeverStartsJobAndForwardsStopOnce() {
        val constructing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val ran = AtomicInteger(); val cancelled = AtomicInteger()
        val reports = ConcurrentLinkedQueue<WorkerReport>()
        val pool = ChiefWorkerPool(1, 0) {
            constructing.countDown(); uninterruptible(release)
            object : WorkerJob {
                override fun run(): String { ran.incrementAndGet(); return "forbidden" }
                override fun cancel() { cancelled.incrementAndGet() }
            }
        }
        try {
            assertTrue(pool.submit(brief("constructing")) { reports += it }); await(constructing)
            assertTrue(pool.cancel("constructing")); assertTrue(pool.cancel("constructing"))
            release.countDown()
            assertTrue(pool.awaitIdle(3_000))
            assertEquals(0, ran.get()); assertEquals(1, cancelled.get())
            assertEquals(listOf(WorkerStatus.CANCELLED), reports.map { it.status })
        } finally { release.countDown(); pool.shutdown() }
    }

    @Test fun lateUninterruptibleResultCannotReplaceCancelledReport() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val exited = CountDownLatch(1)
        val reports = ConcurrentLinkedQueue<WorkerReport>()
        val pool = ChiefWorkerPool(1, 0) { WorkerJob {
            entered.countDown(); uninterruptible(release); exited.countDown(); "too late"
        } }
        try {
            assertTrue(pool.submit(brief("late")) { reports += it }); await(entered)
            assertTrue(pool.cancel("late")); assertTrue(pool.awaitIdle(3_000))
            assertEquals(listOf(WorkerStatus.CANCELLED), reports.map { it.status })
            assertEquals("report completion must not assert native job termination", 1L, exited.count)
            release.countDown(); await(exited)
            assertEquals(1, reports.size); assertFalse(pool.cancel("late"))
        } finally { release.countDown(); pool.shutdown() }
    }

    @Test fun idleIncludesThrowingCallbackCompletionDuringShutdown() {
        val callbackEntered = CountDownLatch(1); val releaseCallback = CountDownLatch(1)
        val callbacks = AtomicInteger()
        val pool = ChiefWorkerPool(1, 0) { WorkerJob { "done" } }
        try {
            assertTrue(pool.submit(brief("callback")) {
                callbacks.incrementAndGet(); callbackEntered.countDown()
                uninterruptible(releaseCallback); error("consumer failed")
            })
            await(callbackEntered); pool.shutdown()
            assertFalse(pool.awaitIdle(0))
            releaseCallback.countDown(); assertTrue(pool.awaitIdle(3_000))
            assertEquals(1, callbacks.get())
        } finally { releaseCallback.countDown(); pool.shutdown() }
    }

    @Test fun openPoolPreservesCallbackReentryAndShutdownClosesIt() {
        for (secondId in listOf("first", "second")) {
            val reports = ConcurrentLinkedQueue<WorkerReport>()
            val delivered = CountDownLatch(1)
            val accepted = AtomicBoolean()
            val pool = ChiefWorkerPool(1, 0) { WorkerJob { "done" } }
            try {
                assertTrue(pool.submit(brief("first")) { first ->
                    reports += first
                    accepted.set(pool.submit(brief(secondId)) { second -> reports += second; delivered.countDown() })
                })
                await(delivered); assertTrue(accepted.get()); assertTrue(pool.awaitIdle(3_000))
                assertEquals(2, reports.size)
                pool.shutdown()
                val attempt = runCatching { pool.submit(brief("closed")) { reports += it } }
                assertTrue(attempt.getOrNull() == false || attempt.exceptionOrNull() is RejectedExecutionException)
                assertEquals(2, reports.size); assertTrue(pool.awaitIdle(0)); assertTrue(pool.active().isEmpty())
            } finally { pool.shutdown() }
        }
    }

    @Test fun submitShutdownRaceNeverStrandsAcceptedWork() {
        repeat(40) { iteration ->
            val go = CountDownLatch(1); val entered = CountDownLatch(1); val release = CountDownLatch(1)
            val reports = ConcurrentLinkedQueue<WorkerReport>()
            val accepted = AtomicBoolean(); val failures = ConcurrentLinkedQueue<Throwable>()
            val pool = ChiefWorkerPool(1, 1) { WorkerJob { entered.countDown(); uninterruptible(release); "done" } }
            val submitter = Thread {
                try { go.await(); accepted.set(pool.submit(brief("race-$iteration")) { reports += it }) }
                catch (e: RejectedExecutionException) { /* rejection is an admissible outcome */ }
                catch (e: Throwable) { failures += e }
            }
            val closer = Thread { go.await(); pool.shutdown() }
            try {
                submitter.start(); closer.start(); go.countDown()
                submitter.join(3_000); closer.join(3_000); release.countDown()
                assertFalse(submitter.isAlive); assertFalse(closer.isAlive); assertTrue(failures.isEmpty())
                assertTrue("stranded shutdown race $iteration", pool.awaitIdle(3_000))
                assertEquals(if (accepted.get()) 1 else 0, reports.size)
                assertTrue(pool.active().isEmpty())
            } finally { release.countDown(); pool.shutdown() }
        }
    }

    @Test fun queuedCallbackExceptionDoesNotSuppressOtherCancellationReports() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val reports = ConcurrentLinkedQueue<String>()
        val pool = ChiefWorkerPool(1, 2) { WorkerJob { entered.countDown(); uninterruptible(release); "late" } }
        try {
            assertTrue(pool.submit(brief("running")) { reports += it.id }); await(entered)
            assertTrue(pool.submit(brief("throwing")) { reports += it.id; error("receiver broken") })
            assertTrue(pool.submit(brief("last")) { reports += it.id })
            pool.shutdown(); release.countDown()
            assertTrue(pool.awaitIdle(3_000))
            assertEquals(listOf("last", "running", "throwing"), reports.sorted())
        } finally { release.countDown(); pool.shutdown() }
    }

    @Test fun interruptResistantWorkerNeverMultipliesPhysicalExecution() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val exited = CountDownLatch(1)
        val physicalRuns = AtomicInteger(); val reports = ConcurrentLinkedQueue<WorkerReport>()
        val pool = ChiefWorkerPool(1, 0) { WorkerJob {
            physicalRuns.incrementAndGet(); entered.countDown(); uninterruptible(release); exited.countDown(); "late"
        } }
        try {
            assertTrue(pool.submit(brief("native", 300)) { reports += it }); await(entered)
            assertTrue(pool.awaitIdle(3_000))
            repeat(24) { i ->
                assertTrue(pool.submit(brief("next-$i", 10)) { reports += it })
                assertTrue(pool.awaitIdle(3_000))
            }
            assertEquals(1, physicalRuns.get())
            assertEquals(25, reports.size)
            assertTrue(reports.all { it.status == WorkerStatus.TIMED_OUT })
        } finally { pool.shutdown(); release.countDown(); await(exited) }
    }
}

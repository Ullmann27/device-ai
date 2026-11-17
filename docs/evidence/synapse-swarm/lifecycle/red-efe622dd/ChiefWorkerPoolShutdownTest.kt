package at.heinz.deviceai.chief

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ChiefWorkerPoolShutdownTest {
    private fun brief(id: String) = WorkerBrief(id, "Aufgabe $id", emptySet(), 5_000)

    private fun awaitIgnoringInterrupts(latch: CountDownLatch) {
        while (true) {
            try { latch.await(); return } catch (_: InterruptedException) { }
        }
    }

    @Test fun shutdownReportsQueuedJobsEvenWhileAFactoryIgnoresInterruption() {
        val factoryEntered = CountDownLatch(1)
        val releaseFactory = CountDownLatch(1)
        val factories = ConcurrentLinkedQueue<String>()
        val reports = ConcurrentLinkedQueue<WorkerReport>()
        val runs = AtomicInteger()
        val pool = ChiefWorkerPool(1, 2) { b ->
            factories += b.id
            factoryEntered.countDown()
            awaitIgnoringInterrupts(releaseFactory)
            WorkerJob { runs.incrementAndGet(); "must not execute" }
        }
        try {
            assertTrue(pool.submit(brief("constructing")) { reports += it })
            assertTrue(factoryEntered.await(2, TimeUnit.SECONDS))
            assertTrue(pool.submit(brief("queued-a")) { reports += it })
            assertTrue(pool.submit(brief("queued-b")) { reports += it })
            pool.shutdown()
            assertEquals("discarded supervisors still need terminal reports", setOf("queued-a", "queued-b"), reports.map { it.id }.toSet())
            assertTrue(reports.all { it.status == WorkerStatus.CANCELLED })
            assertEquals(setOf("constructing"), pool.active())
            assertFalse("the held factory has not completed", pool.awaitIdle(0))
            releaseFactory.countDown()
            assertTrue(pool.awaitIdle(2_000))
            assertEquals(setOf("constructing", "queued-a", "queued-b"), reports.map { it.id }.toSet())
            assertEquals(3, reports.size)
            assertEquals(listOf("constructing"), factories.toList())
            assertEquals(0, runs.get())
            assertTrue(pool.active().isEmpty())
        } finally {
            releaseFactory.countDown()
            pool.shutdown()
        }
    }

    @Test fun callbackCanShutdownItsOwnPoolAndTerminateQueuedJobsExactlyOnce() {
        val releaseFirst = CountDownLatch(1)
        val firstEntered = CountDownLatch(1)
        val callbackDone = CountDownLatch(1)
        val factories = ConcurrentLinkedQueue<String>()
        val reports = ConcurrentLinkedQueue<WorkerReport>()
        lateinit var pool: ChiefWorkerPool
        pool = ChiefWorkerPool(1, 1) { b ->
            factories += b.id
            WorkerJob { firstEntered.countDown(); releaseFirst.await(); "first completed" }
        }
        try {
            assertTrue(pool.submit(brief("first")) {
                reports += it
                pool.shutdown()
                callbackDone.countDown()
            })
            assertTrue(firstEntered.await(2, TimeUnit.SECONDS))
            assertTrue(pool.submit(brief("queued")) { reports += it })
            releaseFirst.countDown()
            assertTrue(callbackDone.await(2, TimeUnit.SECONDS))
            assertTrue("shutdown cannot orphan queued completion", pool.awaitIdle(1_000))
            assertEquals(2, reports.size)
            assertEquals(WorkerStatus.DONE, reports.first { it.id == "first" }.status)
            assertEquals(WorkerStatus.CANCELLED, reports.first { it.id == "queued" }.status)
            assertEquals(listOf("first"), factories.toList())
            pool.shutdown()
            assertEquals(2, reports.size)
            assertTrue(pool.active().isEmpty())
        } finally { releaseFirst.countDown(); pool.shutdown() }
    }

    @Test fun cancelledLateReturnNeverCreatesASecondSuccessOrReopensAdmission() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val cancellationCalls = AtomicInteger()
        val reports = ConcurrentLinkedQueue<WorkerReport>()
        val pool = ChiefWorkerPool(1, 1) {
            object : WorkerJob {
                override fun run(): String {
                    entered.countDown()
                    awaitIgnoringInterrupts(release)
                    exited.countDown()
                    return "late success"
                }
                override fun cancel() { cancellationCalls.incrementAndGet() }
            }
        }
        try {
            assertTrue(pool.submit(brief("late")) { reports += it })
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            pool.shutdown()
            assertTrue(pool.awaitIdle(2_000))
            assertEquals(WorkerStatus.CANCELLED, reports.single().status)
            assertEquals("report delivery is not proof of native process exit", 1L, exited.count)
            release.countDown()
            assertTrue(exited.await(2, TimeUnit.SECONDS))
            pool.shutdown()
            assertEquals(1, cancellationCalls.get())
            assertEquals(1, reports.size)
            assertTrue(pool.active().isEmpty())
            assertTrue(runCatching { pool.submit(brief("new")) {} }.exceptionOrNull() is RejectedExecutionException)
        } finally { release.countDown(); pool.shutdown() }
    }

    @Test fun shutdownRejectsReentrantSubmissionBeforeCallingQueuedReportConsumers() {
        val factoryEntered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val rejected = AtomicBoolean()
        val delivered = AtomicInteger()
        val pool = ChiefWorkerPool(1, 1) {
            factoryEntered.countDown(); awaitIgnoringInterrupts(release); WorkerJob { "unused" }
        }
        try {
            assertTrue(pool.submit(brief("factory")) { delivered.incrementAndGet() })
            assertTrue(factoryEntered.await(2, TimeUnit.SECONDS))
            assertTrue(pool.submit(brief("queued")) {
                delivered.incrementAndGet()
                rejected.set(runCatching { pool.submit(brief("reentrant")) {} }.exceptionOrNull() is RejectedExecutionException)
                throw IllegalStateException("consumer failed")
            })
            pool.shutdown()
            assertTrue("shutdown admission must close before terminal callbacks", rejected.get())
            release.countDown()
            assertTrue(pool.awaitIdle(2_000))
            assertEquals(2, delivered.get())
            assertTrue(pool.active().isEmpty())
        } finally { release.countDown(); pool.shutdown() }
    }
}

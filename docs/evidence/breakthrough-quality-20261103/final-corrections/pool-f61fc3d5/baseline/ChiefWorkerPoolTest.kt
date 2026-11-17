package at.heinz.deviceai.chief

import at.heinz.deviceai.ToolBackendFailure
import at.heinz.deviceai.ToolCallRequest
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.cognitive.AgentModel
import at.heinz.deviceai.cognitive.AgentModelOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Chief pattern point 2: bounded background workers with own brief, scoped tools and exactly one report. */
class ChiefWorkerPoolTest {
    private fun brief(id: String, tools: Set<String> = emptySet(), deadline: Long = 5_000) =
        WorkerBrief(id, "Aufgabe $id", tools, deadlineMillis = deadline)

    @Test fun submitReturnsImmediatelyAndReportsExactlyOnce() {
        val gate = CountDownLatch(1)
        val reports = ConcurrentLinkedQueue<WorkerReport>()
        val pool = ChiefWorkerPool(maxConcurrent = 1, maxQueued = 1) { b -> WorkerJob { gate.await(); "fertig ${b.id}" } }
        val started = System.nanoTime()
        assertTrue(pool.submit(brief("a")) { reports += it })
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 200) // chief stays responsive
        assertTrue(pool.submit(brief("b")) { reports += it })   // queued
        assertFalse(pool.submit(brief("c")) { reports += it })  // over capacity → refused, never silently dropped
        assertFalse(pool.submit(brief("a")) { reports += it })  // duplicate id
        gate.countDown()
        pool.awaitIdle(5_000)
        assertEquals(listOf("a", "b"), reports.map { it.id }.sorted())
        assertTrue(reports.all { it.status == WorkerStatus.DONE })
        assertEquals("fertig a", reports.first { it.id == "a" }.summary)
        pool.shutdown()
    }

    @Test fun awaitIdleDoesNotCompleteBeforeTheReportCallbackReturns() {
        val callbackEntered = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)
        val reports = ConcurrentLinkedQueue<WorkerReport>()
        val pool = ChiefWorkerPool(1, 0) { WorkerJob { "finished" } }
        try {
            assertTrue(pool.submit(brief("delivery")) { report ->
                callbackEntered.countDown()
                releaseCallback.await()
                reports += report
            })
            assertTrue("report callback entered", callbackEntered.await(2, TimeUnit.SECONDS))
            assertFalse("delivery is incomplete while its callback is blocked", pool.awaitIdle(0))
            assertTrue(reports.isEmpty())
            releaseCallback.countDown()
            assertTrue(pool.awaitIdle(2_000))
            assertEquals(1, reports.size)
            assertEquals(WorkerStatus.DONE, reports.single().status)
        } finally {
            releaseCallback.countDown()
            pool.shutdown()
        }
    }

    @Test fun aThrowingReportCallbackReleasesItsSlotAndDoesNotReportTwice() {
        val callbacks = AtomicInteger()
        val reports = ConcurrentLinkedQueue<WorkerReport>()
        val pool = ChiefWorkerPool(1, 0) { WorkerJob { "finished" } }
        try {
            assertTrue(pool.submit(brief("reusable")) {
                callbacks.incrementAndGet()
                error("report consumer failed")
            })
            assertTrue(pool.awaitIdle(2_000))
            assertEquals(1, callbacks.get())
            assertTrue(pool.active().isEmpty())
            assertTrue(pool.submit(brief("reusable")) {
                callbacks.incrementAndGet()
                reports += it
            })
            assertTrue(pool.awaitIdle(2_000))
            assertEquals(2, callbacks.get())
            assertEquals(WorkerStatus.DONE, reports.single().status)
        } finally {
            pool.shutdown()
        }
    }

    @Test fun reportCallbacksCanResubmitTheSameOrANewIdWithoutQueueCapacity() {
        for (nextId in listOf("initial", "next")) {
            val submitted = CountDownLatch(1)
            val delivered = CountDownLatch(1)
            val accepted = AtomicBoolean()
            val reports = ConcurrentLinkedQueue<WorkerReport>()
            val pool = ChiefWorkerPool(1, 0) { WorkerJob { "finished" } }
            try {
                assertTrue(pool.submit(brief("initial")) { first ->
                    reports += first
                    accepted.set(pool.submit(brief(nextId)) { second ->
                        reports += second
                        delivered.countDown()
                    })
                    submitted.countDown()
                })
                assertTrue("callback attempted resubmission", submitted.await(2, TimeUnit.SECONDS))
                assertTrue("callback may reuse released capacity for $nextId", accepted.get())
                assertTrue("reentrant job delivered", delivered.await(2, TimeUnit.SECONDS))
                assertTrue(pool.awaitIdle(2_000))
                assertEquals(listOf("initial", nextId), reports.map { it.id })
            } finally {
                pool.shutdown()
            }
        }
    }

    @Test fun submitAfterShutdownPreservesRejectionWithoutLeavingUnfinishedWork() {
        val callbacks = AtomicInteger()
        val pool = ChiefWorkerPool(1, 0) { WorkerJob { "must not run" } }
        pool.shutdown()
        val failure = runCatching {
            pool.submit(brief("closed")) { callbacks.incrementAndGet() }
        }.exceptionOrNull()
        assertTrue("executor rejection still reaches the caller", failure is RejectedExecutionException)
        assertEquals(0, callbacks.get())
        assertTrue("rejected admission must not retain capacity", pool.active().isEmpty())
        assertTrue("rejected admission is not unfinished work", pool.awaitIdle(0))
    }

    @Test fun deadlineCancelAndFailureBecomeHonestReports() {
        val reports = ConcurrentLinkedQueue<WorkerReport>()
        val pool = ChiefWorkerPool(maxConcurrent = 2, maxQueued = 2) { b ->
            when (b.id) {
                "slow" -> WorkerJob { Thread.sleep(10_000); "nie" }
                "boom" -> WorkerJob { error("kaputt") }
                else -> WorkerJob { Thread.sleep(10_000); "nie" }
            }
        }
        pool.submit(brief("slow", deadline = 100)) { reports += it }
        pool.submit(brief("boom")) { reports += it }
        pool.awaitIdle(3_000)
        val c = ChiefWorkerPool(1, 1) { WorkerJob { Thread.sleep(10_000); "nie" } }
        c.submit(brief("x")) { reports += it }; Thread.sleep(50); assertTrue(c.cancel("x")); c.awaitIdle(3_000)
        val byId = reports.associateBy { it.id }
        assertEquals(WorkerStatus.TIMED_OUT, byId["slow"]!!.status)
        assertEquals(WorkerStatus.FAILED, byId["boom"]!!.status)
        assertEquals(WorkerStatus.CANCELLED, byId["x"]!!.status)
        assertEquals(3, reports.size)
        pool.shutdown(); c.shutdown()
    }

    @Test fun workerToolsAreScopedToItsBriefAtModelAndRunnerLevel() {
        val b = brief("w", tools = setOf("get_battery_info"))
        var ran = 0
        val runner = WorkerScope.runner(b, ToolRunner { ran++; "ok" })
        assertEquals("ok", runner.execute(ToolCallRequest("1", "get_battery_info", emptyMap())))
        val refused = runCatching { runner.execute(ToolCallRequest("2", "delete_file", mapOf("path" to "/x"))) }
        assertTrue(refused.exceptionOrNull() is ToolBackendFailure)
        assertEquals(1, ran)
        val model = WorkerScope.model(b, AgentModel { _, _, _, _ ->
            AgentModelOutput("m", "", ToolProposal("delete_file", mapOf("path" to "/x"))) })
        val blocked = runCatching { model.generate("t", emptyList(), null, emptyList()) }.exceptionOrNull()
        assertTrue("an out-of-scope proposal must fail the job, never produce a successful answer", blocked is WorkerBlocked)
    }
    @Test fun cancellingAnInterruptResistantWorkerInvokesItsCooperativeStop() {
        val entered = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val mayStop = AtomicBoolean(false)
        val stops = AtomicInteger(0)
        val reports = ConcurrentLinkedQueue<WorkerReport>()
        val pool = ChiefWorkerPool(1, 0) {
            object : WorkerJob {
                override fun run(): String {
                    entered.countDown()
                    val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                    while (!mayStop.get() && System.nanoTime() < until) {
                        try { Thread.sleep(5) } catch (_: InterruptedException) { /* simulate native call ignoring interrupts */ }
                    }
                    stopped.countDown()
                    return "finished"
                }
                override fun cancel() { stops.incrementAndGet(); mayStop.set(true) }
            }
        }
        try {
            assertTrue(pool.submit(brief("interrupt-resistant")) { reports += it })
            assertTrue("worker started", entered.await(2, TimeUnit.SECONDS))
            assertTrue(pool.cancel("interrupt-resistant"))
            assertTrue("job stopped cooperatively instead of only cancelling its Future", stopped.await(2, TimeUnit.SECONDS))
            assertTrue(pool.awaitIdle(2_000))
            assertEquals("cooperative cancel is delivered exactly once", 1, stops.get())
            assertEquals(WorkerStatus.CANCELLED, reports.single().status)
        } finally {
            pool.shutdown()
        }
    }

    @Test fun timeoutsAlsoInvokeCooperativeStopBeforeReturningAReport() {
        val mayStop = AtomicBoolean(false)
        val stopped = CountDownLatch(1)
        val stops = AtomicInteger()
        val reports = ConcurrentLinkedQueue<WorkerReport>()
        val pool = ChiefWorkerPool(1, 0) {
            object : WorkerJob {
                override fun run(): String {
                    val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                    while (!mayStop.get() && System.nanoTime() < until) {
                        try { Thread.sleep(5) } catch (_: InterruptedException) { }
                    }
                    stopped.countDown()
                    return "finished"
                }
                override fun cancel() { stops.incrementAndGet(); mayStop.set(true) }
            }
        }
        try {
            assertTrue(pool.submit(brief("timeout", deadline = 50)) { reports += it })
            assertTrue(pool.awaitIdle(2_000))
            assertTrue(stopped.await(2, TimeUnit.SECONDS))
            assertEquals("timeout cancellation is delivered exactly once", 1, stops.get())
            assertEquals(WorkerStatus.TIMED_OUT, reports.single().status)
        } finally {
            pool.shutdown()
        }
    }

}

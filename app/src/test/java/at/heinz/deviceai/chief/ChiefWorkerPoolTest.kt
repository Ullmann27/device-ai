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
import java.util.concurrent.TimeUnit

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
        val out = model.generate("t", emptyList(), null, emptyList())
        assertEquals(null, out.proposal)
        assertTrue(out.response.contains("nicht freigegeben"))
    }
}

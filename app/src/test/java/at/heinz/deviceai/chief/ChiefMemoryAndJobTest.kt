package at.heinz.deviceai.chief

import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.cognitive.AgentEvaluator
import at.heinz.deviceai.cognitive.AgentModel
import at.heinz.deviceai.cognitive.CancellableAgentModel
import at.heinz.deviceai.cognitive.AgentReflection
import at.heinz.deviceai.cognitive.AgentToolObservation
import at.heinz.deviceai.cognitive.AgentModelOutput
import at.heinz.deviceai.cognitive.AgentToolApproval
import at.heinz.deviceai.cognitive.AgentToolApprovalProvider
import at.heinz.deviceai.cognitive.Evaluation
import at.heinz.deviceai.cognitive.InMemoryExperienceStore
import at.heinz.deviceai.cognitive.MemoryRecord
import at.heinz.deviceai.cognitive.ValidationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Sprint 98: Gedächtnis (#371 layers.shared()/search()) → orchestrator memories; task loop as ChiefWorkerPool job. */
class ChiefMemoryAndJobTest {
    private val port = object : ChiefMemoryPort {
        override fun sharedFacts() = listOf(ChiefMemoryItem("f1", "Ich wohne in Wien", 10, confirmed = true))
        override fun search(query: String, nowMillis: Long, limit: Int) = listOf(
            ChiefMemoryItem("f1", "Ich wohne in Wien", 10, confirmed = true),
            ChiefMemoryItem("h1", "Vielleicht ziehe ich nach Graz", 20, confirmed = false))
    }

    @Test fun confirmedFactsAndUnconfirmedHypothesesAreRecalledHonestly() {
        val recalled = ChiefMemory.recall(port, "Wo wohne ich?", nowMillis = 100)
        assertEquals(listOf("f1", "h1"), recalled.map { it.id })            // deduplicated
        val fact = recalled.first { it.id == "f1" }; val hyp = recalled.first { it.id == "h1" }
        assertEquals(ValidationState.CORROBORATED, fact.validationState)
        assertEquals(ValidationState.UNVERIFIED, hyp.validationState)
        assertTrue(hyp.confidence < fact.confidence && "hypothese" in hyp.tags)
    }

    @Test fun brokenMemoryFailsClosedToEmpty() {
        val broken = object : ChiefMemoryPort {
            override fun sharedFacts(): List<ChiefMemoryItem> = error("io")
            override fun search(query: String, nowMillis: Long, limit: Int): List<ChiefMemoryItem> = error("io")
        }
        assertTrue(ChiefMemory.recall(broken, "x", 1).isEmpty())
    }

    @Test fun agentJobRunsOrchestratorWithMemoryAndJobScopedToolsOnly() {
        val seen = ConcurrentLinkedQueue<List<MemoryRecord>>()
        val executed = ConcurrentLinkedQueue<String>()
        val model = AgentModel { _, memories, _, obs ->
            seen += memories
            when (obs.size) {
                0 -> AgentModelOutput("m", "", ToolProposal("delete_file", mapOf("path" to "/sdcard/a"))) // outside brief
                else -> AgentModelOutput("m", "Du wohnst in Wien.")
            }
        }
        val brief = WorkerBrief("job1", "Wo wohne ich?", allowedTools = setOf("get_battery_info"))
        val job = ChiefJobs.agentJob(brief, ChiefJobDeps(
            model = model, runner = ToolRunner { executed += it.name; "ok" },
            policy = ToolPolicy(readOnlyEnabled = true, destructiveEnabled = true),
            memory = port, evaluator = AgentEvaluator { _, _ -> Evaluation(true, 0.5) },
            experienceStore = InMemoryExperienceStore(),
            approvalProvider = AgentToolApprovalProvider { _, _ -> AgentToolApproval(true, true) },
            clock = { 100L }))
        val reports = ConcurrentLinkedQueue<WorkerReport>()
        val pool = ChiefWorkerPool(1, 0) { job }
        assertTrue(pool.submit(brief) { reports += it }); pool.awaitIdle(5_000); pool.shutdown()
        val r = reports.single()
        assertFalse("tool outside the brief must never execute", "delete_file" in executed)
        assertTrue(seen.first().any { it.content == "Ich wohne in Wien" })
        assertTrue(r.status == WorkerStatus.DONE || r.status == WorkerStatus.BLOCKED)
    }

    @Test fun blockedJobIsReportedAsBlocked() {
        val pool = ChiefWorkerPool(1, 0) { WorkerJob { throw WorkerBlocked("Freigabe verweigert") } }
        val reports = ConcurrentLinkedQueue<WorkerReport>()
        pool.submit(WorkerBrief("b", "x", emptySet())) { reports += it }; pool.awaitIdle(3_000); pool.shutdown()
        assertEquals(WorkerStatus.BLOCKED, reports.single().status)
        assertEquals("Freigabe verweigert", reports.single().summary)
    }
    @Test fun chiefJobCancelPropagatesIntoTheUnderlyingCancellableModel() {
        val entered = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val released = AtomicBoolean(false)
        val cancelCalls = AtomicInteger(0)
        val toolRuns = AtomicInteger(0)
        val model = object : CancellableAgentModel {
            override fun generate(
                task: String, memories: List<MemoryRecord>, reflection: AgentReflection?,
                toolObservations: List<AgentToolObservation>
            ): AgentModelOutput {
                entered.countDown()
                val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                while (!released.get() && System.nanoTime() < until) {
                    try { Thread.sleep(5) } catch (_: InterruptedException) { }
                }
                stopped.countDown()
                return AgentModelOutput("m", "Antwort ohne Werkzeuge.")
            }

            override fun cancelGeneration() {
                cancelCalls.incrementAndGet()
                released.set(true)
            }
        }
        val task = WorkerBrief("cancel-real-agent", "Prüfe den Kontext", allowedTools = emptySet())
        val reports = ConcurrentLinkedQueue<WorkerReport>()
        val pool = ChiefWorkerPool(1, 0) {
            ChiefJobs.agentJob(task, ChiefJobDeps(
                model = model,
                runner = ToolRunner { toolRuns.incrementAndGet(); "unexpected" },
                policy = ToolPolicy(readOnlyEnabled = true),
                memory = port, evaluator = AgentEvaluator { _, _ -> Evaluation(true, 0.5) },
                experienceStore = InMemoryExperienceStore(),
                approvalProvider = AgentToolApprovalProvider { _, _ -> AgentToolApproval(false) },
                clock = { 100L }))
        }
        try {
            assertTrue(pool.submit(task) { reports += it })
            assertTrue("the real orchestrator should enter model generation", entered.await(2, TimeUnit.SECONDS))
            assertTrue(pool.cancel(task.id))
            assertTrue("native cancellation bridge must stop model generation", stopped.await(2, TimeUnit.SECONDS))
            assertTrue(pool.awaitIdle(2_000))
            assertTrue("cancelGeneration must be called", cancelCalls.get() >= 1)
            assertEquals(0, toolRuns.get())
            assertEquals(WorkerStatus.CANCELLED, reports.single().status)
        } finally {
            pool.shutdown()
        }
    }

}

package at.heinz.deviceai.chief

import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.cognitive.AgentEvaluator
import at.heinz.deviceai.cognitive.AgentModel
import at.heinz.deviceai.cognitive.AgentModelOutput
import at.heinz.deviceai.cognitive.Evaluation
import at.heinz.deviceai.cognitive.InMemoryExperienceStore
import at.heinz.deviceai.cognitive.MemoryRecord
import at.heinz.deviceai.learning.ContinuumLearningCore
import at.heinz.deviceai.learning.ContinuumMemoryLayers
import at.heinz.deviceai.routines.AgentMailbox
import at.heinz.deviceai.routines.AgentMessage
import at.heinz.deviceai.routines.AgentPriority
import at.heinz.deviceai.routines.Routine
import at.heinz.deviceai.routines.RoutineBook
import at.heinz.deviceai.routines.RoutineTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue

class ChiefBrainWiringTest {
    private fun <T> withLayers(block: (ContinuumMemoryLayers) -> T): T {
        val dir = Files.createTempDirectory("chief-wiring").toFile()
        try {
            val clock = longArrayOf(1_000L)
            val core = ContinuumLearningCore(dir) { clock[0] }
            core.confirmOwnerFact("Ich wohne in Enns.")
            clock[0] = 2_000L; core.confirmOwnerFact("Vielleicht ziehe ich nach Barcelona.")
            clock[0] = 3_000L; core.confirmOwnerFact("Ich arbeite bei Fronius.")
            clock[0] = 4_000L; core.revokeOwnerFact("Ich arbeite bei Fronius.")
            return block(ContinuumMemoryLayers(core))
        } finally { dir.deleteRecursively() }
    }

    private fun deps(model: AgentModel, executed: MutableCollection<String>, port: ChiefMemoryPort? = null, observer: (at.heinz.deviceai.cognitive.AgentCognitiveEvent) -> Unit = {}) = ChiefJobDeps(
        model = model, runner = ToolRunner { executed += it.name; "ok" },
        policy = ToolPolicy(readOnlyEnabled = true, destructiveEnabled = true),
        memory = port, evaluator = AgentEvaluator { _, _ -> Evaluation(true, 0.5) },
        experienceStore = InMemoryExperienceStore(),
        approvalProvider = UnattendedApproval, clock = { 10_000L }, eventObserver = observer)

    @Test fun layersPortGivesConfirmedFactsHidesRevokedAndKeepsHypothesesUnconfirmed() = withLayers { layers ->
        val port = LayersMemoryPort(layers)
        val all = port.sharedFacts() + port.search("Barcelona Fronius Enns", 10_000L, 10)
        assertTrue(all.any { it.confirmed && it.text.contains("Enns") })
        assertFalse("revoked fact must never reach the chief", all.any { it.confirmed && it.text.contains("Fronius") })
        assertFalse("hypothesis must never be a confirmed fact", all.any { it.confirmed && it.text.contains("Barcelona") })
    }

    @Test fun agentJobRecallsRealLayersAndUnattendedApprovalBlocksRiskyTool() = withLayers { layers ->
        val seen = ConcurrentLinkedQueue<List<MemoryRecord>>()
        val executed = ConcurrentLinkedQueue<String>()
        val shown = ConcurrentLinkedQueue<String>()
        val lifecycle = TurnLifecycle("t1", minMilestoneGapMillis = 0)
        val model = AgentModel { _, memories, _, _ ->
            seen += memories
            AgentModelOutput("m", "", ToolProposal("delete_file", mapOf("path" to "/sdcard/a")))
        }
        val brief = WorkerBrief("job-wire", "Wo wohne ich? Lösche danach /sdcard/a", allowedTools = setOf("delete_file"))
        val reports = ConcurrentLinkedQueue<WorkerReport>()
        val pool = ChiefWorkerPool(1, 0) { ChiefJobs.agentJob(it, deps(model, executed, LayersMemoryPort(layers), ChiefTurnBridge.observer(lifecycle, { s -> shown += s }))) }
        assertTrue(pool.submit(brief) { reports += it }); assertTrue(pool.awaitIdle(5_000)); pool.shutdown()
        assertFalse("unattended run must never execute a destructive tool", "delete_file" in executed)
        assertTrue(seen.toString(), seen.first().any { it.content.contains("Enns") })
        assertTrue(seen.first().none { it.content.contains("Fronius") })
        assertTrue(reports.single().status != WorkerStatus.DONE || "delete_file" !in executed)
    }

    @Test fun routineRunsAsWorkerJobAndReportsOnlyNews() {
        val now = longArrayOf(0L)
        val book = RoutineBook()
        assertTrue(book.add(Routine("akku.check", "Wie voll ist der Akku?", RoutineTrigger.Every(RoutineBook.MIN_PERIOD_MILLIS, 0L))))
        val mailbox = AgentMailbox()
        val executed = ConcurrentLinkedQueue<String>()
        val pool = ChiefWiring.unattendedPool(deps(AgentModel { _, _, _, _ -> AgentModelOutput("m", "Akku 80 %") }, executed))
        val dispatcher = RoutineDispatcher(book, pool, mailbox, clock = { now[0] })
        assertEquals(1, dispatcher.tick()); assertTrue(pool.awaitIdle(5_000))
        val first = mailbox.drain("chief")
        assertEquals(1, first.size); assertEquals(AgentPriority.INFO, first.single().priority)
        now[0] = 1_000L
        assertEquals("not due before period", 0, dispatcher.tick())
        now[0] = RoutineBook.MIN_PERIOD_MILLIS + 1
        assertEquals(1, dispatcher.tick()); assertTrue(pool.awaitIdle(5_000)); pool.shutdown()
        assertEquals("unchanged result is not reported again", 0, mailbox.pending("chief"))
        assertTrue(dispatcher.inFlight().isEmpty())
    }

    @Test fun blockedRoutineWakesChiefUrgently() {
        val woken = ConcurrentLinkedQueue<AgentMessage>()
        val mailbox = AgentMailbox(wake = { woken += it })
        val book = RoutineBook().apply { add(Routine("putzen", "Räume Downloads auf", RoutineTrigger.OnEvent("laden"))) }
        val pool = ChiefWorkerPool(1, 0) { WorkerJob { throw WorkerBlocked("Freigabe nötig") } }
        val dispatcher = RoutineDispatcher(book, pool, mailbox, clock = { 5L })
        assertEquals(1, dispatcher.onEvent("laden")); assertTrue(pool.awaitIdle(5_000)); pool.shutdown()
        assertEquals(AgentPriority.URGENT, woken.single().priority)
        assertTrue(woken.single().body.contains("Freigabe"))
    }
}

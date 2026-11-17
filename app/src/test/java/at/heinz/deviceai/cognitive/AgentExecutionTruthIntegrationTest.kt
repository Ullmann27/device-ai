package at.heinz.deviceai.cognitive

import at.heinz.deviceai.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/** Controlled planner, real local filesystem probe, existing execution/journal/checkpoint paths. */
class AgentExecutionTruthIntegrationTest {
    private fun planner(proposal: ToolProposal) = AgentModel { _, _, _, observations ->
        if (observations.isEmpty()) AgentModelOutput("controlled-planner", "", proposal)
        else AgentModelOutput("controlled-planner", "Auftrag erfolgreich abgeschlossen.")
    }

    @Test fun deniedOrUnavailableToolsCannotBeReportedAsSuccessfulByAFormOnlyEvaluator() {
        for (tool in listOf("launch_app", "missing_local_tool", "get_storage_info")) {
            var dispatched = 0
            val proposal = ToolProposal(tool, if (tool == "launch_app")
                mapOf("packageOrName" to "org.example.target") else emptyMap())
            val result = BoundedAgentOrchestrator(
                model = planner(proposal), memories = { emptyList() },
                toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner {
                    dispatched++
                    error("LOCAL_TOOL_UNAVAILABLE")
                }),
                evaluator = AgentEvaluator { _, _ -> Evaluation(true, 1.0, "Form-only evaluator") },
                experienceStore = InMemoryExperienceStore(),
                limits = AgentLimits(maxRetries = 1),
                evaluationConfirmsOutcome = false
            ).run("Prüfe den Auftrag mit dem angeforderten Werkzeug.")
            assertNotNull(tool, result.blocker)
            assertFalse(tool, result.response.contains("erfolgreich abgeschlossen"))
            assertFalse(tool, result.taskEvidence!!.outcomeVerified)
            assertTrue(tool, result.experience == null || result.experience.outcome == ExperienceOutcome.FAILURE)
            if (tool != "get_storage_info") assertEquals(tool, 0, dispatched)
            if (tool == "missing_local_tool") assertTrue(result.response, result.response.contains("UNAVAILABLE"))
        }
    }

    @Test fun actualLocalProbeProducesVerifiedEventsAndAPersistentNonReplayingReceipt() {
        val directory = Files.createTempDirectory("continuum-probe-order").toFile()
        try {
            val receiptDirectory = directory.resolve("receipts")
            val executions = AtomicInteger()
            var observed = ""
            val runner = DeviceToolExecutorRunner({ call, callback ->
                check(call.name == "get_storage_info")
                executions.incrementAndGet()
                observed = DeviceStorageProbe.read(directory)
                callback(Result.success(observed))
            }, 1_000)
            val events = mutableListOf<AgentCognitiveEvent>()
            val result = BoundedAgentOrchestrator(
                model = planner(ToolProposal("get_storage_info", emptyMap())), memories = { emptyList() },
                toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true), runner,
                    receiptStore = FileToolExecutionReceiptStore(receiptDirectory)),
                evaluator = AgentEvaluator { _, record ->
                    val valid = record?.status == ToolExecutionStatus.SUCCEEDED &&
                        observed.contains("Gesamt: ${directory.totalSpace} Byte") &&
                        Regex("Für die App nutzbar: [0-9]+ Byte").containsMatchIn(observed) &&
                        observed.contains("Zeitpunkt:")
                    Evaluation(valid, if (valid) 1.0 else 0.0)
                },
                experienceStore = InMemoryExperienceStore(), eventObserver = events::add,
                callIdPrefix = "local-probe"
            ).run("Prüfe den Speicher des App-Datenträgers.")
            assertNull(result.blocker)
            assertEquals(1, executions.get())
            assertTrue(result.taskEvidence!!.outcomeVerified)
            assertTrue(events.map { it.state }.containsAll(listOf(CognitiveWorkState.PLANNING,
                CognitiveWorkState.EXECUTING, CognitiveWorkState.VERIFYING, CognitiveWorkState.COMPLETED)))
            val restored = FileToolExecutionReceiptStore(receiptDirectory)
            val receipt = restored.get("local-probe-1")!!
            assertEquals(ToolReceiptState.SUCCEEDED, receipt.state)
            assertEquals(sha256Hex(observed), receipt.outputSha256)
            val replay = ToolGate(ToolPolicy(readOnlyEnabled = true), runner, receiptStore = restored)
                .execute(ToolProposal("get_storage_info", emptyMap()).invocation("local-probe-1"))
            assertTrue(replay.replayed)
            assertNull(replay.output)
            assertEquals(1, executions.get())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun restartRestoresWorkStateWithoutAutomaticallyReplayingEffects() {
        val directory = Files.createTempDirectory("continuum-order-restart").toFile()
        try {
            var executions = 0
            fun agent() = BoundedAgentOrchestrator(
                model = planner(ToolProposal("get_storage_info", emptyMap())), memories = { emptyList() },
                toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner {
                    executions++
                    DeviceStorageProbe.read(directory)
                }),
                evaluator = AgentEvaluator { _, record -> Evaluation(record?.status == ToolExecutionStatus.SUCCEEDED, 1.0) },
                experienceStore = InMemoryExperienceStore(), checkpointStore = FileAgentCheckpointStore(directory)
            )
            val checkpoint = agent().checkpoint("Prüfe den App-Speicher.", AgentState.GATED_EXECUTION)
            FileAgentCheckpointStore(directory).save(checkpoint)
            val restored = agent().resumeSaved()!!
            assertNotNull(restored.blocker)
            assertEquals(AgentState.GATED_EXECUTION, restored.state)
            assertEquals(0, executions)
            FileAgentCheckpointStore(directory).save(checkpoint.copy(state = AgentState.PLAN))
            assertNull(agent().resumeSaved()!!.blocker)
            assertEquals(1, executions)
            assertNull(FileAgentCheckpointStore(directory).load())
        } finally {
            directory.deleteRecursively()
        }
    }
}

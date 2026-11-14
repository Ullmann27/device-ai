package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolRunner
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LongOwnerTaskAdmissionTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun fullTask(): String {
        val ending = " Korrektur: nicht starten und nichts senden."
        return "x".repeat(LocalChatPromptBudget.MAX_TASK_CHARACTERS - ending.length) + ending
    }

    private fun saved(task: String) =
        AgentCheckpoint(1, task, AgentState.PLAN, 3, 0, 0, listOf(
            AgentAuditEvent(0, AgentState.OBSERVE, "observed"),
            AgentAuditEvent(1, AgentState.RETRIEVE, "retrieved"),
            AgentAuditEvent(2, AgentState.PLAN, "planned")))

    private fun agent(store: AgentCheckpointStore, model: AgentModel) = BoundedAgentOrchestrator(
        model = model,
        memories = { emptyList() },
        toolGate = ToolGate(ToolPolicy(), ToolRunner { error("No tool expected") }),
        evaluator = AgentEvaluator { response, _ -> Evaluation(response == "done", 1.0) },
        experienceStore = InMemoryExperienceStore(),
        checkpointStore = store,
        limits = AgentLimits(maxRetries = 0))

    @Test fun acceptedLongTaskReachesModelAndDiskWithoutLosingTheLastCorrection() {
        val task = fullTask()
        val store = FileAgentCheckpointStore(temporary.newFolder())
        assertTrue(task.length > 8_000)
        assertTrue(LocalChatPromptBudget.acceptsCompleteTask(task))
        assertNotNull(ContinuumTaskClassifier.classify(task))
        var calls = 0
        val result = agent(store, AgentModel { received, _, _, _ ->
            calls++
            assertEquals(task, received)
            assertEquals(task, store.load()?.task)
            assertEquals(AgentState.MODEL, store.load()?.state)
            AgentModelOutput("test-model", "done")
        }).run(task)
        assertEquals(1, calls)
        assertEquals("done", result.response)
        assertNull(result.blocker)
        assertNull(store.load())
    }

    @Test fun longTaskRoundTripsThroughUnchangedCodecAndFreshFileStoreThenResumes() {
        val directory = temporary.newFolder()
        val task = fullTask()
        val checkpoint = saved(task)
        val encoded = AgentCheckpointCodec.encode(checkpoint)
        assertEquals(1, AgentCheckpointCodec.decode(encoded).schemaVersion)
        assertEquals(checkpoint, AgentCheckpointCodec.decode(encoded))
        FileAgentCheckpointStore(directory).save(checkpoint)
        val restored = FileAgentCheckpointStore(directory)
        assertEquals(checkpoint, restored.load())
        var received: String? = null
        val result = agent(restored, AgentModel { request, _, _, _ ->
            received = request
            AgentModelOutput("test-model", "done")
        }).resumeSaved()
        assertEquals(task, received)
        assertEquals("done", result?.response)
        assertNull(result?.blocker)
        assertNull(restored.load())
    }

    @Test fun multibyteTaskFitsTheSameActualUtf8Limit() {
        val ending = " Nicht öffnen."
        val endingBytes = ending.toByteArray(Charsets.UTF_8).size
        val available = LocalChatPromptBudget.MAX_TASK_CHARACTERS - endingBytes
        val task = "🧠".repeat(available / 4) + "x".repeat(available % 4) + ending
        assertEquals(LocalChatPromptBudget.MAX_TASK_CHARACTERS, task.toByteArray(Charsets.UTF_8).size)
        assertTrue(task.length < LocalChatPromptBudget.MAX_TASK_CHARACTERS)
        assertTrue(LocalChatPromptBudget.acceptsCompleteTask(task))
        val checkpoint = saved(task)
        val store = FileAgentCheckpointStore(temporary.newFolder())
        store.save(checkpoint)
        assertEquals(checkpoint, store.load())
        assertEquals(checkpoint, AgentCheckpointCodec.decode(AgentCheckpointCodec.encode(checkpoint)))
        val result = agent(store, AgentModel { received, _, _, _ ->
            assertEquals(task, received)
            AgentModelOutput("test-model", "done")
        }).run(task)
        assertEquals("done", result.response)
    }

    @Test fun invalidTaskCannotEraseAnExistingCheckpointOrInvokeModel() {
        val store = FileAgentCheckpointStore(temporary.newFolder())
        val original = saved("Existing reviewed task.")
        val orchestrator = agent(store, AgentModel { _, _, _, _ -> error("No model expected") })
        for (task in listOf(" ", "x".repeat(LocalChatPromptBudget.MAX_TASK_CHARACTERS + 1),
                "ä".repeat(LocalChatPromptBudget.MAX_TASK_CHARACTERS / 2 + 1))) {
            store.save(original)
            assertFalse(LocalChatPromptBudget.acceptsCompleteTask(task))
            assertThrows(IllegalArgumentException::class.java) { orchestrator.run(task) }
            assertEquals(original, store.load())
            assertThrows(IllegalArgumentException::class.java) { saved(task) }
        }
    }

    @Test fun longTaskStillCannotResumeAcrossAToolExecutionBoundary() {
        val store = FileAgentCheckpointStore(temporary.newFolder())
        val safe = saved(fullTask())
        val checkpoint = safe.copy(state = AgentState.GATED_EXECUTION, steps = 4,
            auditTrail = safe.auditTrail + AgentAuditEvent(3, AgentState.GATED_EXECUTION, "authorized"))
        store.save(checkpoint)
        val result = agent(store, AgentModel { _, _, _, _ -> error("No model expected") }).resumeSaved()
        assertNotNull(result?.blocker)
        assertEquals(AgentState.GATED_EXECUTION, result?.state)
        assertEquals(checkpoint, store.load())
    }
}

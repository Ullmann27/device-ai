package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolExecutionStatus
import at.heinz.deviceai.Risk
import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedAgentTest {
    @Test
    fun happyPathUsesOrderedBoundedStatesAndStoresEvaluatedExperience() {
        val experiences = InMemoryExperienceStore()
        val reputation = ModelReputationStore()
        val agent = BoundedAgentOrchestrator(
            model = AgentModel { _, _, _, _ -> AgentModelOutput("test-model", "done") },
            memories = { emptyList() },
            toolGate = ToolGate(ToolPolicy(), ToolRunner { error("No tool expected") }),
            evaluator = AgentEvaluator { response, _ -> Evaluation(response == "done", 1.0) },
            experienceStore = experiences,
            modelReputation = reputation,
            clock = { 10L }
        )
        val result = agent.run("test request")
        assertEquals(AgentState.RESPOND, result.state)
        assertEquals(
            listOf(
                AgentState.OBSERVE, AgentState.RETRIEVE, AgentState.PLAN, AgentState.MODEL,
                AgentState.VALIDATE, AgentState.EVALUATE, AgentState.STORE_EXPERIENCE, AgentState.RESPOND
            ),
            result.auditTrail.map { it.state }
        )
        assertEquals(1, experiences.all().size)
        assertEquals(ExperienceOutcome.PARTIAL, result.experience?.outcome)
        assertEquals(
            0,
            reputation.get("test-model", ModelCapability.TOOL_USE, "agent").taskCount
        )
    }

    @Test
    fun toolPathUsesGateAndNeverExecutesRawShellProposal() {
        var runs = 0
        val agent = BoundedAgentOrchestrator(
            model = AgentModel { _, _, _, observations ->
                if (observations.isEmpty()) {
                    AgentModelOutput("test-model", "", ToolProposal("get_device_info", emptyMap()))
                } else {
                    assertEquals("device data", observations.last().output)
                    AgentModelOutput("test-model", "device data observed")
                }
            },
            memories = { emptyList() },
            toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { runs++; "device data" }),
            evaluator = AgentEvaluator { response, record ->
                Evaluation(response == "device data observed" && record?.status == ToolExecutionStatus.SUCCEEDED, 1.0)
            },
            experienceStore = InMemoryExperienceStore()
        )
        val result = agent.run("inspect device")
        assertEquals(1, runs)
        assertTrue(result.auditTrail.any { it.state == AgentState.GATED_EXECUTION })

        var shellRuns = 0
        val shellAgent = BoundedAgentOrchestrator(
            model = AgentModel { _, _, _, _ ->
                AgentModelOutput("test-model", "response", ToolProposal("run_shell", mapOf("command" to "id")))
            },
            memories = { emptyList() },
            toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { shellRuns++; "unsafe" }),
            evaluator = AgentEvaluator { _, record -> Evaluation(record?.status == ToolExecutionStatus.SUCCEEDED, 0.0, "rejected") },
            experienceStore = InMemoryExperienceStore(),
            limits = AgentLimits(maxRetries = 0)
        )
        val rejected = shellAgent.run("test")
        assertEquals(0, shellRuns)
        assertNotNull(rejected.blocker)
    }

    @Test
    fun reflectionIsOneBoundedRepairAndExhaustionReturnsBlocker() {
        var requests = 0
        var reflected: AgentReflection? = null
        val agent = BoundedAgentOrchestrator(
            model = AgentModel { _, _, reflection, _ ->
                requests++
                reflected = reflection
                if (requests == 1) AgentModelOutput("model", "bad", ToolProposal("unknown", emptyMap()))
                else AgentModelOutput("model", "repaired")
            },
            memories = { emptyList() },
            toolGate = ToolGate(ToolPolicy(), ToolRunner { "unused" }),
            evaluator = AgentEvaluator { response, _ -> Evaluation(response == "repaired", 0.8, "response evaluated") },
            experienceStore = InMemoryExperienceStore()
        )
        val result = agent.run("repair")
        assertEquals(2, requests)
        assertNotNull(reflected)
        assertNotNull(reflected?.failure)
        assertNotNull(reflected?.evidence)
        assertNotNull(reflected?.correction)
        assertEquals(1, result.experience?.retries)
        assertNotNull(result.blocker)
        assertTrue(result.response.contains("UNAVAILABLE"))

        val blocked = BoundedAgentOrchestrator(
            model = AgentModel { _, _, _, _ -> AgentModelOutput("model", "not accepted") },
            memories = { emptyList() },
            toolGate = ToolGate(ToolPolicy(), ToolRunner { "" }),
            evaluator = AgentEvaluator { _, _ -> Evaluation(false, 0.1, "not accepted by evaluator") },
            experienceStore = InMemoryExperienceStore(),
            limits = AgentLimits(maxRetries = 2)
        ).run("bounded")
        assertNotNull(blocked.blocker)
        assertEquals(2, blocked.experience?.retries)
    }

    @Test
    fun modelCannotSelfApproveConfirmToolsAndExplicitApprovalIsRequired() {
        val proposal = ToolProposal("stop_app", mapOf("packageOrName" to "com.example.app"))
        var unapprovedExecutions = 0
        val denied = BoundedAgentOrchestrator(
            model = AgentModel { _, _, _, observations ->
                if (observations.isEmpty()) AgentModelOutput("model", "attempt", proposal)
                else AgentModelOutput("model", "blocked")
            },
            memories = { emptyList() },
            toolGate = ToolGate(ToolPolicy(), ToolRunner { unapprovedExecutions++; "stopped" }),
            evaluator = AgentEvaluator { _, record -> Evaluation(record?.status == ToolExecutionStatus.SUCCEEDED, 0.0) },
            experienceStore = InMemoryExperienceStore(),
            limits = AgentLimits(maxRetries = 0)
        ).run("stop an app")
        assertEquals(0, unapprovedExecutions)
        assertNotNull(denied.blocker)

        var approvedExecutions = 0
        val approved = BoundedAgentOrchestrator(
            model = AgentModel { _, _, _, observations ->
                if (observations.isEmpty()) AgentModelOutput("model", "attempt", proposal)
                else AgentModelOutput("model", observations.last().output.orEmpty())
            },
            memories = { emptyList() },
            toolGate = ToolGate(ToolPolicy(), ToolRunner { approvedExecutions++; "confirmed result" }),
            evaluator = AgentEvaluator { response, record ->
                Evaluation(response == "confirmed result" && record?.status == ToolExecutionStatus.SUCCEEDED, 1.0)
            },
            experienceStore = InMemoryExperienceStore(),
            approvalProvider = { _, risk ->
                assertEquals(Risk.CONFIRM, risk)
                AgentToolApproval(userConfirmed = true)
            }
        ).run("stop an app")
        assertEquals(1, approvedExecutions)
        assertNull(approved.blocker)
    }

    @Test
    fun hardStepLimitStopsBeforeModelAndDoesNotStoreUnevaluatedExperience() {
        var modelCalls = 0
        val experiences = InMemoryExperienceStore()
        val result = BoundedAgentOrchestrator(
            model = AgentModel { _, _, _, _ -> modelCalls++; AgentModelOutput("model", "unused") },
            memories = { emptyList() },
            toolGate = ToolGate(ToolPolicy(), ToolRunner { "" }),
            evaluator = AgentEvaluator { _, _ -> Evaluation(true, 1.0) },
            experienceStore = experiences,
            limits = AgentLimits(maxSteps = 1)
        ).run("step bound")
        assertEquals(0, modelCalls)
        assertEquals(0, experiences.all().size)
        assertNotNull(result.blocker)
    }

    @Test
    fun checkpointCodecIsAllowlistedAndResumeContinuesFromAValidatedState() {
        val checkpoint = AgentCheckpoint(
            schemaVersion = 1,
            task = "resume me",
            state = AgentState.PLAN,
            steps = 3,
            toolCalls = 0,
            retries = 0,
            auditTrail = listOf(
                AgentAuditEvent(0, AgentState.OBSERVE, "observed"),
                AgentAuditEvent(1, AgentState.RETRIEVE, "retrieved"),
                AgentAuditEvent(2, AgentState.PLAN, "planned")
            )
        )
        val encoded = AgentCheckpointCodec.encode(checkpoint)
        assertEquals(checkpoint, AgentCheckpointCodec.decode(encoded))
        val badSchema = encoded.copyOf().apply {
            val schemaOffset = 2 + "DEVICE_AI_AGENT_CHECKPOINT".length
            this[schemaOffset + 3] = 2
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            AgentCheckpointCodec.decode(badSchema)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            AgentCheckpointCodec.decode(byteArrayOf(0))
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            checkpoint.copy(state = AgentState.GATED_EXECUTION).let {
                BoundedAgentOrchestrator(
                    AgentModel { _, _, _, _ -> AgentModelOutput("m", "done") },
                    { emptyList() },
                    ToolGate(ToolPolicy(), ToolRunner { "" }),
                    AgentEvaluator { _, _ -> Evaluation(true, 1.0) },
                    InMemoryExperienceStore()
                ).resume(it)
            }
        }
        val resumed = BoundedAgentOrchestrator(
            AgentModel { _, _, _, _ -> AgentModelOutput("m", "done") },
            { emptyList() },
            ToolGate(ToolPolicy(), ToolRunner { "" }),
            AgentEvaluator { _, _ -> Evaluation(true, 1.0) },
            InMemoryExperienceStore()
        ).resume(checkpoint)
        assertEquals(AgentState.RESPOND, resumed.state)
        assertEquals(3, resumed.auditTrail.take(3).size)
    }
}

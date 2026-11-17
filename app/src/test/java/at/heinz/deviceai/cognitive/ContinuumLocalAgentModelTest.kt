package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.ToolExecutionStatus
import at.heinz.deviceai.localmodel.ImportedLocalModel
import at.heinz.deviceai.localmodel.LocalInferenceCallback
import at.heinz.deviceai.localmodel.LocalInferenceEvent
import at.heinz.deviceai.localmodel.LocalInferenceMetrics
import at.heinz.deviceai.localmodel.LocalModelRuntime
import at.heinz.deviceai.localmodel.LocalModelState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumLocalAgentModelTest {
    private val now = 100_000L

    private class FakeRuntime(
        private val reply: (String, LocalInferenceCallback) -> Unit
    ) : LocalModelRuntime {
        override val state = LocalModelState.READY
        override val loadedModel: ImportedLocalModel? = null
        val prompts = mutableListOf<String>()
        var cancelled = false

        override fun load(id: String, version: String,
            callback: (Result<ImportedLocalModel>) -> Unit) = error("unused")
        override fun unload(callback: (Result<Unit>) -> Unit) = callback(Result.success(Unit))
        override fun stream(prompt: String, callback: LocalInferenceCallback) {
            prompts += prompt
            reply(prompt, callback)
        }
        override fun cancel() { cancelled = true }
    }

    private fun completed() = LocalInferenceEvent.Completed(
        LocalInferenceMetrics(0, 0, 1, null, null, null, 1)
    )

    private fun input() : AiContextInput {
        val genome = CognitiveGenome("continuum-m8-bridge")
        val identity = IdentityState.initial(genome)
        val seed = ContinuumSeed(
            instanceId = ContinuumInstanceId(identity.identityId),
            governanceVersion = GovernanceVersion(1),
            stateFormatVersion = StateFormatVersion(1),
            memorySchemaVersion = 1,
            skillSchemaVersion = 1,
            modelAdapterAbiVersion = ModelAdapterAbiVersion(1),
            ownerIdentityReference = "owner:test",
            authorizedNodeIds = listOf(ComputeNodeId("fold7")),
            currentStateRoot = ContinuumObjectId.of(
                ContinuumObjectType.STATE_MANIFEST,
                "local-bridge-root".toByteArray()
            ),
            createdAtMillis = now
        )
        return AiContextInput(seed, genome, identity, "OLD TEMPLATE TASK")
    }

    private fun model(rt: FakeRuntime, maxPassMillis: Long = 1_000) =
        ContinuumLocalAgentModel(
            runtime = rt,
            compiler = AiContextCompiler(
                AiContextPolicy(maxBytes = 14_000), nowMillis = { now }
            ),
            trustedContext = { input() },
            maxPassMillis = maxPassMillis,
            modelIdProvider = { "test-local@1" }
        )

    private fun memory(
        id: String,
        content: String,
        state: ValidationState = ValidationState.UNVERIFIED,
        privacy: PrivacyClass = PrivacyClass.PRIVATE
    ) = MemoryRecord(
        id = id,
        type = MemoryType.EPISODIC,
        content = content,
        provenance = ProvenanceType.MODEL_GENERATED,
        confidence = 0.5,
        createdAt = now,
        updatedAt = now,
        privacyClass = privacy,
        validationState = state
    )

    @Test fun compilesTrustedIdentityAndCurrentTaskBeforeNativeStreaming() {
        val rt = FakeRuntime { _, cb ->
            cb.onEvent(LocalInferenceEvent.TextDelta("device "))
            cb.onEvent(LocalInferenceEvent.TextDelta("ready"))
            cb.onEvent(completed())
        }
        val result = model(rt).generate(
            "Inspect battery health",
            listOf(memory("valid", "battery health is nominal")),
            null,
            emptyList()
        )
        assertEquals("device ready", result.response)
        assertEquals("test-local@1", result.modelId)
        assertEquals(null, result.proposal)
        val prompt = rt.prompts.single()
        assertTrue(prompt.contains("CONTINUUM IDENTITY / GOVERNANCE"))
        assertTrue(prompt.contains("Inspect battery health"))
        assertTrue(prompt.contains("battery health is nominal"))
        assertFalse(prompt.contains("OLD TEMPLATE TASK"))
    }

    @Test fun privacyAndTemporalValidationApplyBeforeAgentInference() {
        val rt = FakeRuntime { _, cb ->
            cb.onEvent(LocalInferenceEvent.TextDelta("read only"))
            cb.onEvent(completed())
        }
        model(rt).generate("battery health", listOf(
            memory("visible", "battery safe"),
            memory("expired", "battery obsolete", ValidationState.STALE),
            memory("contradicted", "battery contradicted", ValidationState.CONTRADICTED),
            memory("sensitive", "password secret sensitive-data",
                privacy = PrivacyClass.SENSITIVE)
        ), null, emptyList())
        val prompt = rt.prompts.single()
        assertTrue(prompt.contains("battery safe"))
        assertFalse(prompt.contains("battery obsolete"))
        assertFalse(prompt.contains("battery contradicted"))
        assertFalse(prompt.contains("sensitive-data"))
    }

    @Test fun streamedToolProposalPassesThroughTheExistingToolGateAndReturnsEvidence() {
        var modelTurns = 0
        var toolRuns = 0
        val rt = FakeRuntime { prompt, cb ->
            modelTurns++
            if (modelTurns == 1) {
                cb.onEvent(LocalInferenceEvent.ToolProposal(
                    ToolProposal("get_device_info", emptyMap())
                ))
            } else {
                assertTrue(prompt.contains("get_device_info"))
                assertTrue(prompt.contains("device-data"))
                cb.onEvent(LocalInferenceEvent.TextDelta("Observed device-data"))
            }
            cb.onEvent(completed())
        }
        val agent = BoundedAgentOrchestrator(
            model = model(rt),
            memories = { emptyList() },
            toolGate = ToolGate(
                ToolPolicy(readOnlyEnabled = true),
                ToolRunner { toolRuns++; "device-data" }
            ),
            evaluator = AgentEvaluator { response, record ->
                Evaluation(response == "Observed device-data" &&
                    record?.status == ToolExecutionStatus.SUCCEEDED, 0.9)
            },
            experienceStore = InMemoryExperienceStore(),
            callIdPrefix = "bridge-proof"
        )
        val outcome = agent.run("Inspect device")
        assertEquals(2, modelTurns)
        assertEquals(1, toolRuns)
        assertEquals(ExperienceOutcome.SUCCESS, outcome.experience?.outcome)
        assertTrue(outcome.experience!!.sourceIds.isNotEmpty())
        assertTrue(outcome.blocker == null)
    }

    @Test fun deniedToolsNeverBecomeVerifiedSuccessFromPermissiveEvaluator() {
        val rt = FakeRuntime { prompt, cb ->
            if (prompt.contains("Observation 1:")) {
                cb.onEvent(LocalInferenceEvent.TextDelta("claimed success"))
            } else {
                cb.onEvent(LocalInferenceEvent.ToolProposal(
                    ToolProposal("get_device_info", emptyMap())
                ))
            }
            cb.onEvent(completed())
        }
        val agent = BoundedAgentOrchestrator(
            model = model(rt),
            memories = { emptyList() },
            toolGate = ToolGate(ToolPolicy(readOnlyEnabled = false),
                ToolRunner { error("must not execute") }),
            evaluator = AgentEvaluator { _, _ -> Evaluation(true, 1.0) },
            experienceStore = InMemoryExperienceStore(),
            callIdPrefix = "bridge-deny"
        )
        val result = agent.run("Inspect device")
        assertEquals(ExperienceOutcome.FAILURE, result.experience?.outcome)
        assertTrue(result.blocker != null)
        assertFalse(result.response.contains("claimed success"))
        assertTrue(result.experience!!.sourceIds.isEmpty())
    }

    @Test fun rejectedToolProposalAndOutputOverflowFailClosed() {
        val denied = FakeRuntime { _, cb ->
            cb.onEvent(LocalInferenceEvent.ToolProposalRejected(
                "delete_file", "policy rejected"
            ))
        }
        assertThrows(SecurityException::class.java) {
            model(denied).generate("Inspect device", emptyList(), null, emptyList())
        }
        assertTrue(denied.cancelled)

        val overflow = FakeRuntime { _, cb ->
            cb.onEvent(LocalInferenceEvent.TextDelta("x".repeat(8_001)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            model(overflow).generate("Inspect device", emptyList(), null, emptyList())
        }
        assertTrue(overflow.cancelled)
    }

    @Test fun timeoutIsBoundedAndCancelsTheNativeRuntime() {
        val rt = FakeRuntime { _, _ -> }
        assertThrows(IllegalStateException::class.java) {
            model(rt, maxPassMillis = 35).generate("Inspect device",
                emptyList(), null, emptyList())
        }
        assertTrue(rt.cancelled)
    }

    @Test fun agentCancelPropagatesToTheNativeRuntimeWithoutToolExecution() {
        val rt = FakeRuntime { _, cb ->
            cb.onEvent(LocalInferenceEvent.TextDelta("pending"))
        }
        val agent = BoundedAgentOrchestrator(
            model = model(rt),
            memories = { emptyList() },
            toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true),
                ToolRunner { error("must not execute") }),
            evaluator = AgentEvaluator { _, _ -> Evaluation(true, 1.0) },
            experienceStore = InMemoryExperienceStore()
        )
        agent.cancel()
        assertTrue(rt.cancelled)
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            agent.run("Inspect device")
        }
        assertTrue(rt.prompts.isEmpty())
    }

    @Test fun laterCallbacksCannotOverwriteCompletedModelResponse() {
        lateinit var callback: LocalInferenceCallback
        val rt = FakeRuntime { _, cb ->
            callback = cb
            cb.onEvent(LocalInferenceEvent.TextDelta("first"))
            cb.onEvent(completed())
        }
        val result = model(rt).generate("Inspect device",
            emptyList(), null, emptyList())
        callback.onEvent(LocalInferenceEvent.TextDelta("malicious late text"))
        assertEquals("first", result.response)
    }
}

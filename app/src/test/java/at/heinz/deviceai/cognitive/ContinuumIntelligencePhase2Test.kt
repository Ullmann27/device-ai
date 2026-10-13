package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumIntelligencePhase2Test {
    @Test
    fun difficultRequestUsesMultipleDataOnlyReasoningPasses() {
        val roles = mutableListOf<ReasoningPassRole>()
        val coordinator = ContinuumIntelligenceCoordinator(
            adapter = ContinuumReasoningAdapter { request ->
                roles += request.role
                ReasoningPassResult(
                    modelId = "fake-local",
                    text = when (request.role) {
                        ReasoningPassRole.CANDIDATE -> "candidate"
                        ReasoningPassRole.CRITIC -> "critique"
                        ReasoningPassRole.VERIFIER -> "verified"
                        ReasoningPassRole.JUDGE -> "final"
                    },
                    latencyMillis = 1
                )
            },
            nowMillis = { 1_000L },
            nowNanos = { 1_000L }
        )

        val result = coordinator.run(
            ContinuumIntelligenceRequest(
                taskId = "task-1",
                userRequest = "Erstelle einen komplexen Plan mit mehreren Bedingungen und Gegenargumenten.",
                compiledContext = "verified identity and memory context",
                requestedStrategy = ReasoningStrategy.MULTI_CANDIDATE
            )
        )

        assertTrue(result.deliberation.modelCalls > 1)
        assertTrue(ReasoningPassRole.CRITIC in roles)
        assertTrue(ReasoningPassRole.JUDGE in roles)
        assertEquals(setOf("fake-local"), result.modelIds)
    }

    @Test
    fun internalToolProposalAttemptFailsClosed() {
        val coordinator = ContinuumIntelligenceCoordinator(
            adapter = ContinuumReasoningAdapter {
                ReasoningPassResult("fake-local", "candidate", 1, attemptedToolProposals = 1)
            },
            nowMillis = { 1_000L },
            nowNanos = { 1_000L }
        )
        assertThrows(IllegalArgumentException::class.java) {
            coordinator.run(
                ContinuumIntelligenceRequest(
                    "task-2",
                    "Give me a short answer.",
                    "identity context"
                )
            )
        }
    }

    @Test
    fun proofCarryingMutationRequiresPolicyAndMaintainsSignatureChain() {
        val signer = TestSigner()
        val ledger = MemoryMutationLedger(signer)
        val policy = MemoryWritePolicy()
        val first = memory("m1", "old", 100)
        val second = first.copy(content = "corrected", updatedAt = 200)
        val decision = policy.decide(
            MemoryWriteContext(
                memoryType = MemoryType.SEMANTIC,
                provenance = ProvenanceType.USER_PROVIDED,
                confidence = 0.9,
                explicitlyRequested = true,
                containsPersonalInformation = true
            )
        )

        val mutation1 = ProofCarryingMemoryMutation.create(
            mutationId = "mut-1",
            before = null,
            after = first,
            reason = MemoryMutationReason.USER_CORRECTION,
            evidenceReferences = emptyList(),
            contradictionReferences = emptyList(),
            previousMutationHash = null,
            decision = decision,
            signer = signer,
            createdAtMillis = 100
        )
        ledger.append(mutation1)

        val mutation2 = ProofCarryingMemoryMutation.create(
            mutationId = "mut-2",
            before = first,
            after = second,
            reason = MemoryMutationReason.NEW_EVIDENCE,
            evidenceReferences = listOf("source:1"),
            contradictionReferences = emptyList(),
            previousMutationHash = ledger.lastHash(),
            decision = decision,
            signer = signer,
            createdAtMillis = 200
        )
        ledger.append(mutation2)

        assertTrue(ledger.verifyChain())
        assertEquals(memoryRecordHash(first), mutation2.payload.beforeHash)
        assertEquals(memoryRecordHash(second), mutation2.payload.afterHash)
    }

    @Test
    fun rejectedMemoryPolicyCannotProduceDurableMutation() {
        val record = memory("m2", "model guess", 100).copy(provenance = ProvenanceType.MODEL_GENERATED)
        val rejected = MemoryWritePolicy(minimumConfidence = 0.9).decide(
            MemoryWriteContext(
                memoryType = MemoryType.SEMANTIC,
                provenance = ProvenanceType.MODEL_GENERATED,
                confidence = 0.2,
                durableKnowledge = true
            )
        )
        assertFalse(rejected.shouldWrite)
        assertThrows(IllegalArgumentException::class.java) {
            ProofCarryingMemoryMutation.create(
                "mut-rejected", null, record, MemoryMutationReason.CONSOLIDATION,
                emptyList(), emptyList(), null, rejected, TestSigner(), 100
            )
        }
    }

    @Test
    fun repeatedEvaluatedReadOnlyPlanBecomesSkillCandidateOnly() {
        val experiences = (1..3).map { index ->
            experience(
                id = "e" + index,
                tools = listOf("get_device_info", "get_battery_info")
            )
        }
        val candidates = ExperienceCompiler().compile(experiences)

        assertEquals(1, candidates.size)
        assertTrue(candidates.single().eligibleForOwnerReview)
        assertTrue(candidates.single().requiresOwnerApproval)
        assertEquals(3, candidates.single().occurrenceCount)
    }

    private fun memory(id: String, content: String, time: Long) = MemoryRecord(
        id = id,
        type = MemoryType.SEMANTIC,
        content = content,
        provenance = ProvenanceType.USER_PROVIDED,
        confidence = 0.9,
        createdAt = time,
        updatedAt = time,
        privacyClass = PrivacyClass.PRIVATE
    )

    private fun experience(id: String, tools: List<String>) = Experience(
        id = id,
        taskType = "device-health",
        requestSummary = "check device health",
        toolIds = tools,
        resultSummary = "ok",
        evaluatorScores = mapOf("quality" to 0.95),
        confidenceBefore = 0.5,
        confidenceAfter = 0.9,
        outcome = ExperienceOutcome.SUCCESS,
        startedAt = 100,
        completedAt = 200,
        evaluation = Evaluation(true, 0.95, "good")
    )

    private class TestSigner : IdentitySigner {
        private val secret = "phase2-test".toByteArray()
        override fun sign(payload: ByteArray): ByteArray = sha256Bytes(secret + payload)
        override fun verify(payload: ByteArray, signature: ByteArray): Boolean =
            java.security.MessageDigest.isEqual(sign(payload), signature)
    }
}

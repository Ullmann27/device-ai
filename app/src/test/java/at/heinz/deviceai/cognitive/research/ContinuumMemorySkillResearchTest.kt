package at.heinz.deviceai.cognitive.research

import at.heinz.deviceai.cognitive.*
import org.junit.Assert.*
import org.junit.Test

class ContinuumMemorySkillResearchTest {
    private val scope = MemorySkillScope("synthetic-owner", "memory-1", "model-1", "policy-1", "source-1")
    private fun episode(id: String, success: Boolean = true, confidence: Double = 0.9,
        tool: String = "get_device_info") = Experience(id, "diagnostic", "Inspect device health",
        sourceIds = listOf("synthetic-source-$id"), toolIds = listOf(tool),
        failures = if (success) emptyList() else listOf("verification failed"), confidenceBefore = confidence,
        outcome = if (success) ExperienceOutcome.SUCCESS else ExperienceOutcome.FAILURE,
        startedAt = 1, completedAt = 2, evaluation = Evaluation(success, if (success) 0.95 else 0.1))
    private fun receipts(episodes: List<Experience>) = episodes.associate { e -> e.id to MemorySkillEvidence(
        scope, ContinuumMemorySkillResearch.digest(e), "receipt-${e.id}", e.sourceIds,
        ProvenanceType.DIRECT_OBSERVATION, PrivacyClass.PUBLIC, ValidationState.VERIFIED, true) }
    private fun gate(policy: MemorySkillResearchPolicy = MemorySkillResearchPolicy(),
        verifier: IndependentExperienceVerifier = IndependentExperienceVerifier { true }) = ContinuumMemorySkillResearch(policy, verifier)
    private fun successes(n: Int = 3) = (1..n).map { episode("e$it") }

    @Test fun disabledDoesNotConsultVerifier() {
        val e = successes()
        assertEquals(MemorySkillReviewStatus.DISABLED,
            gate(verifier = IndependentExperienceVerifier { error("must not run") }).review(e, scope, receipts(e)).status)
    }

    @Test fun defaultVerifierRejectsClaimedSuccess() {
        val e = successes()
        assertTrue(ContinuumMemorySkillResearch().review(e, scope, receipts(e), true).candidates.isEmpty())
    }

    @Test fun repeatedVerifiedSuccessPreservesProofAndApproval() {
        val e = successes()
        val report = gate().review(e, scope, receipts(e), true)
        val c = report.candidates.single()
        assertEquals(e.map { it.id }, c.candidate.sourceExperienceIds)
        assertEquals(e.map { receipts(e).getValue(it.id) }, c.evidence)
        assertEquals(scope, c.scope)
        assertTrue(c.candidate.requiresOwnerApproval)
        assertTrue(c.candidate.eligibleForOwnerReview)
        assertTrue(report.prioritized.isEmpty())
    }

    @Test fun surpriseDoesNotChangeCompilationDenominator() {
        val e = successes(4) + episode("failure", false, 0.1)
        val report = gate().review(e, scope, receipts(e), true)
        assertEquals(listOf("failure"), report.prioritized.map { it.experienceId })
        val c = report.candidates.single()
        assertEquals(5, c.candidate.occurrenceCount)
        assertEquals(0.8, c.candidate.successRate, 0.0)
        assertEquals(listOf("failure"), c.negativeExperienceIds)
        assertEquals(5, c.evidence.size)
    }

    @Test fun failureCanBlockOtherwiseSuccessfulSequence() {
        val e = successes() + episode("failure", false)
        assertTrue(gate().review(e, scope, receipts(e), true).candidates.isEmpty())
    }

    @Test fun unresolvedNegativeCannotDisappearDuringVerification() {
        val e = successes() + episode("failure", false)
        val verifier = IndependentExperienceVerifier { it.id != "failure" }
        assertEquals(1, VerifiedSkillReview(independentVerifier = verifier).candidates(e).size)
        val report = gate(verifier = verifier)
            .review(e, scope, receipts(e), true)
        assertEquals(3, report.reviewedCount)
        assertTrue(report.candidates.isEmpty())
    }

    @Test fun surpriseAblationChangesRecallOnly() {
        val e = successes() + episode("surprise", confidence = 0.1, tool = "get_storage_info")
        val on = gate().review(e, scope, receipts(e), true)
        val off = gate(MemorySkillResearchPolicy(surpriseEnabled = false)).review(e, scope, receipts(e), true)
        assertEquals(listOf("surprise"), on.prioritized.map { it.experienceId })
        assertEquals(4, off.prioritized.size)
        assertEquals(on.candidates, off.candidates)
    }

    @Test fun everyRevisionAndOwnerBoundaryInvalidates() {
        val e = successes()
        val scopes = listOf(scope.copy(ownerId = "other"), scope.copy(memoryRevision = "2"),
            scope.copy(modelRevision = "2"), scope.copy(policyRevision = "2"), scope.copy(sourceRevision = "2"))
        scopes.forEach { changed ->
            val report = gate().review(e, changed, receipts(e), true)
            assertEquals(0, report.reviewedCount)
            assertTrue(report.candidates.isEmpty())
            assertTrue(report.prioritized.isEmpty())
        }
    }

    @Test fun consentAndPrivacyRejectBeforeVerifier() {
        val e = successes()
        val receipt = receipts(e)
        val changes = listOf<(MemorySkillEvidence) -> MemorySkillEvidence>(
            { it.copy(consentForResearch = false) }, { it.copy(privacy = PrivacyClass.SENSITIVE) },
            { it.copy(privacy = PrivacyClass.PRIVATE) }, { it.copy(privacy = PrivacyClass.DEVICE_LOCAL_ONLY) })
        changes.forEach { change ->
            val report = gate(verifier = IndependentExperienceVerifier { error("privacy leak") })
                .review(e, scope, receipt.mapValues { change(it.value) }, true)
            assertEquals(MemorySkillReviewStatus.REVIEWED, report.status)
            assertEquals(3, report.rejectedCount)
        }
    }

    @Test fun staleContradictedAndModelEvidenceCannotPromote() {
        val e = successes()
        for (state in ValidationState.entries.filter { it != ValidationState.VERIFIED }) {
            assertTrue(gate().review(e, scope, receipts(e).mapValues { it.value.copy(validation = state) }, true).candidates.isEmpty())
        }
        for (provenance in listOf(ProvenanceType.MODEL_GENERATED, ProvenanceType.INFERENCE, ProvenanceType.UNVERIFIED)) {
            assertTrue(gate().review(e, scope, receipts(e).mapValues { it.value.copy(provenance = provenance) }, true).candidates.isEmpty())
        }
    }

    @Test fun contentChangeInvalidatesReceipt() {
        val original = successes()
        val changed = original.map { it.copy(resultSummary = "changed") }
        assertEquals(0, gate().review(changed, scope, receipts(original), true).reviewedCount)
    }

    @Test fun digestUsesUnambiguousBoundariesAndAllEvidenceFields() {
        val e = episode("e")
        val variants = listOf(e.copy(memoryIds = listOf("a", "b")), e.copy(modelIds = listOf("model")),
            e.copy(sourceIds = listOf("source")), e.copy(toolIds = listOf("get_storage_info")),
            e.copy(planSummary = "plan"), e.copy(resultSummary = "result"), e.copy(failures = listOf("failure")),
            e.copy(retries = 1), e.copy(evaluatorScores = mapOf("v" to 0.7)), e.copy(feedback = ""),
            e.copy(confidenceBefore = 0.2), e.copy(confidenceAfter = 0.2), e.copy(completedAt = 3),
            e.copy(evaluation = e.evaluation.copy(notes = "different")))
        variants.forEach { assertNotEquals(ContinuumMemorySkillResearch.digest(e), ContinuumMemorySkillResearch.digest(it)) }
        assertNotEquals(ContinuumMemorySkillResearch.digest(e.copy(memoryIds = listOf("a,b"))),
            ContinuumMemorySkillResearch.digest(e.copy(memoryIds = listOf("a", "b"))))
    }

    @Test fun verifierExceptionFailsWholeBatchClosed() {
        val e = successes()
        val report = gate(verifier = IndependentExperienceVerifier { if (it.id == "e2") error("offline") else true })
            .review(e, scope, receipts(e), true)
        assertEquals(MemorySkillReviewStatus.VERIFIER_UNAVAILABLE, report.status)
        assertTrue(report.candidates.isEmpty())
    }

    @Test fun replaysAndConflictingIdentitiesNeverAccumulate() {
        val e = episode("e")
        assertTrue(gate().review(List(3) { e }, scope, receipts(listOf(e)), true).candidates.isEmpty())
        assertThrows(SecurityException::class.java) {
            gate().review(listOf(e, e.copy(resultSummary = "tamper")), scope, receipts(listOf(e)), true)
        }
    }

    @Test fun oneReceiptCannotCreateThreeIndependentAttempts() {
        val e = successes()
        assertThrows(SecurityException::class.java) {
            gate().review(e, scope, receipts(e).mapValues { it.value.copy(receiptId = "same") }, true)
        }
    }

    @Test fun destructiveSequenceRemainsBlocked() {
        val e = (1..3).map { episode("e$it", tool = "delete_file") }
        val c = gate().review(e, scope, receipts(e), true).candidates.single().candidate
        assertFalse(c.eligibleForOwnerReview)
        assertTrue(c.requiresOwnerApproval)
    }

    @Test fun countAndTextLimitsFailClosedBeforeVerifier() {
        val e = successes(4)
        assertEquals(MemorySkillReviewStatus.LIMIT_REJECTED,
            gate(MemorySkillResearchPolicy(maxExperiences = 3)).review(e, scope, receipts(e), true).status)
        val large = listOf(episode("large").copy(requestSummary = "x".repeat(1_000_001)))
        assertEquals(MemorySkillReviewStatus.LIMIT_REJECTED, gate().review(large, scope, emptyMap(), true).status)
    }

    @Test fun callerCannotMutateReviewedProvenance() {
        val e = successes()
        val refs = mutableListOf("proof")
        val report = gate().review(e, scope, receipts(e).mapValues { it.value.copy(sourceReferences = refs) }, true)
        refs.clear()
        val c = report.candidates.single()
        assertEquals(listOf("proof"), c.evidence.first().sourceReferences)
        assertThrows(UnsupportedOperationException::class.java) { (c.candidate.sourceExperienceIds as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (c.evidence as MutableList).clear() }
    }

    @Test fun collectionBoundsPreventLargeEmptyMetadataLists() {
        val e = listOf(episode("large").copy(memoryIds = List(513) { "" }))
        assertEquals(MemorySkillReviewStatus.LIMIT_REJECTED, gate().review(e, scope, emptyMap(), true).status)
    }

    @Test fun deterministicOrderingAndRecallBudget() {
        val e = successes(6).map { it.copy(confidenceBefore = 0.1) }
        val g = gate(MemorySkillResearchPolicy(maxRecall = 2))
        val a = g.review(e, scope, receipts(e), true)
        val b = g.review(e.reversed(), scope, receipts(e), true)
        assertEquals(a, b)
        assertEquals(2, a.prioritized.size)
    }
}

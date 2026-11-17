package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedSkillReviewTest {
    private fun run(
        id: String,
        success: Boolean = true,
        tools: List<String> = listOf("get_device_info")
    ) = Experience(
        id = id,
        taskType = "DEVICE-INFO",
        requestSummary = "Review device health",
        toolIds = tools,
        startedAt = 1_000,
        completedAt = 2_000,
        outcome = if (success) ExperienceOutcome.SUCCESS else ExperienceOutcome.FAILURE,
        evaluation = Evaluation(success, if (success) 0.95 else 0.1)
    )

    @Test fun defaultNeverPromotesSelfEvaluatedSuccesses() {
        val history = (1..3).map { run("case-$it") }
        assertTrue(ExperienceCompiler().compile(history).single().eligibleForOwnerReview)
        assertTrue(VerifiedSkillReview().candidates(history).isEmpty())
    }

    @Test fun independentlyCheckedRunsBecomeOnlyOwnerReviewCandidates() {
        val history = (1..3).map { run("case-$it") }
        val approvedIds = setOf("case-1", "case-2", "case-3")
        val gate = VerifiedSkillReview(
            independentVerifier = IndependentExperienceVerifier {
                it.id in approvedIds
            }
        )
        val candidate = gate.candidates(history).single()
        assertEquals(3, candidate.occurrenceCount)
        assertEquals(history.map { it.id }.sorted(), candidate.sourceExperienceIds)
        assertTrue(candidate.requiresOwnerApproval)
        assertTrue(candidate.eligibleForOwnerReview)
    }

    @Test fun onlyTwoIndependentReceiptsDoNotMeetLearningThreshold() {
        val history = (1..3).map { run("case-$it") }
        val gate = VerifiedSkillReview(
            independentVerifier = IndependentExperienceVerifier { it.id != "case-3" }
        )
        assertTrue(gate.candidates(history).isEmpty())
    }

    @Test fun falseSuccessLabelsCannotOverrideIndependentVerification() {
        val history = (1..4).map { run("case-$it") }
        val gate = VerifiedSkillReview(
            independentVerifier = IndependentExperienceVerifier { it.id == "case-1" }
        )
        assertTrue(gate.candidates(history).isEmpty())
    }

    @Test fun verifierExceptionFailsEntireBatchClosed() {
        val history = (1..3).map { run("case-$it") }
        val gate = VerifiedSkillReview(
            independentVerifier = IndependentExperienceVerifier { exp ->
                if (exp.id == "case-2") error("unreachable independent evidence")
                true
            }
        )
        assertTrue(gate.candidates(history).isEmpty())
    }

    @Test fun sameIdWithDifferentContentsAlwaysFailsClosedBeforeEvidenceLookup() {
        val original = run("same")
        val tampered = original.copy(toolIds = listOf("get_storage_info"))
        val gate = VerifiedSkillReview(
            independentVerifier = IndependentExperienceVerifier { true }
        )
        assertThrows(SecurityException::class.java) {
            gate.candidates(listOf(original, tampered, run("other")))
        }
    }

    @Test fun replaysDoNotCountAsIndependentSuccessfulAttempts() {
        val original = run("repeat")
        val gate = VerifiedSkillReview(
            independentVerifier = IndependentExperienceVerifier { true }
        )
        assertTrue(gate.candidates(List(6) { original }).isEmpty())
    }

    @Test fun destructiveSequenceRemainsNotEligibleDespiteIndependentEvidence() {
        val history = (1..3).map { run("case-$it", tools = listOf("delete_file")) }
        val gate = VerifiedSkillReview(
            independentVerifier = IndependentExperienceVerifier { true }
        )
        val candidate = gate.candidates(history).single()
        assertFalse(candidate.eligibleForOwnerReview)
        assertTrue(candidate.requiresOwnerApproval)
    }
}

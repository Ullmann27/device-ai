package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class ContinuumContrastiveGateTest {
    private val seeds=listOf(11,73,191)
    private fun goodRetrieval():List<ContrastiveRetrievalEvidence> =
        seeds.flatMap { seed -> listOf("a","b","c").flatMap { domain ->
            listOf(0.45,0.65).map { drop ->
                ContrastiveRetrievalEvidence(seed,domain,drop,0.40,0.47)
            }
        } }
    private fun goodProtected():List<ContrastiveSkillEvidence> =
        seeds.flatMap { seed -> listOf("intent","semantic").map { task ->
            ContrastiveSkillEvidence(seed,task,0.30,0.301,5,5,6)
        } }

    @Test fun goodEvidenceOnlyPermitsReview() {
        val result=ContinuumContrastiveGate.evaluate(goodRetrieval(),goodProtected())
        assertEquals("REVIEW_REQUIRED",result.status)
        assertTrue(result.reasons.isEmpty())
        assertFalse(result.automaticPromotion)
    }

    @Test fun unchangedHoldoutRejectsCandidate() {
        val cases=goodRetrieval().map { it.copy(afterMrr=it.beforeMrr) }
        val decision=ContinuumContrastiveGate.evaluate(cases,goodProtected())
        assertEquals("REJECTED",decision.status)
        assertTrue(decision.reasons.contains("HOLDOUT_GAIN_INSUFFICIENT"))
    }

    @Test fun forgettingCannotBeOverriddenByGoodHoldout() {
        val skills=goodProtected().toMutableList()
        skills[0]=skills[0].copy(afterCorrect=4)
        val decision=ContinuumContrastiveGate.evaluate(goodRetrieval(),skills)
        assertEquals("REJECTED",decision.status)
        assertTrue(decision.reasons.contains("SKILL_ACCURACY_REGRESSION"))
        assertFalse(decision.automaticPromotion)
    }

    @Test fun duplicateOrMissingEvidenceRejected() {
        val retrieval=goodRetrieval()
        assertTrue(ContinuumContrastiveGate.evaluate(retrieval+retrieval[0],goodProtected())
            .reasons.contains("DUPLICATE_CASE"))
        assertTrue(ContinuumContrastiveGate.evaluate(retrieval,goodProtected().dropLast(1))
            .reasons.contains("INCOMPLETE_RETENTION_EVIDENCE"))
    }
}

package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Frozen v1 analysis: documents its repeated targets without altering training, audits or stored checkpoints. */
class ContinuumCdxLargeCurriculumCycleTest {
    @Test fun everyThirdStageRepeatsTheAnswerTargetsButNotTheEvidenceIdentity() {
        val c = ContinuumCdxLargeCurriculum
        c.validate()
        for (stage in 0 until c.STAGES - 3) {
            val first = c.trainingStage(stage).pairs
            val later = c.trainingStage(stage + 3).pairs
            assertEquals(c.PAIRS_PER_STAGE, first.size)
            assertEquals(first.map { it.answer }, later.map { it.answer })
            assertTrue(first.map { it.conditionerText() }.toSet()
                .intersect(later.map { it.conditionerText() }.toSet()).isEmpty())
        }
    }

    @Test fun historicalFullSentenceAuditDoesNotHaveTheSameTargetShapeAsShortAnswers() {
        val c = ContinuumCdxLargeCurriculum
        assertEquals(2, c.historicAudit.size)
        assertTrue(c.historicAudit.all { it.answer.endsWith(".") && it.answer.contains(" ") })
        assertFalse(c.train.any { it.answer.endsWith(".") })
        assertEquals(48, c.audit.pairs.size)
        assertTrue(c.train.map { it.conditionerText() }.toSet()
            .intersect(c.protectedAudit().pairs.map { it.conditionerText() }.toSet()).isEmpty())
    }
}

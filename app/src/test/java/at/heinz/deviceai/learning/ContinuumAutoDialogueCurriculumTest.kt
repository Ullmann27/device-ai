package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumAutoDialogueCurriculumTest {
    @Test fun generatedDialoguesAreDisjointAndMechanicallyLabeled() {
        for (stage in listOf(0, 1, 31, 63)) {
            val batch = ContinuumAutoDialogueCurriculum.batch(stage)
            assertEquals(16, batch.training.size)
            assertEquals(16, batch.audit.size)
            val all = batch.training + batch.audit
            assertEquals(all.size, all.map { it.query }.distinct().size)
            val trainCandidates = batch.training.flatMap { it.candidates }.toSet()
            assertTrue(batch.audit.none { test -> test.candidates.any(trainCandidates::contains) })
            for (item in all) {
                assertTrue(item.query.contains("Person A:"))
                assertTrue(item.query.contains("Person B:"))
                assertEquals(3, item.candidates.size)
                assertEquals(3, item.candidates.distinct().size)
                val namedRobot = requireNotNull(Regex("(?:S[0-9]{4}|F[0-9]{3})")
                    .find(item.query)?.value) { "Missing robot identity in synthetic dialogue" }
                assertTrue(item.candidates.all { namedRobot in it })
                assertFalse(item.query.contains("http"))
            }
        }
    }

    @Test fun frozenAuditDoesNotBecomeTrainingAcrossStages() {
        val fixed = ContinuumAutoDialogueCurriculum.batch(0).audit.takeLast(8)
        val first = ContinuumAutoDialogueCurriculum.batch(0)
        val second = ContinuumAutoDialogueCurriculum.batch(1)
        assertEquals(fixed, second.audit.takeLast(8))
        assertTrue(first.training.map { it.query }.toSet()
            .intersect(second.training.map { it.query }.toSet()).isEmpty())
        assertTrue(second.training.none { it.query in fixed.map { e -> e.query } })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeStage() {
        ContinuumAutoDialogueCurriculum.batch(-1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsStagesBeyondFiniteResearchBudget() {
        ContinuumAutoDialogueCurriculum.batch(ContinuumAutoDialogueCurriculum.MAX_STAGES)
    }
}

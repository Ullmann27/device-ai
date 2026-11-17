package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ContinuumPublicCurriculumTest {
    @Test fun corpusHasDisjointBalancedSyntheticTrainAndAuditSets() {
        val corpus=ContinuumPublicCurriculum
        corpus.validate()
        assertEquals(72,corpus.intentTrain.size)
        assertEquals(24,corpus.intentAudit.size)
        assertEquals(32,corpus.semanticTrain.size)
        assertEquals(12,corpus.semanticAudit.size)
        assertEquals(0,corpus.intentTrain.map { it.text }.toSet()
            .intersect(ContinuumLearningCore.INTENT_HOLDOUT.map { it.text }.toSet()).size)
    }

    @Test fun labIsOptInRunsAtMostOnceAndNeverCreatesOwnerFacts() {
        val dir=Files.createTempDirectory("continuum-public-lab").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            core.bootstrapOfflineCurriculum()
            core.bootstrapSemanticCurriculum()
            val before=core.modelSnapshot()
            val report=core.trainPublicCurriculum()!!
            if(report.completed) {
                assertEquals(1100,report.intentSteps)
                assertEquals(900,report.semanticSteps)
            } else {
                assertFalse(report.accepted)
                assertArrayEquals(before,core.modelSnapshot())
            }
            assertEquals(72,report.intentTrainingAfter.total-12)
            assertEquals(24,report.intentAuditAfter.total)
            assertEquals(12,report.semanticAuditAfter.total)
            assertEquals(0,core.status().experiences.userFacts)
            assertEquals(0,core.status().experiences.intentLabels)
            if(report.accepted)assertFalse(before.contentEquals(core.modelSnapshot()))
            else assertArrayEquals(before,core.modelSnapshot())
            if(report.completed) assertNull(core.trainPublicCurriculum())
        } finally { dir.deleteRecursively() }
    }

    @Test fun interruptionDoesNotWriteAuditsOrWeightsAndCanRetry() {
        val dir=Files.createTempDirectory("continuum-public-lab-interrupt").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            val old=core.modelSnapshot()
            val first=core.trainPublicCurriculum { true }!!
            assertFalse(first.completed)
            assertFalse(first.accepted)
            assertArrayEquals(old,core.modelSnapshot())
            assertEquals(0,core.status().experiences.audits)
            val second=core.trainPublicCurriculum()!!
            assertTrue(second.intentSteps>=0)
            if(!second.completed)assertArrayEquals(old,core.modelSnapshot())
        } finally { dir.deleteRecursively() }
    }

    @Test fun ownerFactsAreNeverPromotedOrDeletedBySyntheticCorpus() {
        val dir=Files.createTempDirectory("continuum-public-lab-owner").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            assertTrue(core.recordOwnerFact("Ich wünsche Antworten auf Deutsch."))
            val modelBefore=core.modelSnapshot()
            val result=core.trainPublicCurriculum()!!
            assertEquals(1,core.status().experiences.userFacts)
            assertEquals(listOf("Ich wünsche Antworten auf Deutsch."),core.activeOwnerFacts()
                .map { it.content })
            if(!result.accepted)assertArrayEquals(modelBefore,core.modelSnapshot())
        } finally { dir.deleteRecursively() }
    }
}

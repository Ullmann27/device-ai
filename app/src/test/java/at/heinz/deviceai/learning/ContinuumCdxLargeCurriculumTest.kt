package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ContinuumCdxLargeCurriculumTest {
    @Test fun largeCorpusPartitionsAndNeverTrainsFrozenAuditExamples() {
        val corpus=ContinuumCdxLargeCurriculum
        corpus.validate()
        assertEquals(576,corpus.TOTAL_TRAIN)
        assertEquals(12,corpus.STAGES)
        assertEquals(48,corpus.PAIRS_PER_STAGE)
        assertEquals(48,corpus.audit.pairs.size)
        assertEquals(50,corpus.protectedAudit().pairs.size)
        val seen=mutableSetOf<String>()
        for(stage in 0 until corpus.STAGES) {
            val train=corpus.trainingStage(stage)
            assertEquals(DecoderSplit.TRAIN,train.split)
            assertEquals(48,train.pairs.size)
            train.pairs.forEach { assertTrue(seen.add(it.conditionerText())) }
        }
        assertEquals(576,seen.size)
        assertTrue(corpus.protectedAudit().pairs.none {it.conditionerText() in seen})
        assertThrows(IllegalArgumentException::class.java) {corpus.trainingStage(-1)}
        assertThrows(IllegalArgumentException::class.java) {corpus.trainingStage(12)}
    }

    @Test fun cancelledRoundsNeverModifyDecoderNorStagePointer() {
        val dir=Files.createTempDirectory("continuum-cdx-big-cancel").toFile()
        try {
            val runner=ContinuumCdxLargeLab(dir)
            val initial=runner.status()
            assertEquals(0,initial.completed)
            assertFalse(initial.available)
            assertThrows(IllegalStateException::class.java) {
                runner.next {true}
            }
            assertEquals(initial,runner.status())
            assertFalse(java.io.File(dir,"learning-core-v2/cdx-pairs-v1-stage.txt").exists())
        } finally {dir.deleteRecursively()}
    }

    @Test fun legacyAuditCannotBeReusedAsTrainingInputOrSilentlyPromoted() {
        val dir=Files.createTempDirectory("continuum-cdx-big-overlap").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            val train=ContinuumCdxLargeCurriculum.trainingStage(0)
            val audit=ContinuumCdxLargeCurriculum.audit
            val leaked=DecoderEvidenceCorpus(listOf(train.pairs[0]),DecoderSplit.AUDIT)
            val before=core.modelSnapshot()
            assertThrows(IllegalArgumentException::class.java) {
                core.trainExperimentalPairedDecoder(
                    train,audit,steps=4,protectedAudits=listOf(leaked))
            }
            assertArrayEquals(before,core.modelSnapshot())
            assertFalse(core.pairedDecoderStatus().available)
        } finally {dir.deleteRecursively()}
    }
}

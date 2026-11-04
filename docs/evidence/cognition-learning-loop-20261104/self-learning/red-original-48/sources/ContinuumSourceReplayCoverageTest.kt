package at.heinz.deviceai.learning

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ContinuumSourceReplayCoverageTest {
    @Test fun retriedOldSourceParticipatesDespiteNewerReplay() = fixture { dir, core, store ->
        val old=source(0,"Quantenkohärenz","Quantenkohärenz beschreibt die Phasenbeziehung zwischen Quantenzuständen.")
        val newer=(1..48).map(::source)
        store.appendMissing(listOf(experience(old,1L))+newer.mapIndexed { i,s -> experience(s,i+2L) })
        assertFalse(store.replay(LearningExperienceType.SOURCE_TEXT,48).any { it.id==experience(old,1L).id })
        val before=core.modelSnapshot()
        val expected=TrainableSubwordEncoder.restore(before).evaluateSemantic(
            ContinuumLearningCore.SEMANTIC_BOOTSTRAP+
                newer.drop(1).map { SubwordSemanticExample(it.subject,it.excerpt,true) }+
                SubwordSemanticExample(old.subject,old.excerpt,true)
        )
        val report=checkNotNull(core.learnFromSources(listOf(old)) { true })
        assertEquals("the current source must enter the measured training set",expected.loss,report.trainingBefore.loss,1e-12)
        assertEquals(expected.total,report.trainingBefore.total)
        assertEquals(listOf(experience(old,1L).id),report.experienceIds)
        assertFalse(report.completedEvaluation)
        assertFalse(report.accepted)
        assertArrayEquals(before,core.modelSnapshot())
        assertEquals(LearningStatus.REPLAY_PENDING,store.all().single { it.id==experience(old,1L).id }.status)
        assertTrue(dir.isDirectory)
    }

    @Test fun unusableHistoricalTopicsCannotDisplaceCurrentSource() = fixture { _,core,store ->
        val current=source(0)
        store.append(experience(current,1L))
        store.appendMissing((1..48).map { i ->
            LearningExperienceFactory.create(
                LearningExperienceType.SOURCE_TEXT,LearningProvenance.REVIEWED_WEB_SOURCE,
                LearningTrustClass.SOURCE_OBSERVATION,LearningStatus.OBSERVED,
                "Synthetische geprüfte Beobachtung Nummer $i ohne Lernthema.",
                sourceRef="https://example.org/source/$i",now=i+2L
            )
        })
        val expected=TrainableSubwordEncoder.restore(core.modelSnapshot()).evaluateSemantic(
            ContinuumLearningCore.SEMANTIC_BOOTSTRAP+
                SubwordSemanticExample(current.subject,current.excerpt,true)
        )
        val report=checkNotNull(core.learnFromSources(listOf(current)) { true })
        assertEquals(expected.total,report.trainingBefore.total)
        assertEquals(expected.loss,report.trainingBefore.loss,1e-12)
        assertEquals(1,report.replayExamples)
        assertFalse(report.completedEvaluation)
    }

    @Test fun oversizedDistinctBatchIsRefusedBeforeLedgerWrites() = fixture { _,core,store ->
        val before=core.modelSnapshot()
        val phases=mutableListOf<String>()
        core.observeTraining { phases+=it }
        assertNull(core.learnFromSources((0..48).map(::source)) { true })
        assertTrue(store.all().isEmpty())
        assertTrue(phases.isEmpty())
        assertArrayEquals(before,core.modelSnapshot())
    }

    @Test fun normalizedShortTopicsAreRejectedBeforeAnyLedgerWrite() {
        for(topic in listOf("   "," X "," XY ")) fixture { _,core,store ->
            val before=core.modelSnapshot()
            assertThrows(IllegalArgumentException::class.java) {
                core.learnFromSources(listOf(source(0),source(1,topic))) { true }
            }
            assertTrue(store.all().isEmpty())
            assertArrayEquals(before,core.modelSnapshot())
        }
    }

    @Test fun maximumCurrentBatchRetainsEverySource() = fixture { _,core,_ ->
        val batch=(0..47).map(::source)
        val report=checkNotNull(core.learnFromSources(batch) { true })
        assertEquals(48,report.experienceIds.distinct().size)
        assertEquals(48,report.replayExamples)
        assertEquals(ContinuumLearningCore.SEMANTIC_BOOTSTRAP.size+48,report.trainingBefore.total)
        assertFalse(report.completedEvaluation)
    }

    @Test fun duplicateInputDoesNotConsumeReplayCapacity() = fixture { _,core,store ->
        val source=source(0)
        val report=checkNotNull(core.learnFromSources(List(49) { source }) { true })
        assertEquals(1,report.experienceIds.size)
        assertEquals(1,report.replayExamples)
        assertEquals(1,store.all().count { it.type==LearningExperienceType.SOURCE_TEXT })
        assertFalse(report.completedEvaluation)
    }

    @Test fun revokedSourceCannotReenterTraining() = fixture { _,core,store ->
        val source=source(0)
        val prior=experience(source,1L)
        store.append(prior.copy(status=LearningStatus.REVOKED))
        val before=core.modelSnapshot()
        assertNull(core.learnFromSources(listOf(source)))
        assertEquals(LearningStatus.REVOKED,store.all().single().status)
        assertArrayEquals(before,core.modelSnapshot())
    }

    @Test fun completedEvaluationUsesTheRequestedSource() = fixture { _,core,_ ->
        val source=source(0)
        val before=core.modelSnapshot()
        val expected=TrainableSubwordEncoder.restore(before).evaluateSemantic(
            ContinuumLearningCore.SEMANTIC_BOOTSTRAP+
                SubwordSemanticExample(source.subject,source.excerpt,true)
        )
        val report=checkNotNull(core.learnFromSources(listOf(source)))
        assertEquals(expected.total,report.trainingBefore.total)
        assertEquals(expected.loss,report.trainingBefore.loss,1e-12)
        assertTrue(report.completedEvaluation)
        assertEquals(550,report.steps)
        assertEquals(1,report.experienceIds.size)
    }

    private fun source(index:Int):MentorSource=source(index,"Mechanik")

    private fun source(index:Int,topic:String,excerpt:String=
        "Synthetischer Prüfauszug $index: Ein Elektromotor wandelt elektrische Energie in Bewegung um."
    )=MentorSource(topic,"https://de.wikipedia.org/wiki/Synthetic_Source_$index",
        index.toString(16).padStart(64,'0'),excerpt)

    private fun experience(source:MentorSource,now:Long)=LearningExperienceFactory.create(
        LearningExperienceType.SOURCE_TEXT,LearningProvenance.MENTOR_WIKIPEDIA,
        LearningTrustClass.SOURCE_OBSERVATION,LearningStatus.OBSERVED,
        source.excerpt,source.subject,source.url+"#sha256="+source.sha,now
    )

    private fun fixture(action:(File,ContinuumLearningCore,FileLearningExperienceStore)->Unit) {
        val dir=Files.createTempDirectory("continuum-source-coverage-").toFile()
        try {
            var now=1_000L
            action(dir,ContinuumLearningCore(dir) { now++ },
                FileLearningExperienceStore(File(dir,"learning-core-v2")))
        } finally { dir.deleteRecursively() }
    }
}

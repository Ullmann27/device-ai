package at.heinz.deviceai.learning

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ContinuumSourceRoundRecoveryTest {
    @Test fun wholeBatchIsValidatedBeforeAnyDurableMutation() = withDirectory { dir ->
        val core=ContinuumLearningCore(dir) { 100L }
        val before=core.modelSnapshot()
        val valid=source(1)
        for(invalid in listOf(valid.copy(subject="  x  "),
            valid.copy(excerpt="!".repeat(30)),valid.copy(subject="???"))) {
            assertThrows(IllegalArgumentException::class.java) {
                core.learnFromSources(listOf(valid,invalid)) { true }
            }
            assertTrue(store(dir).all().isEmpty())
            assertFalse(journal(dir).exists())
            assertArrayEquals(before,core.modelSnapshot())
        }
    }

    @Test fun eighteenDistinctRecordsAreRefusedBeforePersistence() = withDirectory { dir ->
        val core=ContinuumLearningCore(dir) { 100L }
        val before=core.modelSnapshot()
        assertNull(core.learnFromSources((1..18).map(::source)) { true })
        assertTrue(store(dir).all().isEmpty())
        assertFalse(journal(dir).exists())
        assertArrayEquals(before,core.modelSnapshot())
    }

    @Test fun seventeenRecordsAndDuplicatesFitOneAudit() = withDirectory { dir ->
        val core=ContinuumLearningCore(dir) { 100L }
        val sources=(1..17).map(::source)
        val before=core.modelSnapshot()
        val report=core.learnFromSources(sources+sources) { true }!!
        assertEquals(17,report.experienceIds.size)
        assertEquals(17,report.experienceIds.distinct().size)
        assertEquals(17,report.replayExamples)
        assertFalse(report.completedEvaluation)
        assertEquals(0,report.steps)
        assertTrue(ContinuumSourceNovelty.completedHashes(dir).isEmpty())
        assertArrayEquals(before,core.modelSnapshot())
        val audit=store(dir).all().single { it.type==LearningExperienceType.TRAINING_AUDIT }
        assertEquals(report.experienceIds,audit.sourceRef!!.split(','))
    }

    @Test fun preparationCopiesAndProtectsTheValidatedBatch() {
        val original=mutableListOf(source(1))
        val prepared=ContinuumLearningCore.prepareSourceBatch(original)!!
        original[0]=source(2)
        assertEquals(listOf(source(1)),prepared)
        assertThrows(UnsupportedOperationException::class.java) {
            (prepared as MutableList<MentorSource>).add(source(3))
        }
    }

    @Test fun anOldRequestedSourceParticipatesDespiteFortyEightNewerRecords() = withDirectory { dir ->
        val requested=source(1).copy(subject="Astronomie",
            excerpt="Planeten bewegen sich auf Umlaufbahnen um einen Stern.")
        val distracting=source(2).copy(subject="Biologie",
            excerpt="Pflanzen wandeln Sonnenlicht durch Fotosynthese in chemische Energie um.")
        store(dir).append(experience(requested,1L))
        store(dir).appendMissing((2..49).map { experience(distracting.copy(
            url="https://example.org/source-$it",sha=sha(it)),1_000L+it) })
        assertFalse(store(dir).replay(LearningExperienceType.SOURCE_TEXT,48)
            .any { it.id==experience(requested,1L).id })
        val core=ContinuumLearningCore(dir) { 100L }
        val before=core.modelSnapshot()
        val expected=TrainableSubwordEncoder.restore(before).evaluateSemantic(
            ContinuumLearningCore.SEMANTIC_BOOTSTRAP+
                List(47) { SubwordSemanticExample(distracting.subject,distracting.excerpt,true) }+
                SubwordSemanticExample(requested.subject,requested.excerpt,true))
        val report=core.learnFromSources(listOf(requested)) { true }!!
        assertEquals(expected.total,report.trainingBefore.total)
        assertEquals(expected.correct,report.trainingBefore.correct)
        assertEquals(expected.loss,report.trainingBefore.loss,1e-12)
        assertEquals(listOf(experience(requested,1L).id),report.experienceIds)
        assertEquals(48,report.replayExamples)
        assertArrayEquals(before,core.modelSnapshot())
    }

    @Test fun cancellationLeavesThePersistedSourceEligibleAfterRestart() = withDirectory { dir ->
        val sources=listOf(source(1))
        val core=ContinuumLearningCore(dir) { 100L }
        val before=core.modelSnapshot()
        val stopped=core.learnFromSources(sources) { true }!!
        assertFalse(stopped.accepted)
        assertFalse(stopped.completedEvaluation)
        assertTrue(store(dir).all().any { it.id in stopped.experienceIds })
        assertEquals(sources,ContinuumSourceNovelty.pending(sources,
            ContinuumSourceNovelty.knownHashes(dir)))
        assertArrayEquals(before,ContinuumLearningCore(dir).modelSnapshot())
        val resumed=ContinuumLearningCore(dir) { 200L }.learnFromSources(sources)!!
        assertTrue(resumed.completedEvaluation)
        assertEquals(setOf(sources.single().sha),ContinuumSourceNovelty.completedHashes(dir))
        assertTrue(ContinuumSourceNovelty.pending(sources,
            ContinuumSourceNovelty.knownHashes(dir)).isEmpty())
    }

    @Test fun revokedSourceIsNeverReactivatedByBatchAppend() = withDirectory { dir ->
        val source=source(1)
        val revoked=experience(source,1L).copy(status=LearningStatus.REVOKED)
        store(dir).append(revoked)
        val core=ContinuumLearningCore(dir) { 100L }
        val before=core.modelSnapshot()
        assertNull(core.learnFromSources(listOf(source)) { true })
        assertEquals(revoked,store(dir).all().single())
        assertTrue(ContinuumSourceNovelty.completedHashes(dir).isEmpty())
        assertArrayEquals(before,core.modelSnapshot())
    }

    @Test fun evidenceWriteFailureReportsTheActualModelCommit() = withDirectory { dir ->
        val control=File(dir,"control").apply { mkdirs() }
        val failed=File(dir,"failed").apply { mkdirs() }
        val sources=listOf(source(1))
        val expectedCore=ContinuumLearningCore(control) { 100L }
        val expected=expectedCore.learnFromSources(sources)!!
        assertTrue(expected.completedEvaluation)
        var phase=""
        val core=ContinuumLearningCore(failed) { 100L }.apply {
            observeTraining {
                phase=it
                if(it=="SAVE_EVIDENCE") {
                    File(journal(failed).parentFile,journal(failed).name+".tmp").mkdir()
                }
            }
        }
        val error=assertThrows(ContinuumSourceEvidenceNotPersisted::class.java) {
            core.learnFromSources(sources)
        }
        assertEquals("SAVE_EVIDENCE",phase)
        assertTrue(error.cause is java.io.IOException)
        assertEquals(expected.accepted,error.modelCommitted)
        assertArrayEquals(expectedCore.modelSnapshot(),core.modelSnapshot())
        assertTrue(ContinuumSourceNovelty.completedHashes(failed).isEmpty())
        assertEquals(sources,ContinuumSourceNovelty.pending(sources,
            ContinuumSourceNovelty.knownHashes(failed)))
    }

    private fun source(id:Int)=MentorSource("Optik","https://example.org/source-$id",sha(id),
        "Licht wird an glatten Oberflächen reflektiert und an Grenzflächen gebrochen.")
    private fun sha(id:Int)="%064x".format(id)
    private fun journal(dir:File)=File(dir,"learning-core-v2/source-attempts-v1.txt")
    private fun store(dir:File)=FileLearningExperienceStore(File(dir,"learning-core-v2"))
    private fun experience(source:MentorSource,at:Long)=LearningExperienceFactory.create(
        LearningExperienceType.SOURCE_TEXT,LearningProvenance.PUBLIC_WEB_SOURCE,
        LearningTrustClass.SOURCE_OBSERVATION,LearningStatus.OBSERVED,source.excerpt,
        annotation=source.subject,sourceRef=source.url+"#sha256="+source.sha,now=at)
    private fun withDirectory(block:(File)->Unit) {
        val dir=Files.createTempDirectory("continuum-source-round-recovery").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }
}

package at.heinz.deviceai.learning

import at.heinz.deviceai.cognitive.IdentitySigner
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

class ContinuumSourceLearningResumeIntegrationTest {
    private val signer=object:IdentitySigner {
        override fun sign(payload:ByteArray):ByteArray=Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec("resume-integration-test-only".toByteArray(),"HmacSHA256"))
            doFinal(payload)
        }
        override fun verify(payload:ByteArray,signature:ByteArray)=
            MessageDigest.isEqual(sign(payload),signature)
    }

    @Test fun cancelledRoundResumesAndPreservesSignedFacts() = fixture { dir ->
        var now=1_000L
        val core=ContinuumLearningCore(dir,signer) { now++ }
        core.confirmOwnerFact("Ich wohne in Steyr.")
        val ownerFacts=core.activeOwnerFacts()
        assertEquals(1,ownerFacts.size)
        val journal=File(dir,"learning-core-v2/"+SignedOwnerCorrectionJournal.FILE_NAME)
        val signedBytes=journal.readBytes()
        val originalModel=core.modelSnapshot()
        val source=source(1)
        ContinuumSourceNovelty.markStarted(dir,listOf(source))
        archive(dir,source)

        val interrupted=checkNotNull(core.learnFromSources(listOf(source)) { true })
        assertFalse(interrupted.completedEvaluation)
        assertFalse(interrupted.accepted)
        assertArrayEquals(originalModel,core.modelSnapshot())
        assertTrue(ContinuumSourceNovelty.completedHashes(dir).isEmpty())

        val reopened=ContinuumLearningCore(dir,signer) { now++ }
        val pending=ContinuumSourceNovelty.pending(listOf(source),ContinuumSourceNovelty.knownHashes(dir))
        assertEquals(listOf(source),pending)
        assertEquals(CorrectionAuditStatus.VERIFIED,reopened.correctionAudit().status)
        val expected=TrainableSubwordEncoder.restore(reopened.modelSnapshot()).evaluateSemantic(
            ContinuumLearningCore.SEMANTIC_BOOTSTRAP+
                SubwordSemanticExample(source.subject,source.excerpt,true)
        )
        val completed=checkNotNull(reopened.learnFromSources(pending))
        assertTrue(completed.completedEvaluation)
        assertEquals(550,completed.steps)
        assertEquals(expected.total,completed.trainingBefore.total)
        assertEquals(expected.loss,completed.trainingBefore.loss,1e-12)
        ContinuumSourceNovelty.markCompleted(dir,pending)

        assertEquals(setOf(source.sha),ContinuumSourceNovelty.completedHashes(dir))
        assertTrue(ContinuumSourceNovelty.pending(listOf(source),ContinuumSourceNovelty.knownHashes(dir)).isEmpty())
        assertEquals(ownerFacts,reopened.activeOwnerFacts())
        assertArrayEquals(signedBytes,journal.readBytes())
        assertEquals(CorrectionAuditStatus.VERIFIED,reopened.correctionAudit().status)
        val observations=FileLearningExperienceStore(File(dir,"learning-core-v2")).all()
            .filter { it.type==LearningExperienceType.SOURCE_TEXT }
        assertEquals(1,observations.size)
        assertEquals(LearningTrustClass.SOURCE_OBSERVATION,observations.single().trust)
        assertEquals(if(completed.accepted)LearningStatus.LEARNED else LearningStatus.REPLAY_PENDING,
            observations.single().status)
    }

    @Test fun oversizedRoundRemainsPendingWithoutPartialLearning() = fixture { dir ->
        val core=ContinuumLearningCore(dir) { 1_000L }
        val batch=(1..18).map(::source)
        val originalModel=core.modelSnapshot()
        ContinuumSourceNovelty.markStarted(dir,batch)
        archive(dir,batch.first())
        assertNull(core.learnFromSources(batch))

        val reopened=ContinuumLearningCore(dir) { 2_000L }
        assertArrayEquals(originalModel,reopened.modelSnapshot())
        assertTrue(FileLearningExperienceStore(File(dir,"learning-core-v2")).all().isEmpty())
        assertTrue(ContinuumSourceNovelty.completedHashes(dir).isEmpty())
        assertEquals(batch,ContinuumSourceNovelty.pending(batch,ContinuumSourceNovelty.knownHashes(dir)))
    }

    private fun source(index:Int):MentorSource {
        val excerpt="Synthetischer Prüfauszug $index: Solarzellen wandeln Licht in elektrische Energie um."
        val sha=MessageDigest.getInstance("SHA-256").digest(excerpt.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return MentorSource("Solarzellen","https://example.org/solar/$index",sha,excerpt)
    }

    private fun archive(dir:File,source:MentorSource) {
        ContinuumKnowledgeArchive(dir).save(ContinuumKnowledgeArchive.Kind.PUBLIC_WEB_EXCERPT,
            source.subject,source.url,"Synthetischer Testauszug",source.excerpt)
    }

    private fun fixture(action:(File)->Unit) {
        val dir=Files.createTempDirectory("source-learning-resume-").toFile()
        try { action(dir) } finally { dir.deleteRecursively() }
    }
}

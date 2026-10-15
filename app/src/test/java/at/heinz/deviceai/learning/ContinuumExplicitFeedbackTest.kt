package at.heinz.deviceai.learning

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

/** Owner feedback is measurable, revocable metadata; never a training label. */
class ContinuumExplicitFeedbackTest {
    @Test fun ratingsSurviveRestartButNeverTrainOrRevealAnswers() {
        val dir=Files.createTempDirectory("continuum-user-feedback").toFile()
        try {
            val now={ 2400L } // equal timestamps must still produce distinct ratings
            val core=ContinuumLearningCore(dir,now)
            val before=core.modelSnapshot()
            assertTrue(core.recordOwnerFact("Fakt: Planet | ist | Himmelskörper"))
            assertTrue(core.recordExplicitFeedback(true))
            assertTrue(core.recordExplicitFeedback(false))
            assertTrue(core.recordExplicitFeedback(true))
            assertEquals(ContinuumFeedbackSummary(2,1,0),
                ContinuumLearningCore(dir,now).feedbackSummary())
            val experiences=FileLearningExperienceStore(
                java.io.File(dir,"learning-core-v2")).all()
            val ratings=experiences.filter { it.type==LearningExperienceType.USER_FEEDBACK }
            assertEquals(3,ratings.size)
            assertEquals(3,ratings.map { it.id }.toSet().size)
            assertTrue(ratings.all {
                it.status==LearningStatus.CONFIRMED &&
                it.provenance==LearningProvenance.USER_CONFIRMED &&
                it.trust==LearningTrustClass.OWNER_CONFIRMED &&
                it.annotation=="rating-only-no-training" &&
                it.content in setOf("helpful","not-helpful") &&
                it.sourceRef?.startsWith("local-feedback/")==true
            })
            assertTrue(ratings.none { it.content.contains("Planet") })
            assertArrayEquals(before,core.modelSnapshot())
            assertEquals(0,core.status().experiences.intentLabels)
            assertEquals(0,core.status().experiences.audits)
        } finally { dir.deleteRecursively() }
    }

    @Test fun revocationIsIdempotentAndLeavesConfirmedMemoriesAndWeightsUntouched() {
        val dir=Files.createTempDirectory("continuum-feedback-revocation").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            val before=core.modelSnapshot()
            assertTrue(core.recordOwnerFact("Meine Sprache ist Deutsch."))
            core.recordExplicitFeedback(false)
            core.recordExplicitFeedback(false)
            assertEquals(2,core.revokeExplicitFeedback())
            assertEquals(0,core.revokeExplicitFeedback())
            assertEquals(ContinuumFeedbackSummary(0,0,2),core.feedbackSummary())
            assertEquals(1,core.activeOwnerFacts().size)
            assertArrayEquals(before,core.modelSnapshot())
            val restarted=ContinuumLearningCore(dir)
            assertEquals(ContinuumFeedbackSummary(0,0,2),restarted.feedbackSummary())
            assertTrue(restarted.recordExplicitFeedback(true))
            assertEquals(ContinuumFeedbackSummary(1,0,2),restarted.feedbackSummary())
        } finally { dir.deleteRecursively() }
    }
}

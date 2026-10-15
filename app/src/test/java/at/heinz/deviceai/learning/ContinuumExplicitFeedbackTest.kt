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

    @Test fun feedbackCannotDisplaceConfirmedFactsOrSourceHistoryAtSharedCapacity() {
        val dir=Files.createTempDirectory("continuum-feedback-cap").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            val original="Mein bestätigter Fakt bleibt unverändert."
            assertTrue(core.recordOwnerFact(original))
            repeat(60) { assertTrue(core.recordExplicitFeedback(it%2==0)) }
            val persisted=ContinuumLearningCore(dir)
            val feedback=persisted.feedbackSummary()
            assertEquals(24,feedback.positive+feedback.negative+feedback.revoked)
            assertTrue(persisted.activeOwnerFacts().any { it.content==original })
            val ledger=FileLearningExperienceStore(java.io.File(dir,"learning-core-v2")).all()
            assertEquals(1,ledger.count { it.type==LearningExperienceType.USER_FACT })
            assertEquals(24,ledger.count { it.type==LearningExperienceType.USER_FEEDBACK })
            assertEquals(25,ledger.size)
        } finally { dir.deleteRecursively() }
    }

    @Test fun fullLedgerRejectsNewRatingWithoutDiscardingExistingHistory() {
        val dir=Files.createTempDirectory("continuum-feedback-no-evict").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            val ledger=FileLearningExperienceStore(java.io.File(dir,"learning-core-v2"))
            repeat(FileLearningExperienceStore.MAX_RECORDS) { i ->
                val item=LearningExperienceFactory.create(
                    LearningExperienceType.COMMAND_MAPPING,
                    LearningProvenance.USER_CONFIRMED,
                    LearningTrustClass.OWNER_CONFIRMED,
                    LearningStatus.CONFIRMED,
                    "Testkommando Nummer $i",annotation="get_battery_info",
                    now=i.toLong()
                )
                assertTrue(ledger.append(item))
            }
            val knownIds=ledger.all().map { it.id }.toSet()
            assertThrows(IllegalStateException::class.java) { core.recordExplicitFeedback(true) }
            assertEquals(knownIds,ledger.all().map { it.id }.toSet())
            assertEquals(0,core.feedbackSummary().positive)
        } finally { dir.deleteRecursively() }
    }

    @Test fun ratingsDoNotPreventOrSubstituteForTheOwnerIndependentOfflineCurriculum() {
        val dir=Files.createTempDirectory("continuum-feedback-isolation").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            core.recordExplicitFeedback(true)
            core.recordExplicitFeedback(false)
            // Unlike owner-confirmed intent labels, a thumbs-up/down supplies no
            // ground-truth class for the model and must not count as a lesson.
            assertEquals(0,core.status().experiences.intentLabels)
            assertNotNull(core.bootstrapOfflineCurriculum())
            assertEquals(1,core.status().experiences.audits)
            assertEquals(ContinuumFeedbackSummary(1,1,0),core.feedbackSummary())
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

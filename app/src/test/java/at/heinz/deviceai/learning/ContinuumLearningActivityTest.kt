package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ContinuumLearningActivityTest {
    @Test fun includesAcceptedAndRejectedTrainingAuditsWithoutLeakingText() {
        val root = Files.createTempDirectory("continuum-activity-metadata").toFile()
        try {
            val ledger = FileLearningExperienceStore(File(root, "learning-core-v2"))
            val rejected = LearningExperienceFactory.create(
                type = LearningExperienceType.TRAINING_AUDIT,
                provenance = LearningProvenance.LOCAL_TRAINING,
                trust = LearningTrustClass.SYSTEM_MEASUREMENT,
                status = LearningStatus.REJECTED,
                content = "discarded synthetic candidate",
                now = 1_000L
            )
            val accepted = LearningExperienceFactory.create(
                type = LearningExperienceType.TRAINING_AUDIT,
                provenance = LearningProvenance.LOCAL_TRAINING,
                trust = LearningTrustClass.SYSTEM_MEASUREMENT,
                status = LearningStatus.LEARNED,
                content = "accepted holdout candidate",
                now = 2_000L
            )
            assertTrue(ledger.append(rejected))
            assertTrue(ledger.append(accepted))
            val timeline = ContinuumLearningCore(root).experienceTimelineWithStatus()
            assertEquals(2, timeline.size)
            assertEquals(setOf(LearningStatus.LEARNED, LearningStatus.REJECTED),
                timeline.map { it.status }.toSet())
            assertEquals(listOf(1_000L, 2_000L), timeline.map { it.createdAt }.sorted())
            assertTrue(timeline.all { it.type == LearningExperienceType.TRAINING_AUDIT })
            assertFalse(timeline.toString().contains(rejected.content))
            assertFalse(timeline.toString().contains(accepted.content))
            assertEquals(2, ContinuumLearningCore(root).experienceTimeline().size)
        } finally {
            root.deleteRecursively()
        }
    }
}

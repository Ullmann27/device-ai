package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class ContinuumPublicStudyCycleResultTest {
    @Test fun anAcceptedLocalStageDoesNotReclassifyFailedResearch() {
        val failure = "FAILED_FETCH_SOURCE_IllegalStateException;STAGE_1_ACCEPTED"
        assertEquals("FAILED", ContinuumPublicStudyCycleResult.outcome(failure))
        assertFalse(ContinuumPublicStudyCycleResult.markTopicCompleted(failure))
    }

    @Test fun rejectedPrimaryResearchStaysRejectedEvenWithAnAcceptedLocalStage() {
        val result = "TRAINING_REJECTED;STAGE_1_ACCEPTED"
        assertEquals("REJECTED", ContinuumPublicStudyCycleResult.outcome(result))
        assertTrue(ContinuumPublicStudyCycleResult.markTopicCompleted(result))
    }

    @Test fun acceptedSourcesAndUnchangedSourcesHaveDistinctAuditableOutcomes() {
        assertEquals("ACCEPTED",
            ContinuumPublicStudyCycleResult.outcome("TRAINING_ACCEPTED;STAGE_2_REJECTED"))
        assertEquals("NO_CHANGE",
            ContinuumPublicStudyCycleResult.outcome("SOURCE_UNAVAILABLE;STAGE_1_ACCEPTED"))
        assertEquals("NO_CHANGE",
            ContinuumPublicStudyCycleResult.outcome("SOURCE_UNCHANGED"))
        assertTrue(ContinuumPublicStudyCycleResult.markTopicCompleted("SOURCE_UNAVAILABLE"))
        assertFalse(ContinuumPublicStudyCycleResult.markTopicCompleted("PROVIDER_RATE_LIMIT"))
        assertFalse(ContinuumPublicStudyCycleResult.markTopicCompleted("TRAINING_DEFERRED"))
        assertFalse(ContinuumPublicStudyCycleResult.markTopicCompleted(
            "FAILED_FETCH_SOURCE_IllegalStateException"))
    }

    @Test fun interruptionsCannotBecomeFalseTrainingSuccesses() {
        assertEquals("INTERRUPTED",
            ContinuumPublicStudyCycleResult.outcome("INTERRUPTED;STAGE_2_ACCEPTED"))
        assertEquals("DEFERRED",
            ContinuumPublicStudyCycleResult.outcome("PROVIDER_RATE_LIMIT"))
        assertEquals("DEFERRED",
            ContinuumPublicStudyCycleResult.outcome("TRAINING_DEFERRED"))
    }
}

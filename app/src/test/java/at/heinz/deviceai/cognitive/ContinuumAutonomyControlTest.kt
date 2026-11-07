package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class ContinuumAutonomyControlTest {
    @Test fun defaultResearchPlusProposalsIsExplicitlyBounded() {
        val level = ContinuumAutonomyLevel.RESEARCH_AND_PROPOSALS
        assertTrue(ContinuumAutonomyControl.mayResearch(level))
        assertTrue(ContinuumAutonomyControl.mayPropose(level))
        assertTrue(ContinuumAutonomyControl.explain(level).contains("Keine ungeprüfte Ausführung"))
    }

    @Test fun emergencyStopDeniesBothAutonomousPipelines() {
        val level = ContinuumAutonomyLevel.PAUSED
        assertFalse(ContinuumAutonomyControl.mayResearch(level))
        assertFalse(ContinuumAutonomyControl.mayPropose(level))
        assertTrue(ContinuumAutonomyControl.explain(level).contains("NOT-STOPP"))
    }

    @Test fun researchOnlyDeniesAgentProposals() {
        val level = ContinuumAutonomyLevel.PUBLIC_RESEARCH
        assertTrue(ContinuumAutonomyControl.mayResearch(level))
        assertFalse(ContinuumAutonomyControl.mayPropose(level))
    }
}

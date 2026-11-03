package at.heinz.deviceai.chief

import at.heinz.deviceai.cognitive.CognitiveWorkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chief pattern point 1: acknowledge → work → deliver once → end; progress only at real milestones. */
class TurnLifecycleTest {
    private var now = 0L
    private fun turn() = TurnLifecycle("t1", minMilestoneGapMillis = 1_500) { now }

    @Test fun acknowledgesExactlyOnceThenDeliversExactlyOnceAndEnds() {
        val t = turn()
        assertNotNull(t.acknowledge()); assertNull(t.acknowledge())
        assertEquals(TurnPhase.ACKNOWLEDGED, t.phase)
        assertTrue(t.deliver()); assertFalse(t.deliver())
        assertEquals(TurnPhase.ENDED, t.phase)
        // Nothing after the end: late worker/orchestrator events are dropped.
        assertNull(t.milestone(TurnMilestone.TOOL_STARTED, "Akku"))
        assertNull(t.acknowledge())
    }

    @Test fun onlyRealMilestonesAreShownDeduplicatedAndRateLimited() {
        val t = turn(); t.acknowledge()
        assertNotNull(t.milestone(TurnMilestone.TOOL_STARTED, "Akku prüfen"))
        assertNull(t.milestone(TurnMilestone.TOOL_STARTED, "Akku prüfen"))     // duplicate
        now += 200
        assertNull(t.milestone(TurnMilestone.TOOL_VERIFIED, "Akku prüfen"))    // too soon
        assertNotNull(t.milestone(TurnMilestone.APPROVAL_NEEDED, "App stoppen")) // always shown
        now += 2_000
        assertNotNull(t.milestone(TurnMilestone.TOOL_VERIFIED, "Akku prüfen"))
        assertEquals(TurnPhase.WORKING, t.phase)
    }

    @Test fun internalWorkStatesAreNotMilestones() {
        assertNull(TurnLifecycle.milestoneOf(CognitiveWorkState.UNDERSTANDING))
        assertNull(TurnLifecycle.milestoneOf(CognitiveWorkState.PLANNING))
        assertNull(TurnLifecycle.milestoneOf(CognitiveWorkState.VERIFYING))
        assertNull(TurnLifecycle.milestoneOf(CognitiveWorkState.COMPLETED))
        assertEquals(TurnMilestone.APPROVAL_NEEDED, TurnLifecycle.milestoneOf(CognitiveWorkState.WAITING_FOR_APPROVAL))
        assertEquals(TurnMilestone.TOOL_STARTED, TurnLifecycle.milestoneOf(CognitiveWorkState.EXECUTING))
        assertEquals(TurnMilestone.BLOCKED, TurnLifecycle.milestoneOf(CognitiveWorkState.BLOCKED))
    }

    @Test fun milestoneBeforeAcknowledgeAcknowledgesImplicitly() {
        val t = turn()
        assertNotNull(t.milestone(TurnMilestone.WORKER_STARTED, "Recherche"))
        assertNull(t.acknowledge())
    }
}

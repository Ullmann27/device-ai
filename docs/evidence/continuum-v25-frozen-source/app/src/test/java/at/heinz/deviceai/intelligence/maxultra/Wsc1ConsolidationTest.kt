package at.heinz.deviceai.intelligence.maxultra

import at.heinz.deviceai.intelligence.maxultra.Lct1Experiment.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Experiment C (WSC1) on a small world (same procedure as the measured run, fewer chains). */
class Wsc1ConsolidationTest {
    private val run = Wsc1Experiment.run(Wsc1Experiment.Config(chains = 40, seen = 25, wakeRepeats = 2, holdoutPerChain = 3,
        eventChains = 3, extraDistractors = 30, selfTest = 5))
    private fun wrong(name: String) = run.results.first { it.name == name }.outcomes.count { it == Outcome.WRONG }

    @Test fun habitsNeverDisagreeWithFreshReasoningAfterTheKnowledgeChanged() {
        assertEquals(0, wrong("FULL"))
        assertEquals(0, wrong("CACHE"))
        assertEquals("a habit answers only while everything it read is unchanged", 0, wrong("WSC1"))
        assertTrue("previously solved questions are served from habits", run.results.first { it.name == "WSC1" }.hits > 0)
        assertTrue("the control without dependencies gives stale answers", wrong("NAIVE") > 0)
    }

    @Test fun theGateRejectsUnverifiedClaimsAndFlawedHabitsAndLeavesRecomputableReceipts() {
        assertTrue(run.selfTest.keys.any { it.startsWith("UNVERIFIED_") } && run.selfTest.keys.any { it.startsWith("UNBOUNDED_WINDOW") } &&
            run.selfTest.keys.any { it.startsWith("MISSING_DEPENDENCY") })
        assertTrue(run.selfTest.toString(), run.selfTest.keys.none { it.endsWith("-> ACCEPTED") })
        assertEquals(run.habits, run.gate["ACCEPTED"])
        assertTrue(run.receiptsMatch)
    }
}

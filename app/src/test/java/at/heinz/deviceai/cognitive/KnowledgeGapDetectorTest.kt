package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeGapDetectorTest {
    @Test fun oneExoticGapDoesNotTriggerResearchProposal() {
        val d=KnowledgeGapDetector()
        d.observe("seltene frage",KnowledgeGapDetector.Outcome.ABSTAINED,1_000,0.1f,0.2f)
        assertTrue(d.proposals(1_000,minimumPriority=0f).isEmpty())
    }

    @Test fun recurringProjectRelevantGapBecomesProposalOnly() {
        val d=KnowledgeGapDetector()
        repeat(4){ i ->
            d.observe("Android Thermal APIs",KnowledgeGapDetector.Outcome.RESEARCH_NEEDED,
                1_000L+i,1f,1f,.5f)
        }
        val p=d.proposals(2_000,minimumPriority=1f)
        assertEquals(1,p.size)
        assertEquals("android thermal apis",p.single().topic)
        assertTrue(p.single().reason.contains("freigabepflichtig"))
    }

    @Test fun answeredObservationsDiluteUncertaintyInsteadOfCreatingFalseGap() {
        val d=KnowledgeGapDetector()
        d.observe("Filament",KnowledgeGapDetector.Outcome.ABSTAINED,1_000,1f,.8f)
        repeat(5){i->d.observe("Filament",KnowledgeGapDetector.Outcome.ANSWERED,2_000L+i,1f,.8f)}
        val state=d.snapshot().single()
        assertEquals(1,state.uncertainObservations)
        assertEquals(6,state.observations)
        assertTrue(d.proposals(3_000,minimumPriority=0f).isEmpty())
    }
}

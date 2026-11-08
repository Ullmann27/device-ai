package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Test

class EpistemicArbitratorTest {
    private fun vector(
        ref: Float=.9f, memory: Float=.9f, retrieval: Float=.9f, source: Float=.9f,
        reasoning: Float=.9f, execution: Float=.9f, alignment: Float=.9f
    ) = EpistemicArbitrator.Vector(ref,memory,retrieval,source,reasoning,execution,alignment)

    @Test fun answerDisagreementWithStrongMemoryForcesVerification() {
        assertEquals(
            EpistemicArbitrator.Decision.VERIFY,
            EpistemicArbitrator.decide(vector(alignment=.2f),false,false,false)
        )
    }

    @Test fun ambiguousReferenceAsksBeforeResearchOrExecution() {
        assertEquals(
            EpistemicArbitrator.Decision.ASK_CLARIFICATION,
            EpistemicArbitrator.decide(vector(ref=.4f,memory=.2f),true,true,true)
        )
    }

    @Test fun factualGapResearchesWhenAllowedOtherwiseAbstains() {
        val weak=vector(memory=.3f,retrieval=.4f,source=.8f)
        assertEquals(EpistemicArbitrator.Decision.RESEARCH,
            EpistemicArbitrator.decide(weak,false,false,true))
        assertEquals(EpistemicArbitrator.Decision.ABSTAIN,
            EpistemicArbitrator.decide(weak,false,false,false))
    }

    @Test fun authorizedExecutionStillRequiresStrongEvidenceAndExecutionConfidence() {
        assertEquals(EpistemicArbitrator.Decision.EXECUTE_AUTHORIZED_TOOL,
            EpistemicArbitrator.decide(vector(),true,true,false))
        assertEquals(EpistemicArbitrator.Decision.VERIFY,
            EpistemicArbitrator.decide(vector(execution=.6f),true,true,false))
        assertEquals(EpistemicArbitrator.Decision.ASK_CLARIFICATION,
            EpistemicArbitrator.decide(vector(),true,false,false))
    }

    @Test fun secureFactsWithWeakReasoningAreQualifiedNotOverclaimed() {
        assertEquals(EpistemicArbitrator.Decision.ANSWER_WITH_QUALIFIER,
            EpistemicArbitrator.decide(vector(reasoning=.4f),false,false,false))
    }
}

package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-module controls for the Code50 cognitive foundations.
 * No model call, network, tool execution or durable memory write occurs here.
 */
class CognitiveArchitectureIntegrationTest {
    @Test fun ambiguousContextCannotReachAuthorizedExecution() {
        val situation=ConversationSituationModel()
        situation.openThread("a","Variante A",listOf("renderer shader"),atTurn=1)
        situation.openThread("b","Variante B",listOf("avatar shader"),atTurn=2)
        situation.pauseActive()
        val resolved=situation.resolveReturn("Nimm den Shader von vorhin",atTurn=3)
        assertTrue(resolved is ConversationSituationModel.Resolution.Ambiguous)

        val decision=EpistemicArbitrator.decide(
            EpistemicArbitrator.Vector(
                referenceResolution=.4f,memory=.95f,retrieval=.95f,source=.95f,
                reasoning=.95f,execution=.95f,answerAlignment=.95f
            ),
            requiresToolExecution=true,toolAuthorized=true,canResearch=true
        )
        assertEquals(EpistemicArbitrator.Decision.ASK_CLARIFICATION,decision)
    }

    @Test fun invalidatedPremiseAndWrongAnswerAreCaughtWithoutSecondModel() {
        val jtms=LightweightJtmsEngine()
        jtms.setFact("build48-fix-x",true)
        jtms.addRule(LightweightJtmsEngine.Rule(
            "build49-fix-x",setOf("build48-fix-x"),setOf("file-x-overwritten")
        ))
        jtms.setFact("file-x-overwritten",true)
        assertEquals(LightweightJtmsEngine.TruthState.BLOCKED_BY_EXCEPTION,jtms.state("build49-fix-x"))

        val verification=AnswerClaimVerifier.verifyFocusedSentence(
            "Build 49 enthält Fix X.",
            AnswerClaimVerifier.GroundedClaim(
                subject="build49",predicate="contains_fix_x",expectedValue="nicht bestätigt",
                forbiddenValues=setOf("enthält fix x"),sourceIds=setOf("jtms:build49-fix-x")
            )
        )
        assertEquals(AnswerClaimVerifier.Verdict.CONTRADICTION,verification.verdict)
    }

    @Test fun hypothesisProbeCanReduceUncertaintyBeforeExpensiveReasoning() {
        val w=HypothesisWorkspace()
        w.add(HypothesisWorkspace.Hypothesis("surface",HypothesisWorkspace.Bucket.UI,"Surface unsichtbar",1f))
        w.add(HypothesisWorkspace.Hypothesis("policy",HypothesisWorkspace.Bucket.CONFIG,"Motion policy blockiert",1f))
        val receipt=HypothesisWorkspace.Probe("frame-receipt",.1f,0f,mapOf("surface" to true,"policy" to false))
        val model=HypothesisWorkspace.Probe("extra-model-call",5_000f,.8f,mapOf("surface" to true,"policy" to false))
        assertEquals("frame-receipt",w.selectNextProbe(listOf(model,receipt))!!.id)
        w.observe(receipt,false,"Surface war sichtbar")
        assertEquals("policy",w.best()!!.id)
    }

    @Test fun counterfactualCannotBecomeActualEvidence() {
        val actual=mapOf("render.fps" to "60")
        val hypothetical=CounterfactualWorldOverlay(actual,"thermal-what-if")
        hypothetical.assume("render.fps","24")
        hypothetical.derive("thermal.result","estimated-lower")
        assertEquals("60",actual["render.fps"])
        assertTrue(hypothetical.labelledSummary().startsWith("[COUNTERFACTUAL_SANDBOX"))
    }

    @Test fun singleSuccessfulExperienceDoesNotCrystallizeAsValidatedPattern() {
        val e=Experience(
            id="one",taskType="agent_task",requestSummary="Prüfe Speicher",
            sourceIds=listOf("tool-receipt"),toolIds=listOf("get_storage_info"),
            outcome=ExperienceOutcome.SUCCESS,startedAt=1,completedAt=2,
            evaluation=Evaluation(true,.95)
        )
        val maturity=ExperiencePatternMaturity.assess(
            listOf(e),"agent_task",listOf("get_storage_info"))
        assertEquals(ExperiencePatternMaturity.Maturity.QUARANTINED_OBSERVATION,maturity.maturity)
    }
}

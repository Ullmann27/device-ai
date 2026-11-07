package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.context.Ctx3Context
import at.heinz.deviceai.learning.context.Ctx3Decision
import at.heinz.deviceai.learning.context.Ctx3Entity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic foundation controls for the 12 target cognitive problem classes.
 * This is NOT a Gemma benchmark and does not claim human-level dialogue quality.
 */
class ContinuumCognitiveBench12Test {
    @Test fun c01_pronounAmbiguityFailsClosed() {
        val c=Ctx3Context()
        val a=Ctx3Entity("a","Alina",setOf("Alina"),setOf("sie"),setOf("a1"))
        val z=Ctx3Entity("z","Zoey",setOf("Zoey"),setOf("sie"),setOf("z1"))
        c.decide("Alina und Zoey",listOf(a,z),"v")
        assertTrue(c.decide("Wie alt ist sie?",listOf(a,z),"v") is Ctx3Decision.Clarify)
    }

    @Test fun c02_indirectOrderedReferenceUsesEstablishedCandidateOrder() {
        val c=Ctx3Context()
        val a=Ctx3Entity("a","Filament",setOf("Filament"),setOf("es"),setOf("s1"))
        val b=Ctx3Entity("b","Canvas",setOf("Canvas"),setOf("es"),setOf("s2"))
        c.decide("Filament und Canvas",listOf(a,b),"v")
        val r=c.decide("Nimm die zweite Variante",listOf(a,b),"v") as Ctx3Decision.Query
        assertEquals(setOf("s2"),r.sourceIds)
    }

    @Test fun c03_topicReturnReactivatesPausedThread() {
        val m=ConversationSituationModel()
        m.openThread("kart","Lumo Kart",listOf("lumo","driften"),atTurn=1)
        m.addOpenQuestion("kart","Drift")
        m.openThread("thermal","Thermik",listOf("android","hitze"),atTurn=2)
        m.pauseActive()
        val r=m.resolveReturn("Und das Driften von vorhin?",atTurn=8)
        assertEquals("kart",(r as ConversationSituationModel.Resolution.Resolved).thread.id)
    }

    @Test fun c04_partialCorrectionBlocksSupersededFocusedValue() {
        val r=AnswerClaimVerifier.verifyFocusedSentence(
            "Wir verwenden weiterhin Meshy für Texturen und Rigging.",
            AnswerClaimVerifier.GroundedClaim(
                subject="project",predicate="meshy_usage",expectedValue="Texturen",
                forbiddenValues=setOf("Rigging"),sourceIds=setOf("correction")
            )
        )
        assertEquals(AnswerClaimVerifier.Verdict.CONTRADICTION,r.verdict)
    }

    @Test fun c05_temporalStateUsesExplicitValidityRatherThanNewestText() {
        val jtms=LightweightJtmsEngine()
        jtms.setFact("decision-before-avatar",true)
        jtms.addRule(LightweightJtmsEngine.Rule("historical-query-answer",setOf("decision-before-avatar")))
        assertEquals(LightweightJtmsEngine.TruthState.VALID_DERIVED,jtms.state("historical-query-answer"))
    }

    @Test fun c06_hiddenContradictionCannotAverageAway() {
        val decision=EpistemicArbitrator.decide(
            EpistemicArbitrator.Vector(.95f,.95f,.95f,.95f,.9f,.9f,.2f),
            requiresToolExecution=false,toolAuthorized=false,canResearch=false
        )
        assertEquals(EpistemicArbitrator.Decision.VERIFY,decision)
    }

    @Test fun c07_implicitGoalMaySuggestResearchButNeverExecuteIt() {
        val gaps=KnowledgeGapDetector()
        repeat(3){ i->
            gaps.observe("android thermal",KnowledgeGapDetector.Outcome.RESEARCH_NEEDED,
                1000L+i,1f,1f,.5f)
        }
        val proposals=gaps.proposals(2_000,minimumPriority=.5f)
        assertTrue(proposals.isNotEmpty())
        assertTrue(proposals.single().reason.contains("freigabepflichtig"))
    }

    @Test fun c08_multistepLogicInvalidatesDownstreamClaim() {
        val jtms=LightweightJtmsEngine()
        jtms.addRule(LightweightJtmsEngine.Rule("b",setOf("a")))
        jtms.addRule(LightweightJtmsEngine.Rule("c",setOf("b")))
        jtms.setFact("a",true)
        assertEquals(LightweightJtmsEngine.TruthState.VALID_DERIVED,jtms.state("c"))
        jtms.setFact("a",false)
        assertEquals(LightweightJtmsEngine.TruthState.INVALIDATED_DEPENDENCY,jtms.state("c"))
    }

    @Test fun c09_missingInformationBlocksAuthorizedAction() {
        val decision=EpistemicArbitrator.decide(
            EpistemicArbitrator.Vector(.45f,.9f,.9f,.9f,.9f,.9f,.9f),
            requiresToolExecution=true,toolAuthorized=true,canResearch=true
        )
        assertEquals(EpistemicArbitrator.Decision.ASK_CLARIFICATION,decision)
    }

    @Test fun c10_projectContinuationUsesExplicitThreadStateNotRecencyGuess() {
        val m=ConversationSituationModel()
        m.setExplicitScope("Continuum","Verifier","Antworten prüfen")
        m.openThread("verifier","Verifier",listOf("verifier","rot","test"),atTurn=1)
        m.addOpenQuestion("verifier","roter Test")
        m.pauseActive(true)
        val r=m.resolveReturn("Mach beim Verifier weiter",atTurn=50)
        assertEquals("verifier",(r as ConversationSituationModel.Resolution.Resolved).thread.id)
        assertEquals("Continuum",m.snapshot().currentProject)
    }

    @Test fun c11_counterfactualNeverLeaksIntoActualMap() {
        val actual=mutableMapOf("fps" to "60")
        val whatIf=CounterfactualWorldOverlay(actual,"bench-counterfactual")
        whatIf.assume("fps","24")
        assertEquals("24",whatIf.read("fps"))
        assertEquals("60",actual["fps"])
    }

    @Test fun c12_experienceTransferRequiresRepeatedVerifiedEvidence() {
        fun e(id:String)=Experience(
            id=id,taskType="agent_task",requestSummary="Surface prüfen",
            sourceIds=listOf("receipt:$id"),toolIds=listOf("get_device_info"),
            outcome=ExperienceOutcome.SUCCESS,startedAt=1,completedAt=2,
            evaluation=Evaluation(true,.9)
        )
        val one=ExperiencePatternMaturity.assess(listOf(e("1")),"agent_task",listOf("get_device_info"))
        val three=ExperiencePatternMaturity.assess(
            listOf(e("1"),e("2"),e("3")),"agent_task",listOf("get_device_info"))
        assertEquals(ExperiencePatternMaturity.Maturity.QUARANTINED_OBSERVATION,one.maturity)
        assertEquals(ExperiencePatternMaturity.Maturity.VALIDATED_PATTERN,three.maturity)
        assertFalse(one.maturity==three.maturity)
    }
}

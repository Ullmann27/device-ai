package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Test

class LightweightJtmsEngineTest {
    @Test fun derivedClaimTracksRequiredPremise() {
        val jtms=LightweightJtmsEngine()
        jtms.addRule(LightweightJtmsEngine.Rule("feature-x",setOf("module-y")))
        assertEquals(LightweightJtmsEngine.TruthState.INVALIDATED_DEPENDENCY,jtms.state("feature-x"))
        jtms.setFact("module-y",true)
        assertEquals(LightweightJtmsEngine.TruthState.VALID_DERIVED,jtms.state("feature-x"))
        jtms.setFact("module-y",false)
        assertEquals(LightweightJtmsEngine.TruthState.INVALIDATED_DEPENDENCY,jtms.state("feature-x"))
    }

    @Test fun explicitOverrideBlocksInheritedConclusion() {
        val jtms=LightweightJtmsEngine()
        jtms.setFact("build48-has-fix-x",true)
        jtms.addRule(LightweightJtmsEngine.Rule(
            "build49-has-fix-x",
            requiredAllActive=setOf("build48-has-fix-x"),
            blockedIfAnyActive=setOf("file-x-overwritten-after-build48")
        ))
        assertEquals(LightweightJtmsEngine.TruthState.VALID_DERIVED,jtms.state("build49-has-fix-x"))
        jtms.setFact("file-x-overwritten-after-build48",true)
        assertEquals(LightweightJtmsEngine.TruthState.BLOCKED_BY_EXCEPTION,jtms.state("build49-has-fix-x"))
        jtms.setFact("file-x-overwritten-after-build48",false)
        assertEquals(LightweightJtmsEngine.TruthState.VALID_DERIVED,jtms.state("build49-has-fix-x"))
    }

    @Test fun multiStepDependencyPropagatesAndInvalidates() {
        val jtms=LightweightJtmsEngine()
        jtms.addRule(LightweightJtmsEngine.Rule("b",setOf("a")))
        jtms.addRule(LightweightJtmsEngine.Rule("c",setOf("b")))
        jtms.addRule(LightweightJtmsEngine.Rule("d",setOf("c")))
        jtms.setFact("a",true)
        assertEquals(LightweightJtmsEngine.TruthState.VALID_DERIVED,jtms.state("d"))
        jtms.setFact("a",false)
        assertEquals(LightweightJtmsEngine.TruthState.INVALIDATED_DEPENDENCY,jtms.state("d"))
    }

    @Test fun unsupportedPositiveCycleNeverBootstrapsItself() {
        val jtms=LightweightJtmsEngine()
        jtms.addRule(LightweightJtmsEngine.Rule("a",setOf("b")))
        jtms.addRule(LightweightJtmsEngine.Rule("b",setOf("a")))
        assertEquals(LightweightJtmsEngine.TruthState.INVALIDATED_DEPENDENCY,jtms.state("a"))
        assertEquals(LightweightJtmsEngine.TruthState.INVALIDATED_DEPENDENCY,jtms.state("b"))
    }

    @Test fun conflictingRuleForSameConclusionIsRejected() {
        val jtms=LightweightJtmsEngine()
        jtms.addRule(LightweightJtmsEngine.Rule("x",setOf("a")))
        var rejected=false
        try { jtms.addRule(LightweightJtmsEngine.Rule("x",setOf("b"))) }
        catch (_:IllegalArgumentException){rejected=true}
        assertEquals(true,rejected)
    }
}

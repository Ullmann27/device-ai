package at.heinz.deviceai.intelligence.maxultra

import at.heinz.deviceai.intelligence.maxultra.Lct1Experiment.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** Experiment B (LCT1): behaviour on a small generated set (own seed, never the TEST split). */
class Lct1ReasoningTest {
    private val unit = Lct1Experiment.generate("unit", 7L, 2, emptySet()).first

    @Test fun everyVariantIsAnsweredOrRefusedCorrectlyAndTheVerifierAgrees() {
        val l = Lct1.Reasoner(unit.kb)
        val verifier = Lct1.Verifier(unit.kb)
        val rnd = Random(1)
        for (case in unit.cases) {
            val a = l.answer(case.query)
            assertEquals(case.id + " " + a, Outcome.CORRECT, Lct1Experiment.score(case, a, unit.kb))
            assertTrue(case.id, verifier.confirms(case.query, a))
            if (a.kind == Lct1.Kind.ABSTAIN) continue
            for ((kind, mutated) in Lct1Experiment.mutations(case.query, a, unit.kb, rnd))
                assertFalse(case.id + " " + kind, verifier.confirms(mutated.first, mutated.second))
        }
        val b0 = Lct1.EbtsAlgorithm(unit.kb)
        val outdated = unit.cases.first { it.variant == Lct1Experiment.Variant.OUTDATED_ONLY }
        assertEquals("the baseline answers with an expired fact", Outcome.WRONG,
            Lct1Experiment.score(outdated, b0.answer(outdated.query), unit.kb))
        val (agree, cases) = Lct1Experiment.ebtsAgreement(unit, b0)
        assertTrue(cases > 0)
        assertEquals("B0 is the algorithm of EvidenceBoundedThoughtSearch", cases, agree)
    }

    @Test fun generationIsDeterministicAndExcludedNamesNeverReturn() {
        val again = Lct1Experiment.generate("unit", 7L, 2, emptySet()).first
        assertEquals(unit.sha256, again.sha256)
        val other = Lct1Experiment.generate("other", 8L, 2, unit.names).first
        assertTrue(other.names.intersect(unit.names).isEmpty())
    }

    @Test fun theGuideLearnsWithTheUpdateRuleOfEphemeralFastWeightCore() {
        val rnd = Random(3)
        val vectors = List(6) { DoubleArray(16) { rnd.nextGaussian() * 3 } }
        val core = EphemeralFastWeightCore()
        val guide = Lct1.FastWeightGuide(1L)
        vectors.forEach { core.observe(it) }
        vectors.zipWithNext { a, b -> guide.learn(Lct1.FastWeightGuide.normalize(a), Lct1.FastWeightGuide.normalize(b)) }
        assertEquals(core.updates, guide.updates)
        assertEquals(core.weightNorm(), guide.weightNorm(), 1e-12)
    }
}

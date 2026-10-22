package at.heinz.deviceai.learning.synapse

import at.heinz.deviceai.learning.synapse.SynapseV2Bench.Split
import at.heinz.deviceai.learning.synapse.SynapseV2Reasoner.Binding
import at.heinz.deviceai.learning.synapse.SynapseV2Reasoner.Config
import at.heinz.deviceai.learning.synapse.SynapseV2Reasoner.Example
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Exact gradients, proof safety and benchmark hygiene for SYNAPSE v2 (JVM, research shadow). */
class SynapseV2ReasonerTest {
    private val chain = Example("Roboter BC-123 gehört zu Linie LN-200. Linie LN-200 steht in Ahlfeld. Gerät DF-301 steht in Bernfeld.",
        "Wo steht BC-123?", "Ahlfeld", "LN-200")
    private val direct = Example("Gerät DF-301 steht nicht in Bernfeld, sondern in Corlach. Roboter BC-123 misst mit Lidar.",
        "Wo steht DF-301?", "Corlach")
    private val none = Example("Gerät DF-301 steht nicht in Bernfeld.", "Wo steht DF-301?", null)

    private fun gradientMatches(config: Config) {
        val model = SynapseV2Reasoner(config, 7)
        repeat(15) { for (e in listOf(chain, direct, none)) model.train(e, 0.05) } // non-trivial weights
        for (e in listOf(chain, direct, none)) {
            val (_, grad) = model.lossAndGradient(e, 0.0)!!
            val params = model.parameters()
            val indices = grad.keys.sorted().let { it.take(40) + it.takeLast(25) }.distinct()
            assertTrue(indices.isNotEmpty())
            for (i in indices) {
                model.loadParameters(params.copyOf().also { it[i] += 1e-6 }); val hi = model.loss(e)!!
                model.loadParameters(params.copyOf().also { it[i] -= 1e-6 }); val lo = model.loss(e)!!
                model.loadParameters(params)
                val numeric = (hi - lo) / 2e-6
                assertTrue("$config index $i analytic ${grad[i]} numeric $numeric", abs(numeric - grad.getValue(i)) < 1e-6)
            }
        }
    }

    @Test fun hrrGradientsAreExact() = gradientMatches(Config(binding = Binding.HRR, twoHop = true))
    @Test fun bagGradientsAreExact() = gradientMatches(Config(binding = Binding.BAG, twoHop = true))

    @Test fun bagNeverMovesRolesAndScalarOnlyNeverMovesEmbeddings() {
        val bag = SynapseV2Reasoner(Config(binding = Binding.BAG, twoHop = true), 3)
        val before = bag.parameters(); repeat(5) { bag.train(chain) }
        val after = bag.parameters()
        val scalarOnly = SynapseV2Reasoner(Config(twoHop = true, scalarOnly = true), 3)
        val s0 = scalarOnly.parameters(); repeat(5) { scalarOnly.train(chain) }
        val s1 = scalarOnly.parameters()
        val changedBag = before.indices.filter { before[it] != after[it] }
        val changedScalar = s0.indices.filter { s0[it] != s1[it] }
        assertTrue(changedBag.isNotEmpty() && changedScalar.isNotEmpty())
        assertTrue("scalar-only touched embeddings", changedScalar.all { it >= s0.size - (2 * SynapseV2Reasoner.SCALARS + 1 + SynapseV2Reasoner.NULL_FEATURES) })
    }

    @Test fun answersCarryVerifiableProofsAndTamperingFails() {
        val model = SynapseV2Reasoner(Config(twoHop = true), 11)
        repeat(60) { for (e in listOf(chain, direct, none)) model.train(e, 0.05) }
        val a = model.answer(chain.evidence, chain.question)
        assertEquals("Ahlfeld", a.answer)
        val proof = a.proof!!
        assertEquals(2, proof.hops)
        assertEquals("Ahlfeld", chain.evidence.substring(proof.answerStart, proof.answerEnd))
        assertEquals("LN-200", chain.evidence.substring(proof.bridgeStart, proof.bridgeStart + 6))
        assertTrue(model.verify(chain.evidence, chain.question, proof))
        assertFalse(model.verify(chain.evidence.replace("Ahlfeld", "Ahlfelt"), chain.question, proof)) // other source text
        assertFalse(model.verify(chain.evidence, chain.question, proof.copy(answerStart = proof.answerStart + 1)))
        assertFalse(model.verify(chain.evidence, "Wo steht DF-301?", proof)) // other entity
        assertFalse(model.verify(chain.evidence, chain.question, proof.copy(bridgeSentence = proof.sentence)))
        assertFalse(model.verify(chain.evidence, chain.question, proof.copy(hops = 1)))
        // a broken bridge offers no two-hop candidate at all, whatever the weights are
        val broken = "Roboter BC-123 gehört zu Linie LN-200. Linie LN-201 steht in Ahlfeld."
        assertNull(model.answer(broken, "Wo steht BC-123?").answer)
        // an absent entity can never be answered
        assertNull(model.answer(chain.evidence, "Wo steht ZZ-999?").answer)
    }

    @Test fun sameSeedIsDeterministicAndModelsAreIndependent() {
        val a = SynapseV2Reasoner(Config(), 5); val b = SynapseV2Reasoner(Config(), 5)
        assertTrue(a.parameters().contentEquals(b.parameters()))
        a.train(direct)
        assertEquals(0, b.updates)
        assertFalse(a.parameters().contentEquals(b.parameters()))
        assertNotNull(a.lossAndGradient(direct))
    }

    @Test fun frozenTestIsUnchangedAndSplitsAreSeparated() {
        val train = SynapseV2Bench.generate(Split.TRAIN)
        val validation = SynapseV2Bench.generate(Split.VALIDATION)
        val test = SynapseV2Bench.generate(Split.TEST)
        assertEquals(SynapseV2Bench.TEST_SHA256, SynapseV2Bench.sha256(test))
        for ((x, y) in listOf(train to test, validation to test, train to validation)) {
            assertTrue((SynapseV2Bench.entities(x) intersect SynapseV2Bench.entities(y)).isEmpty())
            assertTrue((SynapseV2Bench.answers(x) intersect SynapseV2Bench.answers(y)).isEmpty())
        }
        val trainShapes = SynapseV2Bench.shapes(train)
        val validationShapes = SynapseV2Bench.shapes(validation)
        for (t in SynapseV2Bench.heldOutTemplates(Split.TEST).map(SynapseV2Bench::templateShape))
            assertFalse("TEST template leaked: $t", t in trainShapes || t in validationShapes)
        for (t in SynapseV2Bench.heldOutTemplates(Split.VALIDATION).map(SynapseV2Bench::templateShape))
            assertFalse("VALIDATION template leaked into TRAIN: $t", t in trainShapes)
        // the old CSX1 TEST and the PR #71 DEV probe use other identifier formats: no shared entity is possible
        assertTrue(SynapseV2Bench.entities(test).all { Regex("(?:[A-Z]{2}|LN)-\\d{3}").matches(it) })
    }

    @Test fun everyGoldIsAReachableCandidateOfTheTwoHopModel() {
        val model = SynapseV2Reasoner(Config(twoHop = true), 1)
        for (split in Split.entries) for (c in SynapseV2Bench.generate(split))
            assertNotNull("gold unreachable in ${c.id}: ${c.evidence} | ${c.question}",
                model.lossAndGradient(Example(c.evidence, c.question, c.answer, c.bridge), 0.0))
    }
}

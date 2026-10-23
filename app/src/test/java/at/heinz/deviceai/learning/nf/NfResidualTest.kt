package at.heinz.deviceai.learning.nf

import at.heinz.deviceai.learning.lm.KneserNeyBytes
import at.heinz.deviceai.learning.lm.LmBpeTokenizer
import at.heinz.deviceai.learning.lm.LmCorpus
import at.heinz.deviceai.learning.lm.Mat
import at.heinz.deviceai.learning.lm.Tape
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

class NfResidualTest {
    private val rnd = Random(9)
    private fun logits(t: Int, v: Int) = Mat(t, v, FloatArray(t * v) { (rnd.nextGaussian() * .8).toFloat() })
    private val targets = intArrayOf(2, -1, 0, 4)
    private val q = doubleArrayOf(.3, .5, .05, 0.0)

    private fun lossAndGrad(m: Mat, lambda: Double?): Pair<Double, FloatArray> {
        val tape = Tape(); val g = Mat(m.rows, m.cols)
        val out = if (lambda == null) tape.crossEntropy(tape.param(m, g), targets) else tape.mixtureCrossEntropy(tape.param(m, g), targets, q, lambda)
        tape.backward(out)
        return out.value.data[0].toDouble() to g.data
    }

    @Test fun withLambdaOneTheMixtureLossIsTheCrossEntropy() {
        val m = logits(4, 5)
        val (a, ga) = lossAndGrad(m, null)
        val (b, gb) = lossAndGrad(m, 1.0)
        assertEquals(a, b, 1e-5)
        assertArrayEquals(ga, gb, 1e-6f)
    }

    @Test fun theMixtureLossHasTheExactGradientAndMatchesItsDefinition() {
        val m = logits(4, 5)
        val lambda = .4
        val (value, grad) = lossAndGrad(m, lambda)
        var manual = 0.0
        for (r in targets.indices) {
            val y = targets[r]; if (y < 0) continue
            val row = (0 until 5).map { exp(m.data[r * 5 + it].toDouble()) }
            manual -= ln(lambda * row[y] / row.sum() + (1 - lambda) * q[r])
        }
        assertEquals(manual / 3, value, 1e-5)
        val eps = 1e-3f
        for (i in m.data.indices) {
            val keep = m.data[i]
            m.data[i] = keep + eps; val up = lossAndGrad(m, lambda).first
            m.data[i] = keep - eps; val down = lossAndGrad(m, lambda).first
            m.data[i] = keep
            val numeric = (up - down) / (2 * eps)
            assertTrue("coordinate $i: analytic ${grad[i]} numeric $numeric", abs(numeric - grad[i]) <= 2e-3 * maxOf(1.0, abs(numeric)))
        }
        assertTrue("ignored target has no gradient", (0 until 5).all { grad[5 + it] == 0f })
    }

    @Test fun positionsTheCountModelAlreadyPredictsTeachTheNetworkLess() {
        val m = logits(1, 5)
        fun gradNorm(qy: Double): Double {
            val tape = Tape(); val g = Mat(1, 5)
            tape.backward(tape.mixtureCrossEntropy(tape.param(m, g), intArrayOf(1), doubleArrayOf(qy), .5))
            return g.data.sumOf { abs(it.toDouble()) }
        }
        assertTrue(gradNorm(.9) < gradNorm(.01) / 5)
    }

    @Test fun nfc2KeepsSpentAndReservedTestDocumentsOutAndSplitsDeterministically() {
        val paths = (0 until 400).map { "docs/X-$it.md" }
        for (p in paths) {
            assertEquals(NfCorpus2.splitOf(p), NfCorpus2.splitOf(p))
            if (LmCorpus.splitOf(p) == LmCorpus.Split.TEST || NfCorpus.splitOf(p) == LmCorpus.Split.TEST) assertFalse(NfCorpus2.eligible(p))
        }
        val counts = paths.filter { NfCorpus2.eligible(it) }.groupingBy { NfCorpus2.splitOf(it) }.eachCount()
        assertTrue(counts.getValue(LmCorpus.Split.TRAIN) > 4 * counts.getValue(LmCorpus.Split.VALIDATION))
        assertFalse(NfCorpus2.eligible("docs/evidence/nf-20261022/README.md"))
        assertFalse(NfCorpus2.eligible("docs/CONTINUUM-NEURAL-FRONTIER-LAB-20261022.md"))
        assertFalse(NfCorpus2.eligible("THIRD_PARTY_NOTICES.md"))
    }

    @Test fun countModelProbabilitiesAreAlignedWithTheByteTargets() {
        val kn = KneserNeyBytes(7, listOf("der kern lernt", "der kern prüft"))
        val tok = LmBpeTokenizer.bytes()
        val (_, t) = NfExperiment.pair(tok, "der kern", 256)
        val q = NfResidual.targetProbabilities(kn, "der kern", t.size)
        assertEquals(9, q.size)
        assertEquals(0.0, q.last(), 0.0)
        assertArrayEquals(kn.detail("der kern").prob, q.copyOf(8), 0.0)
    }
}

package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Arithmetic of experiment D's evaluation; the measured run itself is documented in docs/research. */
class Cdx1PatchAnomalyExperimentTest {
    @Test fun aurocCountsTiesAsHalfAndOrdersCorrectly() {
        assertEquals(1.0, Cdx1PatchAnomalyExperiment.auroc(listOf(3.0, 4.0), listOf(1.0, 2.0)), 0.0)
        assertEquals(0.0, Cdx1PatchAnomalyExperiment.auroc(listOf(1.0), listOf(2.0)), 0.0)
        assertEquals(0.5, Cdx1PatchAnomalyExperiment.auroc(listOf(1.0), listOf(1.0)), 0.0)
        assertTrue(Cdx1PatchAnomalyExperiment.auroc(emptyList(), listOf(1.0)).isNaN())
    }

    @Test fun maxWindowFindsTheLocalAnomalyThatTheMeanDilutes() {
        val bits = DoubleArray(64) { 2.0 }.also { for (i in 30 until 46) it[i] = 10.0 }
        assertEquals(10.0, Cdx1PatchAnomalyExperiment.maxWindow(bits), 1e-12)
        assertTrue(bits.average() < 5.0)
        assertEquals(3.0, Cdx1PatchAnomalyExperiment.maxWindow(doubleArrayOf(2.0, 4.0)), 1e-12)
    }

    @Test fun nearestRankPercentileAndNgramSurprisalBehave() {
        assertEquals(95.0, Cdx1PatchAnomalyExperiment.percentile95((1..100).map { it.toDouble() }), 0.0)
        val ngram = Cdx1PatchAnomalyExperiment.ByteNgram().also { repeat(20) { _ -> it.train("return a===b;\n") } }
        val seen = ngram.surprisalBits("return a===b;\n")
        val odd = ngram.surprisalBits("return a​\u0007=b;\n")
        assertTrue(seen.all { it.isFinite() && it >= 0.0 } && odd.all { it.isFinite() })
        assertTrue(Cdx1PatchAnomalyExperiment.maxWindow(odd) > Cdx1PatchAnomalyExperiment.maxWindow(seen))
    }
}

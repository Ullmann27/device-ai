package at.heinz.deviceai.learning.nf

import at.heinz.deviceai.learning.lm.KneserNeyBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.ln

/** NF-6b-lite mixture arithmetic: limits, gate without slope, fitting never hurts the fitted half, n-gram detail. */
class NfHybridTest {
    private fun sample(n: Int, seed: Long): NfHybrid.Bytes {
        val r = Random(seed)
        return NfHybrid.Bytes(DoubleArray(n) { .05 + .9 * r.nextDouble() }, DoubleArray(n) { .02 + .9 * r.nextDouble() },
            IntArray(n) { 1 + r.nextInt(7) }, LongArray(n) { r.nextInt(500).toLong() })
    }

    @Test fun fixedMixtureLimitsAreTheComponents() {
        val d = sample(300, 1)
        val neural = d.neural.sumOf { -ln(it) } / ln(2.0)
        val kn = d.kn.sumOf { -ln(it) } / ln(2.0)
        assertEquals(neural, NfHybrid.fixedBits(d, 1.0), 1e-9)
        assertEquals(kn, NfHybrid.fixedBits(d, 0.0), 1e-9)
    }

    @Test fun aGateWithoutSlopeIsTheFixedMixture() {
        val d = sample(300, 2)
        val lambda = .3
        assertEquals(NfHybrid.fixedBits(d, lambda), NfHybrid.gateBits(d, doubleArrayOf(ln(lambda / (1 - lambda)), 0.0, 0.0)), 1e-9)
    }

    @Test fun fittingTheGateNeverWorsensTheFittedData() {
        // the neural model is better exactly where the n-gram context was rarely seen: a gate can learn that
        val r = Random(3)
        val n = 2000
        val count = LongArray(n) { if (r.nextBoolean()) 0L else 400L }
        val d = NfHybrid.Bytes(DoubleArray(n) { if (count[it] == 0L) .6 else .1 }, DoubleArray(n) { if (count[it] == 0L) .05 else .7 },
            IntArray(n) { if (count[it] == 0L) 1 else 7 }, count)
        val lambda = NfHybrid.bestFixed(d)
        val fixed = NfHybrid.fixedBits(d, lambda)
        val gate = NfHybrid.gateBits(d, NfHybrid.fitGate(d, lambda))
        assertTrue("gate $gate vs fixed $fixed", gate < fixed - .05 * n)
    }

    @Test fun ngramDetailMatchesItsBits() {
        val kn = KneserNeyBytes(7, listOf("der Coding-Agent prüft jede Quittung", "die Quittung wird geprüft", "der Agent lernt"))
        val text = "der Agent prüft die Quittung"
        val d = kn.detail(text)
        assertEquals(kn.bits(text), d.prob.sumOf { -ln(it) } / ln(2.0), 1e-9)
        assertTrue(d.order.all { it in 1..7 } && d.count.all { it > 0 })
    }

    @Test fun foldsAreDeterministicAndBothUsed() {
        val paths = (1..40).map { "docs/doc-$it.md" }
        assertEquals(paths.map { NfHybrid.fold(it) }, paths.map { NfHybrid.fold(it) })
        assertEquals(setOf(0, 1), paths.map { NfHybrid.fold(it) }.toSet())
    }
}

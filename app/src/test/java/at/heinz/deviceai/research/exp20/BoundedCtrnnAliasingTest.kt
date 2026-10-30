package at.heinz.deviceai.research.exp20

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs

/**
 * EXP-20 P2 · technical falsification of the P1 claim "weights are fixed at construction".
 * The P1 constructor keeps references to the caller's arrays, so a later write by the array holder changes tau, W or b
 * after the init-time validation. These tests must fail on the unmodified P1 class (ed408a7) and pass after the fix.
 */
class BoundedCtrnnAliasingTest {
    private fun arrays() = Triple(doubleArrayOf(1.0, 2.0), arrayOf(doubleArrayOf(0.2, 0.1), doubleArrayOf(0.0, 0.3)), doubleArrayOf(0.0, 0.1))

    @Test fun externalWritesAfterConstructionDoNotChangeTheNetwork() {
        val (tau, w, b) = arrays()
        val net = BoundedCtrnn(tau, w, b)
        val (tau2, w2, b2) = arrays()
        val reference = BoundedCtrnn(tau2, w2, b2)
        val rho = net.rho(); val bound0 = net.bound(0)
        tau[0] = -1.0; w[0][1] = Double.NaN; w[1][1] = 50.0; b[1] = 1e300      // writes that init() would have rejected
        net.reset(doubleArrayOf(0.5, -0.5)); reference.reset(doubleArrayOf(0.5, -0.5))
        repeat(20) { assertTrue(net.step(doubleArrayOf(0.3, -0.2), 0.5)); reference.step(doubleArrayOf(0.3, -0.2), 0.5) }
        val s = net.state()
        assertTrue("state must stay finite", s.all { it.isFinite() })
        assertArrayEquals("state must equal the unmutated twin bit for bit", reference.state(), s, 0.0)
        assertEquals(rho, net.rho(), 0.0)
        assertEquals(bound0, net.bound(0), 0.0)
    }

    @Test fun leakyBaselineRejectsInvalidTauAtConstruction() {
        for (bad in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            try { LeakyIntegrators(doubleArrayOf(1.0, bad)); fail("tau=$bad accepted") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun leakyBaselineIsNotChangedByExternalTauWrites() {
        val tau = doubleArrayOf(1.0)
        val ema = LeakyIntegrators(tau)
        tau[0] = -1.0                                                         // a = exp(+dt) would leave the convex hull
        repeat(10) { assertTrue(ema.step(doubleArrayOf(0.5), 2.0)) }
        assertTrue("|h| <= max input 0.5, got ${ema.first()}", abs(ema.first()) <= 0.5 + 1e-12)
    }
}

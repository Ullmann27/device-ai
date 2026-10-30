package at.heinz.deviceai.research.exp20

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random

/** EXP-20 P1 · H20a exactly as preregistered (docs/research/exp20/EXP-20-P1-PREREGISTRATION-20261030.md). */
class BoundedCtrnnTest {
    private fun logUniform(r: Random, lo: Double, hi: Double) = exp(r.nextDouble(kotlin.math.ln(lo), kotlin.math.ln(hi)))

    private fun randomNet(r: Random): BoundedCtrnn {
        val n = r.nextInt(2, 33)
        val rho = r.nextDouble(0.0, 0.95)
        val w = Array(n) { DoubleArray(n) { r.nextDouble(-1.0, 1.0) } }
        for (row in w) { val s = row.sumOf { abs(it) }; if (s > 0) for (j in row.indices) row[j] *= rho / s }
        return BoundedCtrnn(DoubleArray(n) { logUniform(r, 0.05, 20.0) }, w, DoubleArray(n) { r.nextDouble(-0.5, 0.5) })
    }

    @Test fun h20aBoundContractionDeterminismOver10000Trials() {
        val r = Random(20261030)
        var boundViolations = 0; var nonFinite = 0; var contractionViolations = 0; var nonDeterministic = 0
        repeat(10_000) {
            val seed = r.nextLong()
            val a = randomNet(Random(seed)); val g = randomNet(Random(seed)); val replay = randomNet(Random(seed))
            val n = a.size
            val h0 = DoubleArray(n) { r.nextDouble(-5.0, 5.0) }
            a.reset(h0); replay.reset(h0); g.reset(DoubleArray(n) { r.nextDouble(-5.0, 5.0) })
            repeat(200) {
                val x = DoubleArray(n) { r.nextDouble(-3.0, 3.0) }
                val dt = logUniform(r, 1e-4, 50.0)
                val before = a.state().zip(g.state().toList()).maxOf { (p, q) -> abs(p - q) }
                a.step(x, dt); g.step(x, dt); replay.step(x, dt)
                val s = a.state(); val t = g.state()
                for (i in 0 until n) {
                    if (!s[i].isFinite() || !t[i].isFinite()) nonFinite++
                    if (abs(s[i]) > maxOf(abs(h0[i]), a.bound(i)) + 1e-12) boundViolations++
                }
                val after = s.zip(t.toList()).maxOf { (p, q) -> abs(p - q) }
                if (after > a.contractionFactor(dt) * before + 1e-12) contractionViolations++
                if (!s.contentEquals(replay.state())) nonDeterministic++
            }
        }
        assertEquals("bound", 0, boundViolations)
        assertEquals("non-finite", 0, nonFinite)
        assertEquals("contraction", 0, contractionViolations)
        assertEquals("determinism", 0, nonDeterministic)
    }

    @Test fun invalidStepsFailClosedAndLeaveTheStateUnchanged() {
        val net = BoundedCtrnn(doubleArrayOf(1.0, 2.0), arrayOf(doubleArrayOf(0.2, 0.1), doubleArrayOf(0.0, 0.3)), doubleArrayOf(0.0, 0.1))
        net.reset(doubleArrayOf(0.5, -0.5))
        val before = net.state()
        for ((x, dt) in listOf(doubleArrayOf(0.1, 0.1) to Double.NaN, doubleArrayOf(0.1, 0.1) to Double.POSITIVE_INFINITY,
            doubleArrayOf(0.1, 0.1) to 0.0, doubleArrayOf(0.1, 0.1) to -1.0, doubleArrayOf(Double.NaN, 0.1) to 0.1,
            doubleArrayOf(0.1, Double.NEGATIVE_INFINITY) to 0.1, doubleArrayOf(0.1) to 0.1)) {
            assertFalse(net.step(x, dt))
            assertArrayEquals(before, net.state(), 0.0)
        }
        assertTrue(net.step(doubleArrayOf(0.1, 0.1), 0.1))
    }

    /** Negative control: without rho < 1 there is no contraction (two stable fixed points), so the test discriminates. */
    @Test fun withoutTheContractionConditionTwoTrajectoriesStayApart() {
        val w = arrayOf(doubleArrayOf(3.0))
        val up = BoundedCtrnn(doubleArrayOf(1.0), w, doubleArrayOf(0.0)).apply { reset(doubleArrayOf(2.0)) }
        val down = BoundedCtrnn(doubleArrayOf(1.0), w, doubleArrayOf(0.0)).apply { reset(doubleArrayOf(-2.0)) }
        repeat(1000) { up.step(doubleArrayOf(0.0), 0.1); down.step(doubleArrayOf(0.0), 0.1) }
        assertTrue(abs(up.state()[0] - down.state()[0]) > 1.0)
        assertTrue(up.rho() >= 1.0)
    }
}

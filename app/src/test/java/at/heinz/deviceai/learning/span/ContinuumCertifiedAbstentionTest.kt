package at.heinz.deviceai.learning.span

import at.heinz.deviceai.learning.span.ContinuumCertifiedAbstention.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.util.Random
import kotlin.math.abs
import kotlin.math.pow

/** Learn-then-Test threshold: exact binomial tail, fail-closed edges and the statistical guarantee by simulation. */
class ContinuumCertifiedAbstentionTest {
    private val sha = "a".repeat(64)

    @Test fun binomialCdfIsExact() {
        fun exact(k: Int, n: Int, p: Double): Double {
            var sum = BigDecimal.ZERO
            val bp = BigDecimal(p); val bq = BigDecimal.ONE - bp
            for (i in 0..k) {
                var c = BigInteger.ONE
                for (j in 0 until i) c = c * BigInteger.valueOf((n - j).toLong()) / BigInteger.valueOf((j + 1).toLong())
                sum += BigDecimal(c) * bp.pow(i) * bq.pow(n - i)
            }
            return sum.round(MathContext.DECIMAL64).toDouble()
        }
        for ((k, n, p) in listOf(Triple(0, 10, 0.3), Triple(3, 10, 0.3), Triple(9, 10, 0.3), Triple(12, 300, 0.05),
                Triple(70, 1700, 0.05), Triple(5, 2000, 0.01))) {
            val e = exact(k, n, p)
            assertTrue("k=$k n=$n p=$p: ${ContinuumCertifiedAbstention.binomialCdf(k, n, p)} vs $e",
                abs(ContinuumCertifiedAbstention.binomialCdf(k, n, p) - e) <= 1e-12 + 1e-9 * e)
        }
        assertEquals(0.0, ContinuumCertifiedAbstention.binomialCdf(-1, 10, 0.3), 0.0)
        assertEquals(1.0, ContinuumCertifiedAbstention.binomialCdf(10, 10, 0.3), 0.0)
    }

    @Test fun tooFewCalibrationQuestionsMeansAlwaysAbstain() {
        // (1 - 0.05)^20 = 0.36 > delta: not even zero errors can be certified
        val outcomes = List(20) { Outcome(true, 0.99, true) }
        val certificate = ContinuumCertifiedAbstention.calibrate(sha, outcomes, alpha = 0.05, delta = 0.1)
        assertNull(certificate)
        val p = ContinuumResidualSpanCore.Prediction("Graz", 0, 4, 0.99, 3)
        assertNull(ContinuumCertifiedAbstention.answer(p, certificate))
    }

    @Test fun fixedSequenceStopsAtTheFirstFailureAndNeverSkipsAhead() {
        // errors only between 0.50 and 0.60; below 0.50 everything is correct again. The sequence must stop above 0.60.
        val outcomes = List(400) { Outcome(true, 0.95, true) } + List(60) { Outcome(true, 0.55, false) } +
            List(400) { Outcome(true, 0.2, true) }
        val c = ContinuumCertifiedAbstention.calibrate(sha, outcomes, alpha = 0.05, delta = 0.1)!!
        assertEquals(0.56, c.threshold, 1e-12)
        assertEquals(0, c.calibrationErrors)
        assertEquals(400, c.calibrationAnswered)
        assertTrue(c.pValue <= 0.1)
    }

    /**
     * Simulated calibrated model: confidence c ~ U(0,1), correct with probability c, so the true risk is
     * R(t) = (1 - t)^2 / 2. Over 2,000 independent calibrations the certified threshold may exceed alpha in at most
     * delta of the runs (plus binomial slack of the simulation itself), and it must not be trivially strict.
     */
    @Test fun guaranteeHoldsInSimulation() {
        val rnd = Random(20261022L)
        val alpha = 0.05; val delta = 0.1; val runs = 2000
        var violations = 0; var thresholds = 0.0
        repeat(runs) {
            val outcomes = List(300) { val c = rnd.nextDouble(); Outcome(true, c, rnd.nextDouble() < c) }
            val t = ContinuumCertifiedAbstention.calibrate(sha, outcomes, alpha, delta)?.threshold ?: 1.0
            if ((1 - t).pow(2) / 2 > alpha) violations++
            thresholds += t
        }
        assertTrue("violation rate ${violations.toDouble() / runs}", violations.toDouble() / runs <= delta + 0.02)
        // oracle threshold is 1 - sqrt(0.1) = 0.684; a useful procedure stays close to it
        assertTrue("mean threshold ${thresholds / runs}", thresholds / runs < 0.80)
    }
}

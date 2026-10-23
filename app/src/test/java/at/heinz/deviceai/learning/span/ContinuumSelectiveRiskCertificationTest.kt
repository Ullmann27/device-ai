package at.heinz.deviceai.learning.span

import at.heinz.deviceai.learning.span.ContinuumCertifiedAbstention.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.util.Random
import kotlin.math.abs
import kotlin.math.pow

/** I2: exact binomial bounds, Holm step-down, fail-closed edges and the selective-risk guarantee by simulation. */
class ContinuumSelectiveRiskCertificationTest {
    private val sha = "b".repeat(64)
    private val grid = ContinuumCertifiedAbstention.GRID

    /** P(Bin(n, p) <= k) with BigDecimal arithmetic (independent of the log-space implementation). */
    private fun exactCdf(k: Int, n: Int, p: Double): Double {
        val mc = MathContext(80)
        val bp = BigDecimal(p); val bq = BigDecimal.ONE - bp
        var sum = BigDecimal.ZERO
        var c = BigInteger.ONE
        for (i in 0..k) {
            if (i > 0) c = c * BigInteger.valueOf((n - i + 1).toLong()) / BigInteger.valueOf(i.toLong())
            sum += BigDecimal(c).multiply(bp.pow(i, mc), mc).multiply(bq.pow(n - i, mc), mc)
        }
        return sum.round(MathContext.DECIMAL64).toDouble()
    }

    @Test fun clopperPearsonUpperBoundIsExact() {
        for ((k, n, level) in listOf(Triple(0, 50, 0.05), Triple(3, 50, 0.01), Triple(12, 300, 0.001),
                Triple(26, 576, 0.1 / 101), Triple(49, 50, 0.05), Triple(0, 343, 0.1 / 101), Triple(70, 708, 0.1 / 101))) {
            val u = ContinuumSelectiveRiskCertification.clopperPearsonUpper(k, n, level)
            val f = exactCdf(k, n, u)
            assertTrue("k=$k n=$n level=$level u=$u F(u)=$f", abs(f - level) <= 1e-8 * level)
            assertTrue(u >= k.toDouble() / n)
        }
        // closed forms: k = 0 gives 1 - level^(1/n); k = n gives 1
        assertEquals(1 - 0.001.pow(1.0 / 200), ContinuumSelectiveRiskCertification.clopperPearsonUpper(0, 200, 0.001), 1e-12)
        assertEquals(1.0, ContinuumSelectiveRiskCertification.clopperPearsonUpper(7, 7, 0.05), 0.0)
    }

    @Test fun pValueTestAndClopperPearsonBoundMakeTheSameDecision() {
        val rnd = Random(7L)
        repeat(400) {
            val n = 1 + rnd.nextInt(800); val k = rnd.nextInt(n + 1)
            val alpha = 0.005 + rnd.nextDouble() * 0.3; val level = 0.0005 + rnd.nextDouble() * 0.2
            val u = ContinuumSelectiveRiskCertification.clopperPearsonUpper(k, n, level)
            if (abs(u - alpha) < 1e-9) return@repeat
            val rejectByP = ContinuumCertifiedAbstention.binomialCdf(k, n, alpha) <= level
            assertEquals("k=$k n=$n alpha=$alpha level=$level u=$u", u <= alpha, rejectByP)
        }
    }

    @Test fun holmIsStepDownAndStopsAtTheFirstFailure() {
        val levels = ContinuumSelectiveRiskCertification.holm(listOf(0.03, 0.001, 0.5, 0.02), delta = 0.1)
        assertEquals(0.05, levels[0]!!, 1e-15)
        assertEquals(0.025, levels[1]!!, 1e-15)
        assertNull(levels[2])
        assertEquals(0.1 / 3, levels[3]!!, 1e-15)
        // 0.026 > 0.1/4: nothing is rejected, although 0.026 would pass the last critical value 0.1/1
        assertTrue(ContinuumSelectiveRiskCertification.holm(List(4) { 0.026 }, delta = 0.1).all { it == null })
    }

    @Test fun tooFewAnswersFailClosed() {
        val outcomes = List(20) { Outcome(true, 0.99, true) } + List(1980) { Outcome(false, 0.7, false) }
        assertNull(ContinuumSelectiveRiskCertification.calibrate(sha, outcomes, alphaSelective = 0.05, delta = 0.1))
        // even without the minimum: (1 - 0.05)^20 = 0.36 is far above any Holm level
        assertNull(ContinuumSelectiveRiskCertification.calibrate(sha, outcomes, 0.05, 0.1, minAnswered = 1))
        val model = ContinuumResidualSpanCore(77)
        assertNull(ContinuumSelectiveRiskCertification.answer(model, "R10 steht in Wien.", "Wo steht R10?", null))
    }

    /** Why I2 is needed: the marginal I1 bound passes although half of the shown answers are wrong. */
    @Test fun marginalCertificatePassesWhileHalfOfTheAnswersAreWrong() {
        val outcomes = List(970) { Outcome(false, 0.6, false) } +
            List(30) { Outcome(true, 0.9, it % 2 == 0) }
        val marginal = ContinuumCertifiedAbstention.calibrate(sha, outcomes, alpha = 0.05, delta = 0.1)
        assertNotNull(marginal)
        val shown = outcomes.filter { it.answered && it.confidence >= marginal!!.threshold }
        assertEquals(0.5, shown.count { !it.correct }.toDouble() / shown.size, 0.0)
        assertNull(ContinuumSelectiveRiskCertification.calibrate(sha, outcomes, alphaSelective = 0.10, delta = 0.1))
        assertNull(ContinuumSelectiveRiskCertification.calibrate(sha, outcomes, 0.10, 0.1, alphaMarginal = 0.05))
    }

    @Test fun jointModeAlsoRequiresTheMarginalBound() {
        // 900 of 1000 answered, 57 wrong (6.3 % of the answers, 5.7 % of all questions), errors spread over confidences
        val outcomes = List(900) { Outcome(true, 0.5 + 0.5 * it / 900.0, it % 16 != 0) } + List(100) { Outcome(false, 0.4, false) }
        val selective = ContinuumSelectiveRiskCertification.calibrate(sha, outcomes, alphaSelective = 0.10, delta = 0.1)!!
        assertEquals(0.0, selective.threshold, 0.0)
        assertEquals(900, selective.calibrationAnswered)
        val joint = ContinuumSelectiveRiskCertification.calibrate(sha, outcomes, 0.10, 0.1, alphaMarginal = 0.05)
        if (joint != null) {
            assertTrue(joint.threshold > selective.threshold)
            assertTrue(ContinuumCertifiedAbstention.binomialCdf(joint.calibrationErrors, 1000, 0.05) <= joint.holmLevel)
        }
        // the selective-only certificate must not be mistaken for the marginal one: 5.7 % > 5 % at its threshold
        assertTrue(ContinuumCertifiedAbstention.binomialCdf(selective.calibrationErrors, 1000, 0.05) > 0.1)
    }

    /** Synthetic model of [ContinuumSelectiveRiskSimulation]: true S(t) = 0.1 (1 - t)^2, oracle threshold 0.2929. */
    @Test fun guaranteeHoldsInSimulation() {
        val r = ContinuumSelectiveRiskSimulation.run(2000, alpha = 0.05) {
            ContinuumSelectiveRiskCertification.calibrate(sha, it, alphaSelective = 0.05, delta = 0.1)?.threshold
        }
        assertTrue("violation rate ${r.violationRate}", r.violationRate <= 0.1 + 0.02)
        assertTrue("certified in ${r.certifiedRate} of the runs", r.certifiedRate >= 0.95)
        // stricter than the oracle (finite sample, 101 tests) but not trivially strict
        assertTrue("mean threshold ${r.meanThreshold}", r.meanThreshold in 0.30..0.60)
    }

    /** Multiple testing made visible: picking the best-looking threshold without a bound breaks the guarantee. */
    @Test fun naiveThresholdSelectionViolatesFarMoreOftenThanDelta() {
        val r = ContinuumSelectiveRiskSimulation.run(2000, alpha = 0.05) { ContinuumSelectiveRiskSimulation.naive(it, 0.05) }
        assertTrue("naive violation rate ${r.violationRate}", r.violationRate >= 0.3)
    }

    @Test fun certificateIsBoundToTheExactCheckpoint() {
        val model = ContinuumResidualSpanCore(77)
        val training = ContinuumResidualSpanCore.Example("Einheit R10 steht in Wien.", "Wo steht R10?", "Wien")
        repeat(50) { model.train(training) }
        val certificate = ContinuumSelectiveRiskCertification.Certificate(
            modelSha256 = ContinuumCertifiedAbstention.checkpointSha256(model), threshold = 0.0, alphaSelective = 0.05,
            alphaMarginal = null, delta = 0.1, calibrationQuestions = 100, calibrationAnswered = 50, calibrationErrors = 0,
            pValue = 0.0001, holmLevel = 0.001, gridSize = 101, minAnswered = 30)
        assertEquals("Wien", ContinuumSelectiveRiskCertification.answer(model, training.evidence, training.question, certificate))
        assertNull(ContinuumSelectiveRiskCertification.answer(ContinuumResidualSpanCore(77), training.evidence, training.question, certificate))
        model.train(training)
        assertNull(ContinuumSelectiveRiskCertification.answer(model, training.evidence, training.question, certificate))
    }
}

package at.heinz.deviceai.learning.span

import at.heinz.deviceai.learning.span.ContinuumCertifiedAbstention.Outcome
import java.util.Random
import kotlin.math.ceil
import kotlin.math.ln

/**
 * Synthetic calibration model for I2 (test sources only, never shipped):
 * confidence c ~ U(0,1), 80 % of the questions get an answer (argmax not null), an answer is wrong with probability
 * 0.3 (1 - c)^2. True selective risk S(t) = 0.1 (1 - t)^2 (S <= 0.05 exactly for t >= 0.2929), true coverage 0.8 (1 - t).
 * Also holds the comparison rules that show why the certificate needs exact bounds AND a multiple-testing correction.
 */
object ContinuumSelectiveRiskSimulation {
    fun trueSelectiveRisk(t: Double) = 0.1 * (1 - t) * (1 - t)
    fun trueCoverage(t: Double) = 0.8 * (1 - t)

    fun sample(rnd: Random, n: Int = 3000): List<Outcome> = List(n) {
        val c = rnd.nextDouble()
        Outcome(rnd.nextDouble() < 0.8, c, rnd.nextDouble() >= 0.3 * (1 - c) * (1 - c))
    }

    data class Result(val violationRate: Double, val certifiedRate: Double, val meanThreshold: Double, val meanCoverage: Double)

    /** Violation: a threshold is returned whose TRUE selective risk exceeds alpha. No threshold = always abstain. */
    fun run(runs: Int, alpha: Double, seed: Long = 20261022L, n: Int = 3000, choose: (List<Outcome>) -> Double?): Result {
        val rnd = Random(seed)
        var violations = 0; var certified = 0; var thresholdSum = 0.0; var coverageSum = 0.0
        repeat(runs) {
            val t = choose(sample(rnd, n)) ?: return@repeat
            certified++; thresholdSum += t; coverageSum += trueCoverage(t)
            if (trueSelectiveRisk(t) > alpha) violations++
        }
        val k = maxOf(1, certified)
        return Result(violations.toDouble() / runs, certified.toDouble() / runs, thresholdSum / k, coverageSum / k)
    }

    // ---- comparison rules (research only) ----

    /** Most permissive threshold whose EMPIRICAL selective error is <= alpha: no bound, no correction. */
    fun naive(outcomes: List<Outcome>, alpha: Double): Double? =
        ContinuumSelectiveRiskCertification.tests(outcomes, alpha).lastOrNull { it.answered >= 30 && it.errors <= alpha * it.answered }?.threshold

    /** Exact binomial bound per threshold at level delta, but no correction for testing 101 thresholds. */
    fun uncorrected(outcomes: List<Outcome>, alpha: Double, delta: Double): Double? =
        ContinuumSelectiveRiskCertification.tests(outcomes, alpha).lastOrNull { it.pSelective <= delta }?.threshold

    /** Bonferroni over the grid: valid, but uniformly less powerful than Holm. */
    fun bonferroni(outcomes: List<Outcome>, alpha: Double, delta: Double): Double? {
        val tests = ContinuumSelectiveRiskCertification.tests(outcomes, alpha)
        return tests.lastOrNull { it.pSelective <= delta / tests.size }?.threshold
    }

    /**
     * SGR-style binary search (Geifman & El-Yaniv 2017) over the fixed grid with level delta / ceil(log2 m) per visited
     * threshold. The visited thresholds depend on earlier test results; the union bound over the visited path assumes
     * more than Holm needs (monotone risk), so it is reported only as a comparison.
     */
    fun sgrBinarySearch(outcomes: List<Outcome>, alpha: Double, delta: Double): Double? {
        val tests = ContinuumSelectiveRiskCertification.tests(outcomes, alpha)
        val steps = ceil(ln(tests.size.toDouble()) / ln(2.0)).toInt()
        val level = delta / steps
        var lo = 0; var hi = tests.size - 1; var best: Int? = null
        repeat(steps) {
            if (lo > hi) return@repeat
            val mid = (lo + hi + 1) / 2
            if (tests[mid].pSelective <= level) { best = mid; lo = mid + 1 } else hi = mid - 1
        }
        return best?.let { tests[it].threshold }
    }
}

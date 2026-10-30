package at.heinz.deviceai.research.exp20.p2

import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/** Preregistered statistics on independent dialog stems (clusters), never on correlated variants. */
object P2Stats {
    private fun logChoose(n: Int, k: Int): Double {
        var s = 0.0; for (i in 1..k) s += ln((n - k + i).toDouble()) - ln(i.toDouble()); return s
    }

    /** Exact two-sided McNemar (binomial on the b + c discordant stems, p = 0.5). */
    fun mcnemarExact(b: Int, c: Int): Double {
        val n = b + c
        if (n == 0) return 1.0
        val k = minOf(b, c)
        var tail = 0.0
        for (i in 0..k) tail += exp(logChoose(n, i) - n * ln(2.0))
        return minOf(1.0, 2.0 * tail)
    }

    /** Holm step-down adjusted p-values in the input order. */
    fun holm(p: DoubleArray): DoubleArray {
        val order = p.indices.sortedBy { p[it] }
        val adj = DoubleArray(p.size); var running = 0.0
        for ((rank, i) in order.withIndex()) { running = maxOf(running, minOf(1.0, (p.size - rank) * p[i])); adj[i] = running }
        return adj
    }

    data class Interval(val estimate: Double, val lo95: Double, val hi95: Double, val lo90: Double, val hi90: Double)

    /** Percentile cluster bootstrap of mean(x) - mean(y) for paired per-cluster scores. */
    fun bootstrapDiff(x: DoubleArray, y: DoubleArray, resamples: Int = P2Config.BOOTSTRAP, seed: Long = P2Config.BOOTSTRAP_SEED): Interval {
        require(x.size == y.size && x.isNotEmpty())
        val n = x.size; val r = Random(seed); val d = DoubleArray(n) { x[it] - y[it] }
        val boots = DoubleArray(resamples) { var s = 0.0; repeat(n) { s += d[r.nextInt(n)] }; s / n }
        boots.sort()
        fun q(p: Double) = boots[((resamples - 1) * p).toInt()]
        return Interval(d.average(), q(0.025), q(0.975), q(0.05), q(0.95))
    }

    enum class Verdict { SUPERIOR, INFERIOR, EQUIVALENT, INCONCLUSIVE }

    data class Comparison(
        val x: ArmId, val y: ArmId, val n: Int, val solvedX: Int, val solvedY: Int, val b: Int, val c: Int,
        val p: Double, val pHolm: Double, val solve: Interval, val turnAcc: Interval, val verdict: Verdict
    )

    /**
     * SUPERIOR: Holm p < alpha, 95 % CI lower bound > 0 and estimate >= MDE. INFERIOR: mirror image.
     * EQUIVALENT: 90 % CI inside [-MDE, +MDE] (TOST at alpha = 0.05). Otherwise INCONCLUSIVE.
     */
    fun verdict(pHolm: Double, ci: Interval): Verdict = when {
        pHolm < P2Config.ALPHA && ci.lo95 > 0.0 && ci.estimate >= P2Config.MDE -> Verdict.SUPERIOR
        pHolm < P2Config.ALPHA && ci.hi95 < 0.0 && ci.estimate <= -P2Config.MDE -> Verdict.INFERIOR
        ci.lo90 >= -P2Config.MDE && ci.hi90 <= P2Config.MDE -> Verdict.EQUIVALENT
        else -> Verdict.INCONCLUSIVE
    }
}

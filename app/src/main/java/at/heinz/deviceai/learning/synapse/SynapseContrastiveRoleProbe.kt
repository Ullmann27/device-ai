package at.heinz.deviceai.learning.synapse

import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/** Shadow-only 4096-parameter role-binding experiment, with analytic two-candidate InfoNCE SGD.
 * The network ranks candidate clauses; it cannot authorize answers without an independent proof. */
class SynapseContrastiveRoleProbe(seed: Int = 20261022) {
    companion object { const val BUCKETS = 128; const val DIM = 16; const val PARAMETER_COUNT = 4096 }
    private val query = DoubleArray(BUCKETS * DIM) { i -> 0.18 * sin(i * 0.71 + seed * 0.002) }
    private val clause = DoubleArray(BUCKETS * DIM) { i -> 0.18 * sin(i * 0.37 + seed * 0.003) }
    private val role = DoubleArray(DIM) { i -> if (i == 1) 1.0 else 0.0 }
    var updates: Int = 0
        private set
    val parameterCount: Int get() = query.size + clause.size

    private fun buckets(text: String): IntArray {
        require(text.length in 1..1024)
        val normalized = text.lowercase(Locale.ROOT)
            .replace(Regex("\\b[a-z]+-?[0-9]+[a-z0-9-]*\\b"), " subj_slot ")
            .replace(Regex("\\s+"), " ").trim()
        val grams = HashSet<Int>()
        val padded = "^$normalized$"
        for (length in 2..4) for (i in 0..padded.length-length) {
            grams += (padded.substring(i, i+length).hashCode() and Int.MAX_VALUE) % BUCKETS
        }
        require(grams.isNotEmpty())
        return grams.sorted().toIntArray()
    }
    private fun embed(table: DoubleArray, ids: IntArray): DoubleArray {
        val out = DoubleArray(DIM)
        for (id in ids) for (d in 0 until DIM) out[d] += table[id * DIM + d] / sqrt(ids.size.toDouble())
        return out
    }
    /** Unbind role from clause hologram: adjoint circular correlation. */
    private fun unbind(c: DoubleArray): DoubleArray = DoubleArray(DIM) { k ->
        (0 until DIM).sumOf { j -> role[j] * c[(j + k) % DIM] }
    }
    private fun bind(q: DoubleArray): DoubleArray = DoubleArray(DIM) { m ->
        (0 until DIM).sumOf { k -> q[k] * role[(m - k + DIM) % DIM] }
    }
    private fun score(q: DoubleArray, c: DoubleArray): Double {
        val u = unbind(c)
        return (0 until DIM).sumOf { q[it] * u[it] }
    }
    @Synchronized fun rankScore(question: String, candidateClause: String): Double {
        val q = embed(query, buckets(question)); val c = embed(clause, buckets(candidateClause))
        return score(q, c)
    }
    /** Pairwise softmax training: loss = ln(1 + exp(scoreNegative - scorePositive)).
     * Two-phase update computes BOTH gradients on pre-update parameters. */
    @Synchronized fun trainPair(question: String, positiveClause: String, hardNegativeClause: String, lr: Double = 0.12): Double {
        require(lr.isFinite() && lr in 0.00001..0.5)
        val qi = buckets(question); val pi = buckets(positiveClause); val ni = buckets(hardNegativeClause)
        val qv = embed(query, qi); val pv = embed(clause, pi); val nv = embed(clause, ni)
        val delta = score(qv, pv) - score(qv, nv)
        val magnitude = if (delta >= 0) exp(-delta) / (1 + exp(-delta)) else 1 / (1 + exp(delta))
        val qGradient = DoubleArray(DIM) { d -> unbind(pv)[d] - unbind(nv)[d] }
        val cGradient = bind(qv)
        // Accumulate all changes using the same prior parameters, then apply once.
        val dq = HashMap<Int, Double>()
        val dc = HashMap<Int, Double>()
        fun accumulate(map: MutableMap<Int, Double>, ids: IntArray, grad: DoubleArray, sign: Double) {
            for (id in ids) for (d in 0 until DIM) {
                val idx = id * DIM + d
                map[idx] = (map[idx] ?: 0.0) + sign * lr * magnitude * grad[d] / sqrt(ids.size.toDouble())
            }
        }
        accumulate(dq, qi, qGradient, 1.0)
        accumulate(dc, pi, cGradient, 1.0)
        accumulate(dc, ni, cGradient, -1.0)
        for ((i, dx) in dq) query[i] = (query[i] + dx).coerceIn(-2.0, 2.0)
        for ((i, dx) in dc) clause[i] = (clause[i] + dx).coerceIn(-2.0, 2.0)
        updates++
        return if (delta > 0) ln(1 + exp(-delta)) else -delta + ln(1 + exp(delta))
    }
    /** SHA-256 over doubles is intentionally omitted: no persistent checkpoint/promotion API exists yet. */
}

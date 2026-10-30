package at.heinz.deviceai.research.exp20.p3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pins the two facts behind the P4 decision (docs/research/exp20/EXP-20-P4-RESULT-20261030.md) so later experiments
 * cannot silently contradict them:
 * 1. one modern-Hopfield step ranks candidates exactly like the plain similarity (softmax is strictly monotone);
 * 2. in a near-miss star (centre plus one-slot variants) the centre usually wins a query-blind sum of similarities (a
 *    measured tendency, not a theorem), which is
 *    why every M5 dataset needs a query-blind control and must not put the true memory at the centre.
 */
class P4MathTest {
    private fun unit(v: DoubleArray): DoubleArray { val n = sqrt(v.sumOf { it * it }); return DoubleArray(v.size) { v[it] / n } }
    private fun dot(a: DoubleArray, b: DoubleArray) = a.indices.sumOf { a[it] * b[it] }

    @Test fun oneHopfieldStepIsRankIdenticalToPlainSimilarity() {
        val r = Random(4)
        repeat(2_000) {
            val d = 32; val q = unit(DoubleArray(d) { r.nextDouble(-1.0, 1.0) })
            val m = List(8) { unit(DoubleArray(d) { r.nextDouble(-1.0, 1.0) }) }
            val s = m.map { dot(q, it) }
            val beta = exp(r.nextDouble(-2.0, 4.0))
            val e = s.map { exp(beta * (it - s.max())) }; val p = e.map { it / e.sum() }
            assertEquals(s.indices.sortedByDescending { s[it] }, p.indices.sortedByDescending { p[it] })
        }
    }

    @Test fun theCentreOfANearMissStarWinsWithoutAnyQuery() {
        val r = Random(5)
        var wins = 0
        repeat(500) {
            val d = 32; val centre = DoubleArray(d) { r.nextDouble(-1.0, 1.0) }
            val variants = List(3) { k -> centre.copyOf().also { v -> for (j in 0 until 4) v[(k * 4 + j) % d] = r.nextDouble(-1.0, 1.0) } }
            val unrelated = List(4) { DoubleArray(d) { r.nextDouble(-1.0, 1.0) } }
            val cands = (listOf(centre) + variants + unrelated).map(::unit)
            val blind = cands.map { c -> cands.sumOf { dot(it, c) } }
            if (blind.drop(1).all { it < blind[0] }) wins++
        }
        println("P4MathTest query-blind centre wins: $wins/500 (chance among 8 candidates: 62.5/500)")
        // A tendency, not a theorem: random geometry can let a variant tie or beat the centre. Gate at 3x chance.
        assertTrue("centre must win far above chance, got $wins/500", wins >= 3 * 500 / 8)
    }
}

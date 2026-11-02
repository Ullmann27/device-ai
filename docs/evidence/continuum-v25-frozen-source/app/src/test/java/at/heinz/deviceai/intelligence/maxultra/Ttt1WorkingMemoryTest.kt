package at.heinz.deviceai.intelligence.maxultra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** Experiment A (TTT1): fast-weight mathematics and the three memories on a small own-seed dialog set. */
class Ttt1WorkingMemoryTest {
    private fun matrix(g: Lct1.FastWeightGuide): Array<DoubleArray> = Array(16) { c ->
        g.predict(DoubleArray(16) { if (it == c) 1.0 else 0.0 })
    }.let { cols -> Array(16) { r -> DoubleArray(16) { c -> cols[c][r] } } }

    @Test fun theFastWeightUpdateIsTheExactGradientStep() {
        val rnd = Random(11)
        val g = Lct1.FastWeightGuide(3L)
        fun unit() = Lct1.FastWeightGuide.normalize(DoubleArray(16) { rnd.nextGaussian() })
        repeat(5) { g.learn(unit(), unit()) }
        val w = matrix(g)
        val x = unit(); val y = unit()
        fun loss(m: Array<DoubleArray>): Double {
            var l = 0.0
            for (r in 0 until 16) { val e = (0 until 16).sumOf { m[r][it] * x[it] } - y[r]; l += 0.5 * e * e }
            return l + 0.001 * m.sumOf { row -> row.sumOf { it * it } }
        }
        val analytic = Array(16) { r -> val e = (0 until 16).sumOf { w[r][it] * x[it] } - y[r]; DoubleArray(16) { c -> e * x[c] + 0.002 * w[r][c] } }
        var worst = 0.0
        for (r in 0 until 16) for (c in 0 until 16) {
            val h = 1e-6
            val plus = Array(16) { w[it].copyOf() }.also { it[r][c] += h }
            val minus = Array(16) { w[it].copyOf() }.also { it[r][c] -= h }
            worst = maxOf(worst, kotlin.math.abs((loss(plus) - loss(minus)) / (2 * h) - analytic[r][c]))
        }
        assertTrue("finite differences, worst $worst", worst < 1e-7)
        g.learn(x, y)
        val after = matrix(g)
        for (r in 0 until 16) for (c in 0 until 16) assertEquals(w[r][c] - 0.08 * analytic[r][c], after[r][c], 1e-12)
    }

    @Test fun normBoundFiniteInputsAndResetOfTheRealCore() {
        val core = EphemeralFastWeightCore()
        val rnd = Random(5)
        repeat(2_000) { core.observe(DoubleArray(16) { (rnd.nextDouble() * 2 - 1) * 100 }) }   // the documented input range
        assertTrue(core.weightNorm() <= 2.5 + 1e-12)
        assertTrue(runCatching { core.observe(DoubleArray(16) { if (it == 3) Double.NaN else 1.0 }) }.isFailure)
        core.reset()
        assertEquals(0.0, core.weightNorm(), 0.0)
        assertEquals(0, core.updates)
    }

    @Test fun theThreeMemoriesBehaveAsSpecified() {
        val g = Ttt1.Generator(7L, emptySet())
        val dialogs = Ttt1.VARIANTS.flatMap { v -> (0 until 4).map { g.dialog(v, "$v-$it") } }
        val store = Ttt1.EntityStore()
        for (d in dialogs) assertTrue(d.id, Ttt1.run(store, d).first.all { it == Ttt1.Outcome.CORRECT })
        val context = Ttt1.BoundedContext()
        dialogs.filter { it.variant == "TYPE_CONSTRAINT" }.forEach {
            assertEquals("the newer same-gender device wins without a type check", listOf(Ttt1.Outcome.WRONG), Ttt1.run(context, it).first)
        }
        dialogs.filter { it.variant == "NEW_SESSION" }.forEach { d ->
            for (m in listOf(context, store, Ttt1.StoreWithFastWeights(1L))) assertEquals(Ttt1.Outcome.CORRECT, Ttt1.run(m, d).first.last())
        }
        val fw = Ttt1.StoreWithFastWeights(1L)
        dialogs.filter { it.variant == "AMBIGUOUS" }.forEach {
            assertNotEquals("fast weights pick instead of asking back", Ttt1.Outcome.CORRECT, Ttt1.run(fw, it).first.last())
        }
    }
}

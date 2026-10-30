package at.heinz.deviceai.research.exp20

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.tanh

/**
 * EXP-20 pilot P1 (research only, test source set; no caller in app/src/main).
 *
 * tau_i dh_i/dt = -h_i + sum_j W_ij tanh(h_j) + x_i + b_i, inputs clipped to [-1, 1], integrated with the
 * exponential Euler step h_i <- a_i h_i + (1 - a_i) drive_i, a_i = exp(-dt / tau_i) (exact for the linear part).
 * The new state is a convex combination of the old state and a drive bounded by R_i = sum_j |W_ij| + |b_i| + 1,
 * so |h_i| <= max(|h_i(0)|, R_i) for every dt > 0; with rho = max_i sum_j |W_ij| < 1 the step is a contraction in
 * the max norm with factor max_i (a_i + (1 - a_i) rho). No learning: weights are fixed at construction.
 */
class BoundedCtrnn(private val tau: DoubleArray, private val w: Array<DoubleArray>, private val b: DoubleArray) {
    val size = tau.size
    private val h = DoubleArray(size)
    private val activation = DoubleArray(size)

    init {
        require(size > 0 && w.size == size && b.size == size && w.all { it.size == size })
        require(tau.all { it.isFinite() && it > 0.0 } && b.all { it.isFinite() } && w.all { r -> r.all { it.isFinite() } })
    }

    fun state(): DoubleArray = h.copyOf()

    fun reset(initial: DoubleArray) {
        require(initial.size == size && initial.all { it.isFinite() })
        initial.copyInto(h)
    }

    /** Fail closed: a non-finite or non-positive dt, or a non-finite input, leaves the state unchanged. */
    fun step(input: DoubleArray, dt: Double): Boolean {
        if (input.size != size || !dt.isFinite() || dt <= 0.0) return false
        for (x in input) if (!x.isFinite()) return false
        for (j in 0 until size) activation[j] = tanh(h[j])
        for (i in 0 until size) {
            var drive = b[i] + input[i].coerceIn(-1.0, 1.0)
            val row = w[i]
            for (j in 0 until size) drive += row[j] * activation[j]
            val a = exp(-dt / tau[i])
            h[i] = a * h[i] + (1.0 - a) * drive
        }
        return true
    }

    fun bound(i: Int): Double = w[i].sumOf { abs(it) } + abs(b[i]) + 1.0
    fun rho(): Double = w.maxOf { r -> r.sumOf { abs(it) } }
    fun contractionFactor(dt: Double): Double = tau.maxOf { t -> val a = exp(-dt / t); a + (1.0 - a) * rho() }
}

/** Cost baseline: independent leaky integrators carrying the same temporal context, no recurrence. */
class LeakyIntegrators(private val tau: DoubleArray) {
    private val h = DoubleArray(tau.size)
    fun step(input: DoubleArray, dt: Double): Boolean {
        if (input.size != h.size || !dt.isFinite() || dt <= 0.0) return false
        for (x in input) if (!x.isFinite()) return false
        for (i in h.indices) { val a = exp(-dt / tau[i]); h[i] = a * h[i] + (1.0 - a) * input[i].coerceIn(-1.0, 1.0) }
        return true
    }
    fun first(): Double = h[0]
}

package at.heinz.deviceai.motion

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Analytic damped spring from 0 to 1 (start at rest, zero velocity): x'' + 2ζω₀x' + ω₀²(x-1) = 0.
 * Under-damped (ζ < 1) overshoots once by exp(-ζπ/√(1-ζ²)) at t = π/ω_d; critical (ζ = 1) and
 * over-damped (ζ > 1) approach 1 monotonically.
 *
 * [settleSec]: from this time on |1 - value| ≤ [tolerance] holds for good (under-damped: analytic
 * envelope bound; otherwise solved on the monotone curve). [valueAt] returns exactly 1 from [settleSec]
 * on (jump ≤ tolerance) and 0 for t ≤ 0. Pure and allocation-free.
 */
class DampedSpring(
    /** ω₀ in rad/s. */
    val naturalFrequency: Float,
    /** ζ > 0. */
    val dampingRatio: Float,
    val tolerance: Float = DEFAULT_TOLERANCE,
) : SpringCurve {
    init {
        require(naturalFrequency.isFinite() && naturalFrequency > 0f) { "naturalFrequency > 0" }
        require(dampingRatio.isFinite() && dampingRatio > 0f) { "dampingRatio > 0" }
        require(tolerance > 0f && tolerance < 1f) { "tolerance in (0,1)" }
    }

    private val w0 = naturalFrequency.toDouble()
    private val z = dampingRatio.toDouble()

    override val settleSec: Float = (dimensionlessSettle(z, tolerance.toDouble()) / w0).toFloat()

    /** Peak value (1 + overshoot); 1 for ζ ≥ 1. */
    val peakValue: Float get() = if (z < 1.0) (1.0 + exp(-z * Math.PI / sqrt(1.0 - z * z))).toFloat() else 1f
    /** Time of the peak (under-damped only, else [settleSec]). */
    val peakSec: Float get() = if (z < 1.0) (Math.PI / (w0 * sqrt(1.0 - z * z))).toFloat() else settleSec

    override fun valueAt(tSec: Float): Float {
        if (!(tSec > 0f)) return 0f
        if (tSec >= settleSec) return 1f
        return (1.0 - displacement(z, w0 * tSec)).toFloat()
    }

    fun valueAtMs(tMs: Long): Float = valueAt(tMs / 1000f)

    /** This spring as an [Easing] over its settle time: at(p) = valueAt(p · settleSec). */
    fun asEasing(): Easing = Easing { valueAt(it * settleSec) }

    /** Duration to use for a [MotionTrack] driven by [asEasing]. */
    val settleMs: Long get() = kotlin.math.ceil(settleSec * 1000.0).toLong()

    companion object {
        const val DEFAULT_TOLERANCE = .005f

        /** Classic parameters (unit mass by default): ω₀ = √(k/m), ζ = c / (2√(km)). */
        fun fromStiffness(stiffness: Float, damping: Float, mass: Float = 1f, tolerance: Float = DEFAULT_TOLERANCE): DampedSpring {
            require(stiffness > 0f && damping > 0f && mass > 0f)
            return DampedSpring(sqrt(stiffness / mass), damping / (2f * sqrt(stiffness * mass)), tolerance)
        }

        /** Spring that settles (|1-x| ≤ tolerance for good) exactly at [settleSec] with damping ratio [dampingRatio]. */
        fun forSettle(settleSec: Float, dampingRatio: Float, tolerance: Float = DEFAULT_TOLERANCE): DampedSpring {
            require(settleSec > 0f && dampingRatio > 0f)
            val tau = dimensionlessSettle(dampingRatio.toDouble(), tolerance.toDouble())
            return DampedSpring((tau / settleSec).toFloat(), dampingRatio, tolerance)
        }

        /** 1 - x at dimensionless time τ = ω₀t. */
        private fun displacement(z: Double, tau: Double): Double = when {
            z < 1.0 -> {
                val wd = sqrt(1.0 - z * z)
                exp(-z * tau) * (cos(wd * tau) + z / wd * sin(wd * tau))
            }
            z == 1.0 -> exp(-tau) * (1.0 + tau)
            else -> {
                val s = sqrt(z * z - 1.0)
                val r1 = -(z - s); val r2 = -(z + s)
                (r2 * exp(r1 * tau) - r1 * exp(r2 * tau)) / (r2 - r1)
            }
        }

        /** Dimensionless settle time τ_s (settleSec = τ_s / ω₀). */
        private fun dimensionlessSettle(z: Double, tol: Double): Double {
            if (z < 1.0) return ln(1.0 / (tol * sqrt(1.0 - z * z))) / z // envelope e^{-ζτ}/√(1-ζ²) ≤ tol
            // Monotone decreasing displacement: bisection for displacement(τ) = tol.
            var hi = 1.0
            while (displacement(z, hi) > tol) hi *= 2.0
            var lo = 0.0
            repeat(80) { val mid = (lo + hi) / 2.0; if (displacement(z, mid) > tol) lo = mid else hi = mid }
            return hi
        }
    }
}

package at.heinz.deviceai.motion

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow

/**
 * Easing curves for [Easing] (t 0..1 → 0..1, overshoot allowed). Inputs outside 0..1 are clamped, so a
 * curve is safe to evaluate before start / after end. Every curve hits f(0)=0 and f(1)=1 exactly.
 * JarvisMotion.Easing (ui) delegates to these, so there is one definition per curve.
 */
object Easings {
    private fun c(t: Float) = if (t.isNaN()) 0f else t.coerceIn(0f, 1f)

    val linear = Easing { c(it) }
    val easeInCubic = Easing { val x = c(it); x * x * x }
    val easeOutCubic = Easing { val x = 1f - c(it); 1f - x * x * x }
    val easeInOutCubic = Easing { val x = c(it); if (x < .5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f }
    /** Strong deceleration (Muster G pills). */
    val easeOutQuint = Easing { val x = 1f - c(it); 1f - x * x * x * x * x }
    /** Exponential ease-in (Muster A scene change, Video 3 warp): 2^(10t-10), exact 0 at t=0. */
    val easeInExpo = Easing { val x = c(it); if (x <= 0f) 0f else if (x >= 1f) 1f else 2f.pow(10f * x - 10f) }
    /** Sine in-out (Muster C idle loop shape). */
    val easeInOutSine = Easing { -(cos(PI.toFloat() * c(it)) - 1f) / 2f }
    /** Back-out with ~10 % overshoot (c = 1.2), kept from JarvisMotion. */
    val easeOutBack = Easing { val x = c(it) - 1f; val k = 1.2f; 1f + (k + 1f) * x * x * x + k * x * x }

    /**
     * CSS-style cubic Bézier through (0,0), (x1,y1), (x2,y2), (1,1). x1, x2 must be in 0..1 (x monotone);
     * y may leave 0..1 for overshoot. Solved per call by Newton with bisection fallback, no allocation.
     */
    fun cubicBezier(x1: Float, y1: Float, x2: Float, y2: Float): Easing {
        require(x1 in 0f..1f && x2 in 0f..1f) { "Bezier x control points must be in 0..1" }
        require(y1.isFinite() && y2.isFinite())
        return CubicBezier(x1, y1, x2, y2)
    }

    private class CubicBezier(x1: Float, y1: Float, x2: Float, y2: Float) : Easing {
        private val cx = 3.0 * x1; private val bx = 3.0 * (x2 - x1) - cx; private val ax = 1.0 - cx - bx
        private val cy = 3.0 * y1; private val by = 3.0 * (y2 - y1) - cy; private val ay = 1.0 - cy - by
        private fun x(s: Double) = ((ax * s + bx) * s + cx) * s
        private fun y(s: Double) = ((ay * s + by) * s + cy) * s
        private fun dx(s: Double) = (3.0 * ax * s + 2.0 * bx) * s + cx

        override fun at(t: Float): Float {
            val x = c(t).toDouble()
            if (x <= 0.0) return 0f
            if (x >= 1.0) return 1f
            var s = x
            for (i in 0 until 8) {
                val err = x(s) - x
                if (abs(err) < 1e-7) return y(s).toFloat()
                val d = dx(s)
                if (abs(d) < 1e-6) break
                s -= err / d
            }
            var lo = 0.0; var hi = 1.0; s = x
            for (i in 0 until 40) {
                val v = x(s)
                if (abs(v - x) < 1e-7) break
                if (v < x) lo = s else hi = s
                s = (lo + hi) / 2.0
            }
            return y(s).toFloat()
        }
    }
}

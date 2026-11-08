package at.heinz.deviceai.avatar3d

import kotlin.math.exp

/**
 * Allocation-free critically damped scalar spring with an exact constant-target step.
 * A repeated timestamp is strictly idempotent; discontinuities snap explicitly.
 */
internal class CriticalDampedSpring(initial: Float = 0f) {
    var value: Float = initial
        private set
    var velocity: Float = 0f
        private set

    fun snap(target: Float): Float {
        require(target.isFinite())
        value = target
        velocity = 0f
        return value
    }

    fun step(target: Float, dtSeconds: Float, omega: Float): Float {
        require(target.isFinite())
        require(omega.isFinite() && omega > 0f)
        if (!dtSeconds.isFinite() || dtSeconds < 0f || dtSeconds > 1f) return snap(target)
        if (dtSeconds == 0f) return value

        val y = value - target
        val c = velocity + omega * y
        val decay = exp((-omega * dtSeconds).toDouble()).toFloat()
        value = target + (y + c * dtSeconds) * decay
        velocity = (velocity - omega * c * dtSeconds) * decay
        if (kotlin.math.abs(value - target) < SNAP_EPSILON &&
            kotlin.math.abs(velocity) < SNAP_VELOCITY_EPSILON) {
            value = target
            velocity = 0f
        }
        return value
    }

    private companion object {
        const val SNAP_EPSILON = 0.0001f
        const val SNAP_VELOCITY_EPSILON = 0.001f
    }
}

package at.heinz.deviceai.ui

import at.heinz.deviceai.avatar3d.CockpitStageFrame
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** One reversible monitor flight. */
object CockpitMonitorMotion {
    const val DURATION_MS = 420L

    data class Sample(val focus: Float, val velocityPerMs: Float)

    class Flight internal constructor(
        private val start: Float,
        private val velocity: Float,
        private val target: Float,
        private val brakingMs: Float,
        private val waypoint: Float,
        private val travelMs: Float
    ) {
        val durationMs = brakingMs + travelMs

        fun sample(elapsedMs: Float): Sample {
            val time = if (elapsedMs.isFinite()) elapsedMs.coerceAtLeast(0f) else 0f
            if (time >= durationMs) return Sample(target, 0f)
            if (time == 0f) return Sample(start, velocity)
            if (time < brakingMs) {
                val fraction = time / brakingMs
                return Sample(start + velocity * time * (1f - fraction * .5f),
                    velocity * (1f - fraction))
            }
            val p = ((time - brakingMs) / travelMs).toDouble()
            val tangent = if (brakingMs > 0f) 0.0 else (velocity * travelMs).toDouble()
            val distance = (target - waypoint).toDouble()
            val value = waypoint + distance * (3 * p * p - 2 * p * p * p) +
                tangent * (p * p * p - 2 * p * p + p)
            val speed = (distance * (6 * p - 6 * p * p) +
                tangent * (3 * p * p - 4 * p + 1)) / travelMs
            return Sample(value.toFloat().coerceIn(0f, 1f), speed.toFloat())
        }
    }

    /** Preserve velocity through direction changes. */
    fun flight(from: Float, velocityPerMs: Float, target: Float): Flight {
        require(from.isFinite() && from in 0f..1f && target in 0f..1f)
        val velocity = velocityPerMs.takeIf { it.isFinite() &&
            !(from == 0f && it < 0f) && !(from == 1f && it > 0f) } ?: 0f
        val reverse = velocity != 0f && velocity * (target - from) <= 0f
        val room = if (velocity > 0f) 1f - from else from
        val brakingMs = if (reverse) minOf(70f, 1.6f * room / abs(velocity)) else 0f
        val waypoint = from + velocity * brakingMs * .5f
        val distance = abs(target - waypoint)
        val naturalMs = minOf(DURATION_MS * distance, 480f - brakingMs)
        val travelMs = if (brakingMs == 0f && velocity != 0f)
            minOf(naturalMs, 3f * distance / abs(velocity)) else naturalMs
        return Flight(from, velocity, target, brakingMs, waypoint, travelMs)
    }

    fun pose(base: CockpitStageFrame, focus: Float, density: Float = 1f): CockpitStageFrame {
        require(base.valid() && density.isFinite() && density > 0f)
        val progress = if (focus.isFinite()) focus.coerceIn(0f, 1f) else 0f
        if (progress == 0f) return base
        val arc = if (progress == 1f) 0f else sin(PI * progress).toFloat()
        return base.copy(
            yawDegrees = (base.yawDegrees * (1f - progress) - 3.5f * arc).coerceIn(-8f, 8f),
            pitchDegrees = (base.pitchDegrees * (1f - progress) - 7.5f * arc).coerceIn(-12f, 12f),
            depthOffset = base.depthOffset + minOf(base.monitorWidth, base.monitorHeight) * .22f * progress
        )
    }
}

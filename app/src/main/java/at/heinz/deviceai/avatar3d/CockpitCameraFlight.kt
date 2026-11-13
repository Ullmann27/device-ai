package at.heinz.deviceai.avatar3d

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Code56 phase C: when a computer task is accepted the camera starts behind the monitor (rear housing, stand and
 * cable side in view), sweeps over its side and settles exactly in the rest camera, so every native overlay that is
 * aligned to the rest projection is aligned again when the flight ends.
 *
 * Orientation is a unit quaternion slerped from the rear key to identity (no Euler blending, no gimbal flip); the
 * orbit pivot glides from the monitor centre to the rest target and the orbit radius from a close rear distance to
 * the rest distance. Progress uses smootherstep (zero velocity and acceleration at both ends). Pure math, host-tested;
 * reduced motion never starts a flight.
 */
internal object CockpitCameraFlight {
    const val DURATION_NANOS = 2_600_000_000L
    /** Rear key: 150° around the vertical axis puts the camera behind and beside the monitor. */
    const val START_YAW_DEGREES = 150.0
    /** Slightly from above so the top vents and the stand foot read in the rear view. */
    const val START_PITCH_DEGREES = -11.0
    /** Rear orbit radius as a multiple of the monitor's world width; small enough to stay inside the room. */
    const val START_RADIUS_MONITOR_WIDTHS = 1.35

    data class Pose(
        val eyeX: Double, val eyeY: Double, val eyeZ: Double,
        val targetX: Double, val targetY: Double, val targetZ: Double,
        val upX: Double, val upY: Double, val upZ: Double
    )

    /** Quaternion (x, y, z, w). */
    internal data class Quat(val x: Double, val y: Double, val z: Double, val w: Double) {
        fun dot(o: Quat) = x * o.x + y * o.y + z * o.z + w * o.w
        fun normalized(): Quat { val n = sqrt(dot(this)); return Quat(x / n, y / n, z / n, w / n) }
        operator fun times(o: Quat) = Quat(
            w * o.x + x * o.w + y * o.z - z * o.y,
            w * o.y - x * o.z + y * o.w + z * o.x,
            w * o.z + x * o.y - y * o.x + z * o.w,
            w * o.w - x * o.x - y * o.y - z * o.z)
        fun rotate(vx: Double, vy: Double, vz: Double): DoubleArray {
            // v' = v + 2w(q×v) + 2 q×(q×v)
            val cx = y * vz - z * vy; val cy = z * vx - x * vz; val cz = x * vy - y * vx
            val dx = y * cz - z * cy; val dy = z * cx - x * cz; val dz = x * cy - y * cx
            return doubleArrayOf(vx + 2 * (w * cx + dx), vy + 2 * (w * cy + dy), vz + 2 * (w * cz + dz))
        }
        companion object {
            val IDENTITY = Quat(0.0, 0.0, 0.0, 1.0)
            fun axis(ax: Double, ay: Double, az: Double, degrees: Double): Quat {
                val half = Math.toRadians(degrees) * .5
                val s = sin(half)
                return Quat(ax * s, ay * s, az * s, cos(half))
            }
        }
    }

    internal val startOrientation: Quat =
        (Quat.axis(0.0, 1.0, 0.0, START_YAW_DEGREES) * Quat.axis(1.0, 0.0, 0.0, START_PITCH_DEGREES)).normalized()

    fun smootherstep(t: Double): Double {
        val x = t.coerceIn(0.0, 1.0)
        return x * x * x * (x * (x * 6 - 15) + 10)
    }

    internal fun slerp(a: Quat, b: Quat, t: Double): Quat {
        var to = b
        var cosine = a.dot(b)
        if (cosine < 0) { to = Quat(-b.x, -b.y, -b.z, -b.w); cosine = -cosine }
        if (cosine > .9995) {
            return Quat(a.x + (to.x - a.x) * t, a.y + (to.y - a.y) * t, a.z + (to.z - a.z) * t,
                a.w + (to.w - a.w) * t).normalized()
        }
        val theta = acos(cosine.coerceIn(-1.0, 1.0))
        val s = sin(theta)
        val wa = sin((1 - t) * theta) / s
        val wb = sin(t * theta) / s
        return Quat(a.x * wa + to.x * wb, a.y * wa + to.y * wb, a.z * wa + to.z * wb, a.w * wa + to.w * wb)
    }

    /**
     * [progress] 0 = rear key, 1 = rest camera (eye (0,0,[restDistance]) looking at the origin with +Y up).
     * [pivotX]/[pivotY]/[pivotZ] is the monitor centre in world units, [monitorWidth] its world width.
     */
    fun pose(progress: Double, pivotX: Double, pivotY: Double, pivotZ: Double,
             monitorWidth: Double, restDistance: Double): Pose {
        require(restDistance > 0 && monitorWidth > 0)
        val e = smootherstep(progress)
        val q = slerp(startOrientation, Quat.IDENTITY, e)
        val startRadius = minOf(restDistance, monitorWidth * START_RADIUS_MONITOR_WIDTHS)
        val radius = startRadius + (restDistance - startRadius) * e
        val tx = pivotX * (1 - e); val ty = pivotY * (1 - e); val tz = pivotZ * (1 - e)
        val offset = q.rotate(0.0, 0.0, radius)
        val up = q.rotate(0.0, 1.0, 0.0)
        return Pose(tx + offset[0], ty + offset[1], tz + offset[2], tx, ty, tz, up[0], up[1], up[2])
    }

    /** One flight at a time; restarting replaces it, stop/cleanup ends it at once (camera returns to rest). */
    class Clock {
        private var startedAt = Long.MIN_VALUE
        fun start(nowNanos: Long) { startedAt = nowNanos }
        fun cancel() { startedAt = Long.MIN_VALUE }
        /** Progress in [0,1), or null when no flight runs (rest camera). */
        fun progress(nowNanos: Long): Double? {
            if (startedAt == Long.MIN_VALUE) return null
            val elapsed = nowNanos - startedAt
            if (elapsed < 0 || elapsed >= DURATION_NANOS) { if (elapsed >= DURATION_NANOS) cancel(); return null }
            return elapsed.toDouble() / DURATION_NANOS
        }
        val active: Boolean get() = startedAt != Long.MIN_VALUE
    }

    internal fun nearlyRest(pose: Pose, restDistance: Double, epsilon: Double = 1e-9) =
        abs(pose.eyeX) < epsilon && abs(pose.eyeY) < epsilon && abs(pose.eyeZ - restDistance) < epsilon &&
            abs(pose.targetX) < epsilon && abs(pose.targetY) < epsilon && abs(pose.targetZ) < epsilon &&
            abs(pose.upX) < epsilon && abs(pose.upY - 1) < epsilon && abs(pose.upZ) < epsilon
}

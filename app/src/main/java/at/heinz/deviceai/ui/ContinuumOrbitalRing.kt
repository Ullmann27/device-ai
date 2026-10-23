package at.heinz.deviceai.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A rigid circular orbit, projected in perspective and split exactly at the sphere's depth plane.
 * All buffers are reused. The two batches are composited behind/before the glass, respectively. */
internal class ContinuumOrbitalRing(private val radius: Float, minor: Float, tiltDegrees: Float) {
    private val steps = 72
    private val x = FloatArray(steps + 1)
    private val y = FloatArray(steps + 1)
    private val z = FloatArray(steps + 1)
    private val minorRatio = minor / radius
    private val depthRatio = sqrt(1f - minorRatio * minorRatio)
    private val tilt = tiltDegrees * PI.toFloat() / 180f
    val back = FloatArray((steps + 2) * 4)
    val front = FloatArray((steps + 2) * 4)
    var backSize = 0; private set
    var frontSize = 0; private set

    fun project(phase: Float, speed: Float, cx: Float, cy: Float, scale: Float) {
        backSize = 0; frontSize = 0
        // Independent axes/frequencies; unlike a 2D wobble this changes the projected ring's depth and shape.
        val yaw = (phase - .4f) * .18f * speed
        val pitch = .18f * sin((phase - .4f) * .37f * speed)
        val c = cos(yaw); val s = sin(yaw); val cp = cos(pitch); val sp = sin(pitch)
        val ct = cos(tilt); val st = sin(tilt)
        for (i in 0..steps) {
            val t = (i % steps) * (2f * PI.toFloat() / steps)
            val bx = cos(t) * radius; val by = sin(t) * radius * minorRatio
            val bz = sin(t) * radius * depthRatio
            val rx = bx * c + bz * s; val rz = bz * c - bx * s
            val ry = by * cp - rz * sp
            x[i] = rx * ct - ry * st; y[i] = rx * st + ry * ct; z[i] = by * sp + rz * cp
        }
        for (i in 0 until steps) {
            val j = i + 1
            if ((z[i] >= 0f) == (z[j] >= 0f)) {
                segment(x[i], y[i], z[i], x[j], y[j], z[j], z[i] >= 0f, cx, cy, scale)
            } else {
                val t = z[i] / (z[i] - z[j])
                val mx = x[i] + (x[j] - x[i]) * t; val my = y[i] + (y[j] - y[i]) * t
                segment(x[i], y[i], z[i], mx, my, 0f, z[i] >= 0f, cx, cy, scale)
                segment(mx, my, 0f, x[j], y[j], z[j], z[j] >= 0f, cx, cy, scale)
            }
        }
    }

    private fun segment(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float,
                        near: Boolean, cx: Float, cy: Float, scale: Float) {
        val out = if (near) front else back
        val offset = if (near) frontSize else backSize
        val p0 = 8f / (8f - z0); val p1 = 8f / (8f - z1)
        out[offset] = cx + x0 * scale * p0; out[offset + 1] = cy + y0 * scale * p0
        out[offset + 2] = cx + x1 * scale * p1; out[offset + 3] = cy + y1 * scale * p1
        if (near) frontSize += 4 else backSize += 4
    }

    companion object {
        /** ±2.4% of radius, separate from core rotation and orbital precession; no layout mutation. */
        fun lift(phase: Float, radius: Float): Float = -.024f * radius * sin((phase - .4f) * .85f)
    }
}

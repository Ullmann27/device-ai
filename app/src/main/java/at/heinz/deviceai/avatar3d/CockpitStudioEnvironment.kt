package at.heinz.deviceai.avatar3d

import kotlin.math.sqrt

/**
 * Procedural studio environment for the cockpit's glass and metal. The stage used a constant ambient term only, so
 * monitor glass, bezels and the desk had nothing to reflect. This is a small, deterministic radiance field (dark room,
 * cyan and violet soft boxes, a warm key, a cool rim behind Jarvis) that is baked ONCE into a prefiltered cubemap:
 * no asset download, no per-frame work, no extra render pass. Values are relative to the stage's ambient calibration.
 */
internal object CockpitStudioEnvironment {
    const val FACE_SIZE = 64
    const val PEAK = 1.25f

    private class Box(x: Float, y: Float, z: Float, val inner: Float, val outer: Float,
                      val r: Float, val g: Float, val b: Float) {
        private val n = 1f / sqrt(x * x + y * y + z * z)
        val x = x * n; val y = y * n; val z = z * n
    }

    // +z looks at the viewer, +y is up, -z lies behind Jarvis (same axes as CockpitWorldTransforms).
    private val boxes = arrayOf(
        Box(-.80f, .25f, .55f, .93f, .78f, .14f * PEAK, .82f * PEAK, 1f * PEAK),      // cyan soft box, front left
        Box(.85f, .20f, .30f, .94f, .80f, .58f * PEAK, .24f * PEAK, 1f * PEAK),       // violet soft box, right
        Box(.20f, .75f, .65f, .97f, .90f, 1f * PEAK, .82f * PEAK, .62f * PEAK),       // warm key, top front
        Box(0f, .60f, -.80f, .95f, .82f, .26f, .52f, .90f)                             // cool rim behind Jarvis
    )

    /** Radiance for a direction (need not be normalised). Writes linear RGB into [out] at [offset]. */
    fun radiance(dx: Float, dy: Float, dz: Float, out: FloatArray, offset: Int = 0) {
        val n = 1f / sqrt(dx * dx + dy * dy + dz * dz)
        val x = dx * n; val y = dy * n; val z = dz * n
        // Dark room: a faint blue gradient above the horizon, a dimmer floor bounce below it.
        val up = if (y > 0f) y else 0f
        val down = if (y < 0f) -y else 0f
        var r = .012f + .010f * up + .006f * down
        var g = .020f + .018f * up + .010f * down
        var b = .036f + .034f * up + .018f * down
        for (box in boxes) {
            val d = x * box.x + y * box.y + z * box.z
            if (d <= box.outer) continue
            val t = ((d - box.outer) / (box.inner - box.outer)).coerceIn(0f, 1f)
            val w = t * t * (3f - 2f * t)
            r += box.r * w; g += box.g * w; b += box.b * w
        }
        out[offset] = r; out[offset + 1] = g; out[offset + 2] = b
    }

    /** Six faces in Filament/OpenGL order (+X, -X, +Y, -Y, +Z, -Z), RGB float, rows top to bottom. */
    fun faces(size: Int = FACE_SIZE): FloatArray {
        require(size in 8..128 && size and (size - 1) == 0)
        val out = FloatArray(6 * size * size * 3)
        var at = 0
        for (face in 0 until 6) for (row in 0 until size) for (column in 0 until size) {
            val u = (column + .5f) / size * 2f - 1f
            val v = (row + .5f) / size * 2f - 1f
            when (face) {
                0 -> radiance(1f, -v, -u, out, at)
                1 -> radiance(-1f, -v, u, out, at)
                2 -> radiance(u, 1f, v, out, at)
                3 -> radiance(u, -1f, -v, out, at)
                4 -> radiance(u, -v, 1f, out, at)
                else -> radiance(-u, -v, -1f, out, at)
            }
            at += 3
        }
        return out
    }

    fun levels(size: Int = FACE_SIZE): Int = 32 - Integer.numberOfLeadingZeros(size)
}

/**
 * Light the central monitor casts onto desk and keyboard while real frames are shown. Pure state: the stage only
 * uploads when the quantised value changes, so an idle cockpit costs nothing per frame.
 */
internal class CockpitMonitorGlow(private val riseSeconds: Float = .35f, private val fallSeconds: Float = .60f) {
    private var level = 0f
    private var lastNanos = Long.MIN_VALUE
    private var uploaded = -1

    /** Returns lumens to upload, or null when the visible value did not change. */
    fun step(pixelsVisible: Boolean, nowNanos: Long): Float? {
        val dt = if (lastNanos == Long.MIN_VALUE) 0f else ((nowNanos - lastNanos).coerceIn(0L, 100_000_000L) / 1e9f)
        lastNanos = nowNanos
        val target = if (pixelsVisible) 1f else 0f
        val rate = if (target > level) riseSeconds else fallSeconds
        level = if (dt <= 0f && uploaded < 0) target * 0f
        else (level + (target - level) * (dt / rate).coerceIn(0f, 1f)).coerceIn(0f, 1f)
        val step = (level * STEPS + .5f).toInt()
        if (step == uploaded) return null
        uploaded = step
        return MAX_LUMENS * step / STEPS
    }

    fun reset() { level = 0f; lastNanos = Long.MIN_VALUE; uploaded = -1 }

    companion object {
        const val MAX_LUMENS = 12_000f
        const val STEPS = 48
    }
}

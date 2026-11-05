package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisDesign.Colors
import kotlin.math.PI
import kotlin.math.sin

/**
 * 59509 energy streams through the hero: seven cubic splines in three depth layers flow around the head and
 * into the orb. Time [t] (seconds) comes from the existing avatar frame clock, so there is no second animation
 * loop; with reduced motion the caller passes a constant time and the streams stand still.
 *
 * Motion is continuous and slow: control points drift on sines (periods 7–13 s), hues travel smoothly along
 * cyan → blue → violet → magenta, and light pulses glide along each spline. [energy] (0..1, from the real
 * speech/work state) brightens the streams; it never invents activity.
 */
object JarvisEnergyFlows {
    private class Flow(val p: FloatArray, val depth: Int, val period: Float, val phase: Float, val pulseSpeed: Float)

    /** Normalised stage coordinates (x0,y0, c1x,c1y, c2x,c2y, x1,y1); depth 0 = far, 2 = near. */
    private val flows = listOf(
        Flow(floatArrayOf(-.02f, .96f, .22f, .62f, .44f, 1.02f, .70f, .80f), 2, 9f, 0f, .060f),
        Flow(floatArrayOf(.02f, 1.02f, .30f, .74f, .56f, 1.06f, 1.02f, .88f), 1, 11f, 1.3f, .045f),
        Flow(floatArrayOf(.16f, .18f, .22f, .52f, .38f, .82f, .58f, .97f), 1, 13f, 2.1f, .050f),
        Flow(floatArrayOf(.60f, .80f, .70f, .36f, .94f, .26f, 1.02f, .56f), 0, 10f, .7f, .040f),
        Flow(floatArrayOf(.36f, 1.02f, .54f, .68f, .80f, .64f, 1.02f, .74f), 2, 8f, 2.9f, .070f),
        Flow(floatArrayOf(-.02f, .66f, .18f, .86f, .34f, .58f, .52f, .70f), 0, 12f, 3.7f, .035f),
        Flow(floatArrayOf(.28f, .06f, .48f, -.02f, .70f, .16f, .98f, .04f), 0, 7f, 4.4f, .055f),
    )
    private val palette = intArrayOf(Colors.cyan, Argb.rgb(0x3A, 0x7B, 0xFF), Colors.violet, Colors.magenta)
    private val path = VectorPath()

    /** Smooth periodic palette sample; [u] wraps every 1.0. */
    fun hue(u: Float): Int {
        val x = ((u % 1f) + 1f) % 1f * palette.size
        val i = x.toInt() % palette.size
        val f = x - x.toInt()
        val s = f * f * (3f - 2f * f)
        return Argb.mix(palette[i], palette[(i + 1) % palette.size], s)
    }

    /** Cubic Bézier point of [f] at [u] after drift, in stage pixels. */
    private fun point(f: Flow, w: Float, h: Float, t: Float, u: Float): FloatArray {
        val d = .022f + f.depth * .008f
        val wave = 2f * PI.toFloat() * t / f.period + f.phase
        fun c(i: Int) = f.p[i] + d * sin(wave + i * 1.7f)
        val x0 = c(0); val y0 = c(1); val x1 = c(2); val y1 = c(3); val x2 = c(4); val y2 = c(5); val x3 = c(6); val y3 = c(7)
        val v = 1f - u
        val x = v * v * v * x0 + 3 * v * v * u * x1 + 3 * v * u * u * x2 + u * u * u * x3
        val y = v * v * v * y0 + 3 * v * v * u * y1 + 3 * v * u * u * y2 + u * u * u * y3
        return floatArrayOf(x * w, y * h)
    }

    fun draw(p: ContinuumPainter, w: Float, h: Float, t: Float, energy: Float = 0f, enabled: Boolean = true) {
        if (!enabled || w <= 8f || h <= 8f || !t.isFinite()) return
        val boost = 1f + .6f * energy.coerceIn(0f, 1f)
        p.setAdditive(true)
        for ((index, f) in flows.withIndex()) {
            path.reset()
            for (k in 0..24) {
                val q = point(f, w, h, t, k / 24f)
                if (k == 0) path.moveTo(q[0], q[1]) else path.lineTo(q[0], q[1])
            }
            val shift = t * .035f + index * .17f
            val a = point(f, w, h, t, 0f); val b = point(f, w, h, t, 1f)
            val fill = Fill.Linear(a[0], a[1], b[0], b[1], intArrayOf(Argb.withAlpha(hue(shift), 0f), hue(shift + .25f),
                hue(shift + .5f), Argb.withAlpha(hue(shift + .75f), 0f)), floatArrayOf(0f, .3f, .7f, 1f))
            val near = .55f + f.depth * .225f
            p.strokePath(path, (10f + f.depth * 6f) * near, fill, .045f * boost)
            p.strokePath(path, (3.2f + f.depth * 1.6f) * near, fill, .16f * boost)
            p.strokePath(path, .9f + f.depth * .35f, fill, (.55f + f.depth * .15f) * boost)
            // Two pulses glide along each stream.
            for (j in 0 until 2) {
                val u = (((t * f.pulseSpeed + j * .5f + f.phase * .1f) % 1f) + 1f) % 1f
                val q = point(f, w, h, t, u)
                val glow = sin(PI.toFloat() * u)                 // fades in and out at the ends, no hard jump
                val color = hue(shift + u * .5f)
                JarvisNeon.halo(p, q[0], q[1], 9f + f.depth * 3f, color, .32f * glow * boost)
                p.fillCircle(q[0], q[1], 1.3f + f.depth * .4f, Fill.Solid(Colors.glowCore), .9f * glow)
            }
        }
        p.setAdditive(false)
    }
}

package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisShellLayout.Box
import kotlin.math.cos
import kotlin.math.sin

/**
 * 59509 head stage: violet/cyan aura, two tilted orbital rings and segmented HUD arcs around the live head.
 *
 * Drawn by the shell underneath the transparent native avatar surface, so the rings sit behind the 3D head and
 * never cover the face. Purely decorative and static: it reports no state, starts no animation loop and is
 * omitted when [enabled] is false (reduced motion keeps it; it does not move).
 */
object JarvisHeadAura {
    /** Approximate face centre inside the native bust framing (head, neck, upper shoulders). */
    fun faceCenter(head: Box): Pair<Float, Float> = head.centerX to head.top + head.height * .40f

    fun draw(p: ContinuumPainter, head: Box, enabled: Boolean = true) {
        if (!enabled || head.width <= 8f || head.height <= 8f) return
        val (cx, cy) = faceCenter(head)
        val h = head.height
        // Soft back light: violet left/top, cyan right – the reference's two-tone hologram.
        JarvisNeon.halo(p, cx - h * .08f, cy - h * .06f, h * .62f, Colors.violet, .26f)
        JarvisNeon.halo(p, cx + h * .12f, cy + h * .04f, h * .46f, Colors.cyan, .14f)
        p.setAdditive(true)
        ring(p, cx, cy - h * .10f, h * .60f, h * .13f, -14f, Colors.magenta, Colors.violet, 1.5f)
        ring(p, cx, cy - h * .02f, h * .52f, h * .11f, 9f, Colors.cyan, Colors.violetRim, 1.1f)
        hudArc(p, cx, cy, h * .66f, 148f, 66f, Colors.magenta, segments = 9)
        hudArc(p, cx, cy, h * .66f, -36f, 62f, Colors.cyan, segments = 8)
        hudArc(p, cx, cy, h * .74f, 160f, 40f, Colors.violetRim, segments = 0)
        hudArc(p, cx, cy, h * .74f, -20f, 34f, Colors.cyanSoft, segments = 0)
        sparks(p, cx, cy, h)
        p.setAdditive(false)
    }

    /** Share of the head stage height over which the bust dissolves at the bottom (alpha mask, not an overlay). */
    const val BOTTOM_FADE = .26f
    /** Share of the head stage width over which the cut shoulders dissolve at each side. */
    const val SIDE_FADE = .09f

    /**
     * Alpha (0..1) the native head layer keeps at head-local ([x], [y]) inside a [w]×[h] stage. The Android view
     * applies the same mask with DST_IN gradients; previews multiply their head image with it.
     */
    fun fadeAlpha(x: Float, y: Float, w: Float, h: Float): Float {
        if (w <= 0f || h <= 0f) return 1f
        fun smooth(t: Float) = t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
        val bottom = smooth((h - y) / (h * BOTTOM_FADE))
        val side = smooth(minOf(x, w - x) / (w * SIDE_FADE))
        return bottom * side
    }

    /**
     * Drawn ABOVE the native head surface, in head-local dp (0..[w], 0..[h]): two thin light streams cross the
     * dissolved chest, as in 59509. Additive light only, so no rectangular darkening. Static; no state.
     */
    fun drawLightStreams(p: ContinuumPainter, w: Float, h: Float) {
        if (w <= 8f || h <= 8f) return
        p.setAdditive(true)
        val stream = Fill.Linear(0f, 0f, w, 0f, intArrayOf(Argb.withAlpha(Colors.violet, 0f), Colors.violetRim, Colors.cyan,
            Argb.withAlpha(Colors.cyan, 0f)))
        val a = VectorPath().moveTo(-w * .05f, h * .93f).cubicTo(w * .30f, h * .80f, w * .62f, h * .99f, w * 1.05f, h * .84f)
        val b = VectorPath().moveTo(-w * .05f, h * .86f).cubicTo(w * .36f, h * .97f, w * .70f, h * .78f, w * 1.05f, h * .92f)
        for (path in listOf(a, b)) {
            p.strokePath(path, 6f, stream, .10f)
            p.strokePath(path, 1.2f, stream, .85f)
        }
        p.setAdditive(false)
    }

    private fun ring(p: ContinuumPainter, cx: Float, cy: Float, rx: Float, ry: Float, degrees: Float,
                     from: Int, to: Int, width: Float) {
        val fill = Fill.Linear(cx - rx, cy, cx + rx, cy, intArrayOf(from, to, Argb.mix(to, Colors.cyan, .4f)))
        p.save(); p.rotate(degrees, cx, cy)
        p.strokeOval(cx - rx, cy - ry, cx + rx, cy + ry, width * 5f, fill, .07f)
        p.strokeOval(cx - rx, cy - ry, cx + rx, cy + ry, width * 2.2f, fill, .20f)
        p.strokeOval(cx - rx, cy - ry, cx + rx, cy + ry, width, fill, .85f)
        p.restore()
    }

    /** Segmented arc (segments > 0) or a thin continuous arc. Angles in degrees, clockwise from 3 o'clock. */
    private fun hudArc(p: ContinuumPainter, cx: Float, cy: Float, r: Float, start: Float, sweep: Float, color: Int,
                       segments: Int) {
        val fill = Fill.Solid(color)
        if (segments <= 0) {
            p.strokeArc(cx - r, cy - r, cx + r, cy + r, start, sweep, 1f, fill, .55f, roundCap = false)
            return
        }
        val step = sweep / segments
        for (i in 0 until segments) {
            val a = start + i * step
            val strong = i % 3 == 1
            p.strokeArc(cx - r, cy - r, cx + r, cy + r, a, step * .62f, if (strong) 5f else 3.2f, fill, .16f, roundCap = false)
            p.strokeArc(cx - r, cy - r, cx + r, cy + r, a, step * .62f, if (strong) 2.4f else 1.5f, fill,
                if (strong) .95f else .62f, roundCap = false)
        }
    }

    private fun sparks(p: ContinuumPainter, cx: Float, cy: Float, h: Float) {
        for (i in 0 until 18) {
            val a = (i * 137.5f) * (Math.PI / 180.0)
            val r = h * (.50f + (i % 5) * .045f)
            val x = cx + (cos(a) * r).toFloat(); val y = cy + (sin(a) * r * .55f).toFloat()
            val c = if (i % 3 == 0) Colors.magenta else if (i % 3 == 1) Colors.cyanSoft else Colors.violetRim
            p.fillCircle(x, y, if (i % 4 == 0) 1.6f else 1f, Fill.Solid(c), .85f)
        }
    }
}

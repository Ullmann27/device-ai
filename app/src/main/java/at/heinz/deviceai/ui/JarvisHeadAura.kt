package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisShellLayout.Box
import kotlin.math.cos
import kotlin.math.sin

/**
 * 59509 head stage: violet/cyan aura, two tilted orbital rings and segmented HUD arcs around the live head.
 *
 * Drawn by the live avatar view underneath its transparent native head surface, so the rings sit behind the 3D head
 * and never cover the face. Purely decorative: it reports no state. Time [t] is the existing avatar frame clock;
 * with reduced motion the caller passes a constant time and nothing moves.
 */
object JarvisHeadAura {
    private class LightStreams(val width: Float, val height: Float) {
        val fill = Fill.Linear(0f, 0f, width, 0f,
            intArrayOf(Argb.withAlpha(Colors.violet, 0f), Colors.violetRim, Colors.cyan,
                Argb.withAlpha(Colors.cyan, 0f)))
        val paths = arrayOf(
            VectorPath().moveTo(-width * .05f, height * .93f)
                .cubicTo(width * .30f, height * .80f, width * .62f, height * .99f, width * 1.05f, height * .84f),
            VectorPath().moveTo(-width * .05f, height * .86f)
                .cubicTo(width * .36f, height * .97f, width * .70f, height * .78f, width * 1.05f, height * .92f)
        )
    }

    private var lightStreams: LightStreams? = null

    /**
     * Approximate face centre of the anchored close-up framing inside the head stage. [headScale] is the live 3D
     * window's head size relative to the calibrated close-up (AvatarFraming); the crown margin is fixed, so the face
     * centre moves towards the top by the same factor.
     */
    fun faceCenter(stage: Box, anchorX: Float = .5f, headScale: Float = 1f): Pair<Float, Float> =
        stage.left + stage.width * anchorX to stage.top + stage.height * .45f * validScale(headScale)

    private fun validScale(scale: Float) = if (scale.isFinite() && scale > 0f) scale.coerceAtMost(1f) else 1f

    private fun wave(t: Float, period: Float, phase: Float = 0f) = sin(2f * Math.PI.toFloat() * t / period + phase)

    /** Actual runtime energy only. */
    internal fun activityBoost(energy: Float, speech: Float): Float {
        val e = if (energy.isFinite()) energy.coerceIn(0f, 1f) else .18f
        val s = if (speech.isFinite()) speech.coerceIn(0f, 1f) else 0f
        return (((e - .18f).coerceAtLeast(0f) * .16f) + s * .15f).coerceIn(0f, .27f)
    }

    fun draw(p: ContinuumPainter, stage: Box, anchorX: Float = .5f, t: Float = 0f, enabled: Boolean = true,
             headScale: Float = 1f, activityEnergy: Float = .18f, speechLevel: Float = 0f,
             referenceMode: Boolean = false) {
        if (!enabled || stage.width <= 8f || stage.height <= 8f || !t.isFinite()) return
        val (cx, cy) = faceCenter(stage, anchorX, headScale)
        val h = stage.height * validScale(headScale)
        val signal = activityBoost(activityEnergy, speechLevel)
        JarvisSceneFills.halo(p, cx - h * .08f, cy - h * .06f, h * .62f, Colors.violet, .26f + signal)
        JarvisSceneFills.halo(p, cx + h * .12f, cy + h * .04f, h * .46f, Colors.cyan, .14f + signal * .65f)
        if (signal > .015f) JarvisSceneFills.halo(p, cx + h * .03f,
            cy - h * .04f, h * .24f, Colors.glowCore, signal * .34f)
        if (referenceMode) return
        p.setAdditive(true)
        ring(p, cx, cy - h * .12f, h * .62f, h * .13f, -14f + 3f * wave(t, 9f), Colors.magenta, Colors.violet, 1.5f,
            bead = t / 14f)
        ring(p, cx, cy - h * .02f, h * .54f, h * .11f, 9f + 2.5f * wave(t, 11f, 1f), Colors.cyan, Colors.violetRim, 1.1f,
            bead = .5f - t / 17f)
        val sway = 6f * wave(t, 16f)
        hudArc(p, cx, cy, h * .68f, 148f + sway, 66f, Colors.magenta, segments = 9)
        hudArc(p, cx, cy, h * .68f, -36f - sway, 62f, Colors.cyan, segments = 8)
        hudArc(p, cx, cy, h * .76f, 160f - sway * .6f, 40f, Colors.violetRim, segments = 0)
        hudArc(p, cx, cy, h * .76f, -20f + sway * .6f, 34f, Colors.cyanSoft, segments = 0)
        sparks(p, cx, cy, h, t)
        p.setAdditive(false)
    }

    /** Share of the head stage height over which the bust dissolves at the bottom (alpha mask, not an overlay). */
    const val BOTTOM_FADE = .26f
    /** Share of the head stage width over which the cut shoulders dissolve at each side. */
    const val SIDE_FADE = .09f
    /** Side fades start below this share of the height, so ears and the back of the head are never masked. */
    const val SIDE_FADE_FROM = .60f

    /**
     * Alpha (0..1) the native head layer keeps at head-local ([x], [y]) inside a [w]×[h] stage. The Android view
     * applies the same mask with DST_IN gradients; previews multiply their head image with it.
     */
    fun fadeAlpha(x: Float, y: Float, w: Float, h: Float): Float {
        if (w <= 0f || h <= 0f) return 1f
        fun smooth(t: Float) = t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
        val bottom = smooth((h - y) / (h * BOTTOM_FADE))
        val side = if (y < h * SIDE_FADE_FROM) 1f else smooth(minOf(x, w - x) / (w * SIDE_FADE))
        return bottom * side
    }

    /**
     * Drawn ABOVE the native head surface, in head-local dp (0..[w], 0..[h]): two thin light streams cross the
     * dissolved chest, as in 59509. Additive light only, so no rectangular darkening. Static; no state.
     */
    fun drawLightStreams(p: ContinuumPainter, w: Float, h: Float) {
        if (w <= 8f || h <= 8f) return
        val streams = lightStreams?.takeIf { it.width == w && it.height == h }
            ?: LightStreams(w, h).also { lightStreams = it }
        p.setAdditive(true)
        for (path in streams.paths) {
            p.strokePath(path, 6f, streams.fill, .10f)
            p.strokePath(path, 1.2f, streams.fill, .85f)
        }
        p.setAdditive(false)
    }

    private fun ring(p: ContinuumPainter, cx: Float, cy: Float, rx: Float, ry: Float, degrees: Float,
                     from: Int, to: Int, width: Float, bead: Float) {
        val fill = JarvisSceneFills.cached(listOf("ring", cx.toInt(), cy.toInt(), rx.toInt(), from, to)) {
            Fill.Linear(cx - rx, cy, cx + rx, cy, intArrayOf(from, to, Argb.mix(to, Colors.cyan, .4f)))
        }
        p.save(); p.rotate(degrees, cx, cy)
        p.strokeOval(cx - rx, cy - ry, cx + rx, cy + ry, width * 5f, fill, .07f)
        p.strokeOval(cx - rx, cy - ry, cx + rx, cy + ry, width * 2.2f, fill, .20f)
        p.strokeOval(cx - rx, cy - ry, cx + rx, cy + ry, width, fill, .85f)
        // A light bead travels around the ring (continuous angle, no jump).
        val a = 2.0 * Math.PI * (((bead % 1f) + 1f) % 1f)
        val bx = cx + (cos(a) * rx).toFloat(); val by = cy + (sin(a) * ry).toFloat()
        JarvisSceneFills.halo(p, bx, by, width * 9f, from, .35f)
        p.fillCircle(bx, by, width * 1.2f, Fill.Solid(Colors.glowCore), .95f)
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

    private fun sparks(p: ContinuumPainter, cx: Float, cy: Float, h: Float, t: Float) {
        for (i in 0 until 18) {
            val a = (i * 137.5f) * (Math.PI / 180.0)
            val r = h * (.50f + (i % 5) * .045f)
            val x = cx + (cos(a) * r).toFloat(); val y = cy + (sin(a) * r * .55f).toFloat()
            val c = if (i % 3 == 0) Colors.magenta else if (i % 3 == 1) Colors.cyanSoft else Colors.violetRim
            p.fillCircle(x, y, if (i % 4 == 0) 1.6f else 1f, Fill.Solid(c), .45f + .4f * wave(t, 3f + i % 4, i.toFloat()))
        }
    }
}

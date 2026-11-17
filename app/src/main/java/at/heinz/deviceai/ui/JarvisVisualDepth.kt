package at.heinz.deviceai.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Visual depth for Code 52 ref-d (Bildwirkung): multi-plane space and glass
 * edge thickness derived from the cinema Z-slice contract.
 *
 * Z values match the cinema-flyin contract (`HollywoodFuiConstruct` on that
 * branch): rear glass −24 dp, content 0, front brackets +20 dp. Perspective
 * is capped at [HollywoodFuiConstruct.ENTRANCE_TILT_DEGREES] (14°). Visible
 * edge thickness is `|z| · sin(tilt)`, not an invented extrusion.
 *
 * Rear plates and shadows are decoration — control bounds stay on the
 * caller's box.
 */
object JarvisVisualDepth {
    const val REAR_GLASS_Z_DP = -24f
    const val CONTENT_Z_DP = 0f
    const val FRONT_BRACKET_Z_DP = 20f

    fun edgeThicknessDp(tiltDeg: Float = HollywoodFuiConstruct.ENTRANCE_TILT_DEGREES): Float {
        val tilt = tiltDeg.coerceIn(0f, HollywoodFuiConstruct.ENTRANCE_TILT_DEGREES)
        return abs(REAR_GLASS_Z_DP) * sin(tilt * PI.toFloat() / 180f)
    }

    fun frontLiftDp(tiltDeg: Float = HollywoodFuiConstruct.ENTRANCE_TILT_DEGREES): Float {
        val tilt = tiltDeg.coerceIn(0f, HollywoodFuiConstruct.ENTRANCE_TILT_DEGREES)
        return FRONT_BRACKET_Z_DP * sin(tilt * PI.toFloat() / 180f)
    }

    fun paintSpace(p: ContinuumPainter, w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        val span = maxOf(w, h)
        val C = JarvisDesign.Colors
        // Dark field with localized light islands (hero / orb), not a flat cyan wash.
        // Intensities raised toward ref-d (cinematic volume) without inventing HUD values.
        JarvisNeon.halo(p, w * .38f, h * .22f, span * .22f, C.violet, .42f)
        JarvisNeon.halo(p, w * .58f, h * .24f, span * .20f, C.cyan, .40f)
        JarvisNeon.halo(p, w * .48f, h * .20f, span * .16f, C.magenta, .20f)
        JarvisNeon.halo(p, w * .12f, h * .78f, span * .18f, C.magenta, .10f)
        JarvisNeon.halo(p, w * .50f, h * .92f, span * .40f, C.backgroundHaze, .14f)
        JarvisNeon.halo(p, w * .82f, h * .16f, span * .14f, C.warn, .16f)
        JarvisNeon.halo(p, w * .64f, h * .38f, span * .11f, C.warn, .10f)

        // Corner falloff: near-black vignette so mid-field reads closer than the edges.
        p.fillRect(0f, 0f, w, h, Fill.Radial(w * .50f, h * .36f, span * .92f,
            intArrayOf(0, Argb.withAlpha(C.background, .18f), Argb.withAlpha(C.background, .48f)),
            floatArrayOf(0f, .62f, 1f)), .55f)

        paintBokehLayer(p, w, h, count = 10, minR = 52f, maxR = 78f, alpha = .09f,
            seed = 0xC03D, includeAmber = true)
        paintBokehLayer(p, w, h, count = 16, minR = 20f, maxR = 46f, alpha = .13f,
            seed = 0xF11A, includeAmber = true)
        paintBokehLayer(p, w, h, count = 20, minR = 8f, maxR = 16f, alpha = .09f,
            seed = 0xA22B, includeAmber = false)
        paintLightArcs(p, w, h)
    }

    private fun paintBokehLayer(
        p: ContinuumPainter, w: Float, h: Float,
        count: Int, minR: Float, maxR: Float, alpha: Float, seed: Int, includeAmber: Boolean
    ) {
        val C = JarvisDesign.Colors
        var s = seed
        p.setAdditive(true)
        repeat(count) { i ->
            s = s * 1103515245 + 12345
            val bx = ((s ushr 8) % 10000) / 10000f * w
            s = s * 1103515245 + 12345
            val by = ((s ushr 8) % 10000) / 10000f * h
            s = s * 1103515245 + 12345
            val br = minR + ((s ushr 8) % 1000) / 1000f * (maxR - minR)
            val color = when {
                includeAmber && i % 5 == 3 -> C.warn
                i % 3 == 0 -> C.violet
                i % 3 == 1 -> C.cyan
                else -> C.magenta
            }
            // Nested discs approximate distance blur without a bitmap convolve.
            p.fillCircle(bx, by, br, Fill.Radial(bx, by, br, intArrayOf(Argb.withAlpha(color, alpha), 0)))
            p.fillCircle(bx, by, br * .55f, Fill.Radial(bx, by, br * .55f,
                intArrayOf(Argb.withAlpha(color, alpha * 1.4f), 0)))
            p.fillCircle(bx, by, br * .28f, Fill.Radial(bx, by, br * .28f,
                intArrayOf(Argb.withAlpha(color, alpha * 1.8f), 0)))
        }
        p.setAdditive(false)
    }

    /** Darken an already painted empty glass panel so the hero stays dominant. Boxes do not shrink. */
    fun recedeVacantPanel(p: ContinuumPainter, b: JarvisShellLayout.Box) {
        if (b.width <= 2f || b.height <= 2f) return
        val r = minOf(14f, minOf(b.width, b.height) * .25f).coerceAtLeast(1f)
        p.fillRoundRect(b.left, b.top, b.right, b.bottom, r,
            Fill.Solid(Argb.rgb(1, 4, 10)), .40f)
        val inset = minOf(b.width, b.height) * .08f
        if (inset > 2f) {
            p.fillRoundRect(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset,
                (r - 2f).coerceAtLeast(1f),
                Fill.Radial(b.centerX, b.centerY, maxOf(b.width, b.height) * .55f,
                    intArrayOf(Argb.withAlpha(Argb.rgb(0, 2, 6), .22f), Argb.withAlpha(Argb.rgb(0, 2, 6), .50f))),
                .55f)
        }
    }

    /**
     * Glowing ring platform under the three work windows (ref-b / ref-c). Decoration only —
     * the windows keep their layout boxes and fly-away remains Bot 04 / #692.
     */
    fun paintWorkStage(p: ContinuumPainter, stage: JarvisShellLayout.Box) {
        if (stage.width <= 8f || stage.height <= 8f) return
        val cx = stage.centerX
        val cy = stage.bottom + stage.height * .06f
        val rx = stage.width * .42f
        val ry = rx * .22f
        val C = JarvisDesign.Colors
        p.setAdditive(true)
        p.fillOval(cx - rx * 1.05f, cy - ry * 1.15f, cx + rx * 1.05f, cy + ry * 1.35f,
            Fill.Radial(cx, cy, rx, intArrayOf(Argb.withAlpha(C.cyan, .22f),
                Argb.withAlpha(C.violet, .14f), Argb.withAlpha(C.magenta, .06f), 0)), .90f)
        p.strokeOval(cx - rx, cy - ry, cx + rx, cy + ry, 10f, Fill.Solid(C.cyan), .10f)
        p.strokeOval(cx - rx, cy - ry, cx + rx, cy + ry, 3.2f, Fill.Solid(C.violetRim), .28f)
        p.strokeOval(cx - rx, cy - ry, cx + rx, cy + ry, 1.3f,
            Fill.Linear(cx - rx, cy, cx + rx, cy, intArrayOf(C.magenta, C.cyan, C.violet, C.cyan, C.magenta)), .85f)
        p.strokeOval(cx - rx * .72f, cy - ry * .62f, cx + rx * .72f, cy + ry * .62f, 1.1f,
            Fill.Solid(C.cyan), .55f)
        p.strokeOval(cx - rx * .46f, cy - ry * .38f, cx + rx * .46f, cy + ry * .38f, 2.4f,
            Fill.Solid(C.magenta), .22f)
        p.strokeOval(cx - rx * 1.08f, cy - ry * 1.12f, cx + rx * 1.08f, cy + ry * 1.12f, 2.0f,
            Fill.Solid(C.warn), .22f)
        p.strokeOval(cx - rx * .88f, cy - ry * .78f, cx + rx * .88f, cy + ry * .78f, 1.4f,
            Fill.Linear(cx - rx, cy, cx + rx, cy, intArrayOf(C.warn, C.cyan, C.warn)), .40f)
        // Upward well — no second brain; the original orb stays in the hero column.
        p.fillRect(cx - rx * .18f, stage.top + stage.height * .35f, cx + rx * .18f, cy,
            Fill.Linear(cx, cy, cx, stage.top + stage.height * .35f,
                intArrayOf(Argb.withAlpha(C.cyan, .20f), Argb.withAlpha(C.violet, .08f), 0)), .55f)
        p.setAdditive(false)
    }

    private fun paintLightArcs(p: ContinuumPainter, w: Float, h: Float) {
        val C = JarvisDesign.Colors
        p.setAdditive(true)
        p.strokeArc(w * -.05f, h * .08f, w * .62f, h * .95f, 200f, 70f, 10f, Fill.Solid(C.cyan), .06f)
        p.strokeArc(w * -.05f, h * .08f, w * .62f, h * .95f, 200f, 70f, 1.4f, Fill.Solid(C.cyan), .22f)
        p.strokeArc(w * .38f, h * -.10f, w * 1.12f, h * .72f, 110f, 55f, 8f, Fill.Solid(C.violet), .05f)
        p.strokeArc(w * .38f, h * -.10f, w * 1.12f, h * .72f, 110f, 55f, 1.2f, Fill.Solid(C.violet), .18f)
        p.strokeArc(w * .18f, h * .22f, w * .92f, h * 1.05f, 220f, 48f, 9f, Fill.Solid(C.warn), .05f)
        p.strokeArc(w * .18f, h * .22f, w * .92f, h * 1.05f, 220f, 48f, 1.3f, Fill.Solid(C.warn), .16f)
        p.setAdditive(false)
    }
}

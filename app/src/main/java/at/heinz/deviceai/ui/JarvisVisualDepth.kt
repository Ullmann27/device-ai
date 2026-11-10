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
        // FAR: large soft nebulae (distance wash).
        JarvisNeon.halo(p, w * .34f, h * .22f, span * .38f, C.violet, .22f)
        JarvisNeon.halo(p, w * .72f, h * .28f, span * .36f, C.cyan, .16f)
        JarvisNeon.halo(p, w * .50f, h * .82f, span * .44f, C.cyan, .11f)
        JarvisNeon.halo(p, w * .10f, h * .70f, span * .28f, C.magenta, .10f)
        JarvisNeon.halo(p, w * .45f, h * .94f, span * .60f, C.backgroundHaze, .16f)
        // Spare warm key-light (palette extension, one far source).
        JarvisNeon.halo(p, w * .82f, h * .16f, span * .18f, C.warn, .10f)

        paintBokehLayer(p, w, h, count = 16, minR = 20f, maxR = 46f, alpha = .11f,
            seed = 0xF11A, includeAmber = true)
        paintBokehLayer(p, w, h, count = 20, minR = 8f, maxR = 16f, alpha = .08f,
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
                includeAmber && i == 3 -> C.warn
                i % 3 == 0 -> C.violet
                i % 3 == 1 -> C.cyan
                else -> C.magenta
            }
            // Nested discs approximate distance blur without a bitmap convolve.
            p.fillCircle(bx, by, br, Fill.Radial(bx, by, br, intArrayOf(Argb.withAlpha(color, alpha), 0)))
            p.fillCircle(bx, by, br * .55f, Fill.Radial(bx, by, br * .55f,
                intArrayOf(Argb.withAlpha(color, alpha * 1.4f), 0)))
        }
        p.setAdditive(false)
    }

    private fun paintLightArcs(p: ContinuumPainter, w: Float, h: Float) {
        val C = JarvisDesign.Colors
        p.setAdditive(true)
        p.strokeArc(w * -.05f, h * .08f, w * .62f, h * .95f, 200f, 70f, 10f, Fill.Solid(C.cyan), .06f)
        p.strokeArc(w * -.05f, h * .08f, w * .62f, h * .95f, 200f, 70f, 1.4f, Fill.Solid(C.cyan), .22f)
        p.strokeArc(w * .38f, h * -.10f, w * 1.12f, h * .72f, 110f, 55f, 8f, Fill.Solid(C.violet), .05f)
        p.strokeArc(w * .38f, h * -.10f, w * 1.12f, h * .72f, 110f, 55f, 1.2f, Fill.Solid(C.violet), .18f)
        p.setAdditive(false)
    }
}

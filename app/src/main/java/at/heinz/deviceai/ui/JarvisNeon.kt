package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisDesign.Glow

/**
 * Reusable glow / neon primitives on the platform-neutral [ContinuumPainter]
 * (same drawing stack as PR #477 – Android Canvas on device, Java2D in JVM previews).
 * Glow = one soft light layer with falloff (3 additive strokes of growing width, decreasing alpha),
 * never offset copies of the shape or text, so it does not double at large font scales.
 */
object JarvisNeon {
    private fun edge(l: Float, t: Float, r: Float, b: Float) = Fill.Linear(l, t, r, b, Colors.neonEdge)
    private val falloff = floatArrayOf(1f, .55f, .25f)

    /** Soft halo along a rounded rect outline. */
    fun rimGlow(p: ContinuumPainter, l: Float, t: Float, r: Float, b: Float, radius: Float, glow: Glow, boost: Float = 1f,
                fill: Fill = edge(l, t, r, b)) {
        if (glow == Glow.OFF) return
        p.setAdditive(true)
        falloff.forEachIndexed { i, k ->
            p.strokeRoundRect(l, t, r, b, radius, glow.haloWidth * (i + 1) / falloff.size * 2f, fill, glow.haloAlpha * k * boost / 2f)
        }
        p.setAdditive(false)
    }

    /**
     * Glass card (Review 1): #0A1128 @50 %, 1 px cyan rim @40 %, radius 14 dp.
     * [nativeGlass] = backdrop blur + rim glow already drawn by JarvisGlassRenderer (Android) → skip fallback glow.
     */
    fun glassCard(p: ContinuumPainter, l: Float, t: Float, r: Float, b: Float,
                  radius: Float = JarvisDesign.Metrics.radiusCard, glow: Glow = Glow.NORMAL,
                  active: Boolean = false, glowBoost: Float = 1f, nativeGlass: Boolean = false) {
        if (!nativeGlass) rimGlow(p, l, t, r, b, radius, glow, glowBoost * (if (active) 1.4f else .6f), Fill.Solid(Colors.cyan))
        p.fillRoundRect(l, t, r, b, radius, Fill.Linear(l, t, r, b, intArrayOf(Colors.surface, Colors.surfaceLow)))
        p.fillRoundRect(l + 1f, t + 1f, r - 1f, t + (b - t) * .35f, radius - 1f,
            Fill.Linear(0f, t, 0f, t + (b - t) * .35f, intArrayOf(Argb.of(18, 0, 255, 255), 0)))
        p.strokeRoundRect(l, t, r, b, radius, 1f, Fill.Solid(Colors.rim))
        if (active) p.strokeRoundRect(l, t, r, b, radius, 1.4f, Fill.Solid(Colors.activeWhite), .5f)
    }

    /** Neon line (light trail, laser connection, divider). */
    fun line(p: ContinuumPainter, x0: Float, y0: Float, x1: Float, y1: Float, color: Int = Colors.cyan,
             width: Float = 1f, glow: Glow = Glow.NORMAL) {
        if (glow != Glow.OFF) {
            p.setAdditive(true)
            falloff.forEachIndexed { i, k -> p.drawLine(x0, y0, x1, y1, width + glow.haloWidth * (i + 1) / 3f, Fill.Solid(color), glow.haloAlpha * k / 2f) }
            p.setAdditive(false)
        }
        p.drawLine(x0, y0, x1, y1, width, Fill.Solid(color))
    }

    /** Laser connection drawn from node to panel; [progress] 0..1 from JarvisMotion.laser(). */
    fun laser(p: ContinuumPainter, x0: Float, y0: Float, x1: Float, y1: Float, progress: Float, glow: Glow = Glow.NORMAL) {
        val k = progress.coerceIn(0f, 1f)
        if (k <= 0f) return
        line(p, x0, y0, x0 + (x1 - x0) * k, y0 + (y1 - y0) * k, Colors.cyan, 1f, glow)
    }

    /** Neon ring / orbit (avatar halo, brain-orb pedestal, gauges). */
    fun ring(p: ContinuumPainter, cx: Float, cy: Float, rx: Float, ry: Float = rx, width: Float = 1.5f,
             colors: IntArray = Colors.neonEdge, glow: Glow = Glow.NORMAL) {
        val fill = Fill.Linear(cx - rx, cy, cx + rx, cy, colors)
        if (glow != Glow.OFF) {
            p.setAdditive(true)
            falloff.forEachIndexed { i, k -> p.strokeOval(cx - rx, cy - ry, cx + rx, cy + ry, width + glow.haloWidth * (i + 1) / 3f, fill, glow.haloAlpha * k / 2f) }
            p.setAdditive(false)
        }
        p.strokeOval(cx - rx, cy - ry, cx + rx, cy + ry, width, fill)
    }

    /** Radial light pool behind hero content (head, brain orb). */
    fun halo(p: ContinuumPainter, cx: Float, cy: Float, radius: Float, color: Int = Colors.cyan, strength: Float = .35f) =
        p.fillCircle(cx, cy, radius, Fill.Radial(cx, cy, radius, intArrayOf(Argb.withAlpha(color, strength), 0)))

    /**
     * Neon text: one soft light pool behind the text bounds (radius scales with the font size), then crisp text.
     * No offset copies, so large system fonts stay single and sharp.
     */
    fun text(p: ContinuumPainter, s: String, x: Float, y: Float, style: TextStyle, color: Int = Colors.cyan,
             align: TextAlign = TextAlign.LEFT, glow: Glow = Glow.SUBTLE) {
        if (glow != Glow.OFF && s.isNotEmpty()) {
            val w = p.measureText(s, style); val h = p.capHeight(style)
            val l = when (align) { TextAlign.LEFT -> x; TextAlign.CENTER -> x - w / 2f; TextAlign.RIGHT -> x - w }
            val pad = style.size * .45f
            p.setAdditive(true)
            p.fillRoundRect(l - pad, y - h - pad, l + w + pad, y + pad, pad * 2f,
                Fill.Linear(0f, y - h - pad, 0f, y + pad, intArrayOf(0, Argb.withAlpha(color, glow.haloAlpha * .45f), 0)))
            p.setAdditive(false)
        }
        p.drawText(s, x, y, style, Fill.Solid(color), align)
    }
}

package at.heinz.deviceai.ui

/**
 * Reusable glow / neon primitives on the platform-neutral [ContinuumPainter]
 * (same drawing stack as PR #477 – Android Canvas on device, JVM painter in tests/previews).
 * Each effect draws a wide faint halo pass, then the crisp core.
 */
object JarvisNeon {
    private fun edge(l: Float, t: Float, r: Float, b: Float) = Fill.Linear(l, t, r, b, JarvisDesign.Colors.neonEdge)

    /** Glass card with luminous cyan→violet rim (wraps ContinuumSurfaces.glassCard + outer glow). */
    fun glassCard(p: ContinuumPainter, l: Float, t: Float, r: Float, b: Float,
                  radius: Float = JarvisDesign.Metrics.radiusCard, glow: JarvisDesign.Glow = JarvisDesign.Glow.NORMAL,
                  active: Boolean = false) {
        if (glow != JarvisDesign.Glow.OFF) {
            p.setAdditive(true)
            p.strokeRoundRect(l, t, r, b, radius, glow.haloWidth, edge(l, t, r, b), glow.haloAlpha * (if (active) 1.4f else 1f))
            p.setAdditive(false)
        }
        ContinuumSurfaces.glassCard(p, l, t, r, b, radius)
        if (active) p.strokeRoundRect(l, t, r, b, radius, 1.6f, Fill.Solid(JarvisDesign.Colors.cyan), .9f)
    }

    /** Neon line (light trail, divider, orbit spoke). */
    fun line(p: ContinuumPainter, x0: Float, y0: Float, x1: Float, y1: Float, color: Int = JarvisDesign.Colors.cyan,
             width: Float = 1.5f, glow: JarvisDesign.Glow = JarvisDesign.Glow.NORMAL) {
        if (glow != JarvisDesign.Glow.OFF) p.drawLine(x0, y0, x1, y1, width + glow.haloWidth, Fill.Solid(color), glow.haloAlpha)
        p.drawLine(x0, y0, x1, y1, width, Fill.Solid(color))
    }

    /** Neon ring / orbit (avatar halo, brain-orb pedestal, gauges). */
    fun ring(p: ContinuumPainter, cx: Float, cy: Float, rx: Float, ry: Float = rx, width: Float = 1.5f,
             colors: IntArray = JarvisDesign.Colors.neonEdge, glow: JarvisDesign.Glow = JarvisDesign.Glow.NORMAL) {
        val fill = Fill.Linear(cx - rx, cy, cx + rx, cy, colors)
        if (glow != JarvisDesign.Glow.OFF) p.strokeOval(cx - rx, cy - ry, cx + rx, cy + ry, width + glow.haloWidth, fill, glow.haloAlpha)
        p.strokeOval(cx - rx, cy - ry, cx + rx, cy + ry, width, fill)
    }

    /** Radial light pool behind hero content (head, brain orb). */
    fun halo(p: ContinuumPainter, cx: Float, cy: Float, radius: Float, color: Int = JarvisDesign.Colors.cyan, strength: Float = .35f) =
        p.fillCircle(cx, cy, radius, Fill.Radial(cx, cy, radius, intArrayOf(Argb.withAlpha(color, strength), 0)))

    /** Neon text: blurred-looking halo via offset passes, then crisp text. */
    fun text(p: ContinuumPainter, s: String, x: Float, y: Float, style: TextStyle, color: Int = JarvisDesign.Colors.cyan,
             align: TextAlign = TextAlign.LEFT, glow: JarvisDesign.Glow = JarvisDesign.Glow.SUBTLE) {
        if (glow != JarvisDesign.Glow.OFF) {
            val d = glow.haloWidth / 4f
            val halo = Fill.Solid(Argb.withAlpha(color, glow.haloAlpha))
            for ((dx, dy) in listOf(-d to 0f, d to 0f, 0f to -d, 0f to d)) p.drawText(s, x + dx, y + dy, style, halo, align)
        }
        p.drawText(s, x, y, style, Fill.Solid(color), align)
    }
}

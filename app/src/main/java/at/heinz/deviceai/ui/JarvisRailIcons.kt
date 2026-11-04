package at.heinz.deviceai.ui

/**
 * Line icons of the ref5 rail (Integration; REVIEW-3: speech bubble etc. differed from ref5). Drawn as vector paths on
 * a 24-unit grid through [ContinuumPainter], so the same geometry renders on Android (Canvas) and in the JVM preview.
 * Colour comes from the caller (JarvisNeon.button → JarvisDesign.Colors.cyanSoft / cyan, System Status: violetSoft).
 * Stroke: ref5 icon line ≈ 2.5 px of a 25 px icon → 2.4 units (≈ 1.6 dp at Styles.button.iconSize 16 dp).
 */
object JarvisRailIcons {
    const val STROKE_UNITS = 2.4f
    private val path = VectorPath()

    fun has(target: String) = target in TARGETS
    private val TARGETS = setOf("home", "chat", "memory", "logic", "research", "agents", "tasks", "web", "learning", "device", "system")

    /** Draws the ref5 icon for a rail [target] into the square (left, top, size). Returns false for unknown targets. */
    fun draw(p: ContinuumPainter, target: String, left: Float, top: Float, size: Float, color: Int): Boolean {
        if (!has(target)) return false
        val k = size / 24f; val w = STROKE_UNITS * k; val f = Fill.Solid(color)
        fun x(v: Float) = left + v * k
        fun y(v: Float) = top + v * k
        fun poly(vararg pts: Float, close: Boolean = false) {
            path.reset().moveTo(x(pts[0]), y(pts[1]))
            var i = 2; while (i < pts.size) { path.lineTo(x(pts[i]), y(pts[i + 1])); i += 2 }
            if (close) path.close()
            p.strokePath(path, w, f)
        }
        fun line(x0: Float, y0: Float, x1: Float, y1: Float) = p.drawLine(x(x0), y(y0), x(x1), y(y1), w, f)
        fun circle(cx: Float, cy: Float, r: Float) = p.strokeOval(x(cx - r), y(cy - r), x(cx + r), y(cy + r), w, f)
        fun rrect(l: Float, t: Float, r: Float, b: Float, rad: Float) = p.strokeRoundRect(x(l), y(t), x(r), y(b), rad * k, w, f)
        when (target) {
            "home" -> { poly(2.5f, 11.5f, 12f, 3f, 21.5f, 11.5f); poly(5f, 9.5f, 5f, 21f, 19f, 21f, 19f, 9.5f); poly(10f, 21f, 10f, 15f, 14f, 15f, 14f, 21f) }
            "chat" -> { // rounded bubble, tail bottom left, small stroke inside (ref5)
                path.reset().moveTo(x(7f), y(4f)).lineTo(x(17f), y(4f)).quadTo(x(21f), y(4f), x(21f), y(8f)).lineTo(x(21f), y(12f))
                    .quadTo(x(21f), y(16f), x(17f), y(16f)).lineTo(x(10f), y(16f)).lineTo(x(5f), y(20.5f)).lineTo(x(6f), y(15.8f))
                    .quadTo(x(3f), y(15f), x(3f), y(12f)).lineTo(x(3f), y(8f)).quadTo(x(3f), y(4f), x(7f), y(4f)).close()
                p.strokePath(path, w, f); line(13.5f, 9.5f, 15.5f, 11.5f)
            }
            "memory" -> { poly(12f, 3f, 21f, 7.5f, 12f, 12f, 3f, 7.5f, close = true); poly(3f, 12f, 12f, 16.5f, 21f, 12f); poly(3f, 16.5f, 12f, 21f, 21f, 16.5f) }
            "logic" -> { circle(12f, 5f, 2.5f); circle(5f, 18.5f, 2.5f); circle(19f, 18.5f, 2.5f); circle(12f, 12.5f, 1.6f)
                line(12f, 7.5f, 12f, 10.9f); line(10.7f, 13.6f, 6.8f, 16.8f); line(13.3f, 13.6f, 17.2f, 16.8f) }
            "research" -> { circle(10f, 10f, 6.5f); line(14.8f, 14.8f, 21f, 21f) }
            "agents" -> { poly(12f, 2.5f, 20.5f, 7f, 20.5f, 17f, 12f, 21.5f, 3.5f, 17f, 3.5f, 7f, close = true)
                poly(3.5f, 7f, 12f, 11.5f, 20.5f, 7f); line(12f, 11.5f, 12f, 21.5f) }
            "tasks" -> { rrect(3.5f, 5f, 20.5f, 21f, 2.5f); line(8f, 3f, 8f, 7f); line(16f, 3f, 16f, 7f); poly(8f, 13.5f, 11f, 16.5f, 16.5f, 10.5f) }
            "web" -> { circle(12f, 12f, 9f); p.strokeOval(x(8f), y(3f), x(16f), y(21f), w, f); line(3f, 12f, 21f, 12f)
                line(4.8f, 7.5f, 19.2f, 7.5f); line(4.8f, 16.5f, 19.2f, 16.5f) }
            "learning" -> { poly(12f, 4f, 22f, 9f, 12f, 14f, 2f, 9f, close = true); poly(6f, 11f, 6f, 16f, 12f, 19f, 18f, 16f, 18f, 11f); line(22f, 9f, 22f, 14f) }
            "device" -> { rrect(7f, 2.5f, 17f, 21.5f, 2.5f); line(10.5f, 18.5f, 13.5f, 18.5f) }
            "system" -> poly(2f, 12f, 7f, 12f, 9f, 7f, 12f, 18f, 15f, 4f, 17f, 12f, 22f, 12f)
        }
        return true
    }
}

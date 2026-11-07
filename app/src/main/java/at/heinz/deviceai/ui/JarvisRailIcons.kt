package at.heinz.deviceai.ui

/** Shared, cached 24-unit icon family. */
object JarvisRailIcons {
    const val STROKE_UNITS = 1.8f
    private const val FEATURE_STROKE_UNITS = 1.5f
    private data class Glyph(val main: List<VectorPath>,
                             val secondary: List<VectorPath> = emptyList(),
                             val accent: List<VectorPath> = emptyList(),
                             val weight: Float = 1f)
    private val fills = HashMap<Int, Fill.Solid>()
    private val glyphs = mapOf(
        "home" to Glyph(listOf(
            poly(3f, 10.5f, 12f, 3.5f, 21f, 10.5f),
            VectorPath().moveTo(5.2f, 9f).lineTo(5.2f, 19f).quadTo(5.2f, 20.5f, 6.7f, 20.5f)
                .lineTo(17.3f, 20.5f).quadTo(18.8f, 20.5f, 18.8f, 19f).lineTo(18.8f, 9f)),
            accent = listOf(poly(9.5f, 20.5f, 9.5f, 14f, 14.5f, 14f, 14.5f, 20.5f))),
        "chat" to Glyph(listOf(
            VectorPath().moveTo(7.5f, 4.5f).lineTo(16.5f, 4.5f)
                .quadTo(20.5f, 4.5f, 20.5f, 8.5f).lineTo(20.5f, 13f)
                .quadTo(20.5f, 17f, 16.5f, 17f).lineTo(9.3f, 17f)
                .lineTo(5.3f, 19.6f).quadTo(4.5f, 20.1f, 4.7f, 19.2f)
                .lineTo(5.3f, 16.1f).quadTo(3.5f, 15.2f, 3.5f, 12.8f)
                .lineTo(3.5f, 8.5f).quadTo(3.5f, 4.5f, 7.5f, 4.5f).close()),
            accent = listOf(poly(8f, 10.5f, 16f, 10.5f))),
        "memory" to Glyph(listOf(poly(12f, 3.5f, 21f, 8f, 12f, 12.5f, 3f, 8f).close()),
            secondary = listOf(poly(3.5f, 12.2f, 12f, 16.5f, 20.5f, 12.2f)),
            accent = listOf(poly(3.5f, 16.5f, 12f, 20.8f, 20.5f, 16.5f)), weight = .95f),
        "logic" to Glyph(listOf(
            VectorPath().moveTo(12f, 7.6f).lineTo(12f, 11f)
                .cubicTo(12f, 14f, 5.5f, 12.8f, 5.5f, 16.1f)
                .moveTo(12f, 11f).cubicTo(12f, 14f, 18.5f, 12.8f, 18.5f, 16.1f),
            oval(9.6f, 2.8f, 14.4f, 7.6f), oval(3.2f, 16.1f, 7.8f, 20.7f)),
            accent = listOf(oval(16.2f, 16.1f, 20.8f, 20.7f))),
        "research" to Glyph(listOf(oval(3.7f, 3.7f, 17.3f, 17.3f)),
            accent = listOf(poly(15.4f, 15.4f, 20.6f, 20.6f))),
        "agents" to Glyph(listOf(poly(12f, 3f, 20f, 7.5f, 20f, 16.5f,
            12f, 21f, 4f, 16.5f, 4f, 7.5f).close()),
            secondary = listOf(poly(4f, 7.5f, 12f, 12f, 12f, 21f)),
            accent = listOf(poly(12f, 12f, 20f, 7.5f)), weight = .95f),
        "tasks" to Glyph(listOf(roundRect(4f, 5f, 20f, 21f, 2.6f),
            poly(8f, 3f, 8f, 7f), poly(16f, 3f, 16f, 7f)),
            accent = listOf(poly(8f, 13.4f, 11f, 16.2f, 16.3f, 11.2f))),
        "web" to Glyph(listOf(oval(3.4f, 3.4f, 20.6f, 20.6f)),
            secondary = listOf(oval(8.4f, 3.4f, 15.6f, 20.6f)),
            accent = listOf(poly(3.4f, 12f, 20.6f, 12f)), weight = .95f),
        "learning" to Glyph(listOf(poly(3f, 9f, 12f, 4.7f, 21f, 9f, 12f, 13.3f).close()),
            secondary = listOf(VectorPath().moveTo(6.2f, 10.6f).lineTo(6.2f, 16.1f)
                .quadTo(12f, 20.2f, 17.8f, 16.1f).lineTo(17.8f, 10.6f)),
            accent = listOf(poly(21f, 9f, 21f, 15.7f))),
        "device" to Glyph(listOf(roundRect(6.5f, 2.8f, 17.5f, 21.2f, 2.4f)),
            accent = listOf(poly(10f, 18.3f, 14f, 18.3f)), weight = 1.04f),
        "system" to Glyph(listOf(poly(2.8f, 12f, 7.5f, 12f, 9.3f, 7.2f,
            12.2f, 18.3f, 15.2f, 5.3f, 17.2f, 12f)),
            accent = listOf(poly(17.2f, 12f, 21.2f, 12f)))
    )

    fun has(target: String) = target in glyphs

    /** Small controls keep stronger strokes. */
    fun draw(p: ContinuumPainter, target: String, left: Float, top: Float, size: Float, color: Int): Boolean =
        drawGlyph(p, target, left, top, size, color, color, STROKE_UNITS, 1.1f, 1f)

    /** Restrained duotone for feature cards. */
    fun drawFeature(p: ContinuumPainter, target: String, left: Float, top: Float, size: Float,
                    color: Int, detailColor: Int, alpha: Float = 1f): Boolean =
        drawGlyph(p, target, left, top, size, color, detailColor, FEATURE_STROKE_UNITS, 0f, alpha)

    private fun drawGlyph(p: ContinuumPainter, target: String, left: Float, top: Float, size: Float,
                          color: Int, detailColor: Int, stroke: Float, minimum: Float, alpha: Float): Boolean {
        val glyph = glyphs[target] ?: return false
        if (!left.isFinite() || !top.isFinite() || !size.isFinite() || size <= 0f) return false
        val scale = size / 24f
        val width = maxOf(stroke * glyph.weight, minimum / scale)
        val primary = fill(color)
        val detail = fill(detailColor)
        p.save(); p.translate(left, top); p.scale(scale, scale)
        glyph.main.forEach { p.strokePath(it, width, primary, alpha) }
        glyph.secondary.forEach { p.strokePath(it, width * .82f, primary, alpha * .86f) }
        glyph.accent.forEach { p.strokePath(it, width * .90f, detail, alpha) }
        p.restore()
        return true
    }

    private fun fill(color: Int): Fill.Solid = fills[color] ?: run {
        if (fills.size >= 32) fills.clear()
        Fill.Solid(color).also { fills[color] = it }
    }

    private fun poly(vararg xy: Float): VectorPath = VectorPath().apply {
        moveTo(xy[0], xy[1])
        var i = 2
        while (i < xy.size) { lineTo(xy[i], xy[i + 1]); i += 2 }
    }

    private fun oval(left: Float, top: Float, right: Float, bottom: Float): VectorPath {
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val dx = (right - left) * .2761425f
        val dy = (bottom - top) * .2761425f
        return VectorPath().moveTo(cx, top)
            .cubicTo(cx + dx, top, right, cy - dy, right, cy)
            .cubicTo(right, cy + dy, cx + dx, bottom, cx, bottom)
            .cubicTo(cx - dx, bottom, left, cy + dy, left, cy)
            .cubicTo(left, cy - dy, cx - dx, top, cx, top).close()
    }

    private fun roundRect(left: Float, top: Float, right: Float, bottom: Float, r: Float): VectorPath =
        VectorPath().moveTo(left + r, top).lineTo(right - r, top)
            .quadTo(right, top, right, top + r).lineTo(right, bottom - r)
            .quadTo(right, bottom, right - r, bottom).lineTo(left + r, bottom)
            .quadTo(left, bottom, left, bottom - r).lineTo(left, top + r)
            .quadTo(left, top, left + r, top).close()
}

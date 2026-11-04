package at.heinz.deviceai.ui

/** Text-only accessibility scale. Geometry, icons and the 3D viewport are never stretched with the font. */
class JarvisTextScalePainter(private val base: ContinuumPainter) : ContinuumPainter by base {
    private val styles = HashMap<TextStyle, TextStyle>()
    var fontScale: Float = 1f
        set(value) {
            val next = if (value.isFinite()) value.coerceIn(.85f, 2f) else 1f
            if (field == next) return
            field = next
            styles.clear()
        }

    private fun scaled(style: TextStyle): TextStyle = if (fontScale == 1f) style
        else styles.getOrPut(style) { style.copy(size = style.size * fontScale, letterSpacing = style.letterSpacing * fontScale) }

    override fun drawText(text: String, x: Float, y: Float, style: TextStyle, fill: Fill, align: TextAlign, alpha: Float) =
        base.drawText(text, x, y, scaled(style), fill, align, alpha)

    override fun measureText(text: String, style: TextStyle): Float = base.measureText(text, scaled(style))
    override fun capHeight(style: TextStyle): Float = base.capHeight(scaled(style))
}

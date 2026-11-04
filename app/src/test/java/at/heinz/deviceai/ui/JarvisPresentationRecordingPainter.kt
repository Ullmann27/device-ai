package at.heinz.deviceai.ui

/** Records the actual renderer output without replacing any card or presentation logic. */
internal class JarvisPresentationRecordingPainter : ContinuumPainter {
    val texts = mutableListOf<String>()
    override fun save() {}
    override fun restore() {}
    override fun translate(dx: Float, dy: Float) {}
    override fun scale(sx: Float, sy: Float, px: Float, py: Float) {}
    override fun rotate(degrees: Float, px: Float, py: Float) {}
    override fun clipRect(left: Float, top: Float, right: Float, bottom: Float) {}
    override fun clipRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {}
    override fun fillRect(left: Float, top: Float, right: Float, bottom: Float, fill: Fill, alpha: Float) {}
    override fun fillRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, fill: Fill, alpha: Float) {}
    override fun strokeRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, width: Float, fill: Fill, alpha: Float) {}
    override fun fillOval(left: Float, top: Float, right: Float, bottom: Float, fill: Fill, alpha: Float) {}
    override fun strokeOval(left: Float, top: Float, right: Float, bottom: Float, width: Float, fill: Fill, alpha: Float) {}
    override fun strokeArc(left: Float, top: Float, right: Float, bottom: Float, startDegrees: Float, sweepDegrees: Float, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) {}
    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) {}
    override fun fillPath(path: VectorPath, fill: Fill, alpha: Float) {}
    override fun strokePath(path: VectorPath, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) {}
    override fun drawText(text: String, x: Float, y: Float, style: TextStyle, fill: Fill, align: TextAlign, alpha: Float) { texts += text }
    override fun measureText(text: String, style: TextStyle) = text.length * style.size * .5f
    override fun capHeight(style: TextStyle) = style.size * .7f
}

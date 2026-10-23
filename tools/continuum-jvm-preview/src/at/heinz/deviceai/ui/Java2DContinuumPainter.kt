package at.heinz.deviceai.ui

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.MultipleGradientPaint
import java.awt.Paint
import java.awt.PaintContext
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.font.FontRenderContext
import java.awt.font.TextAttribute
import java.awt.geom.AffineTransform
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.ColorModel
import java.awt.image.DirectColorModel
import java.awt.image.Raster
import java.io.File
import kotlin.math.PI
import kotlin.math.atan2

/**
 * JVM implementation of [ContinuumPainter] on Java2D, used only for previews and tests (never shipped).
 * Fonts are the same asset files the app bundles, so text metrics match the device closely.
 */
class Java2DContinuumPainter(private val g: Graphics2D, private val assetRoot: File) : ContinuumPainter {
    private val stack = ArrayList<Pair<AffineTransform, Shape?>>()

    /** A drawn text and its bounds in output pixels (for masking live values in the design comparison). */
    class TextBox(val text: String, val left: Double, val top: Double, val right: Double, val bottom: Double)

    /** When set, every drawn text is recorded with its pixel bounds. */
    var textBoxes: MutableList<TextBox>? = null

    init {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY)
    }

    override fun save() { stack += g.transform to g.clip }
    override fun restore() {
        val (transform, clip) = stack.removeAt(stack.size - 1)
        g.transform = transform
        g.clip = clip
    }
    override fun translate(dx: Float, dy: Float) = g.translate(dx.toDouble(), dy.toDouble())
    override fun scale(sx: Float, sy: Float, px: Float, py: Float) {
        g.translate(px.toDouble(), py.toDouble()); g.scale(sx.toDouble(), sy.toDouble()); g.translate(-px.toDouble(), -py.toDouble())
    }
    override fun rotate(degrees: Float, px: Float, py: Float) =
        g.rotate(Math.toRadians(degrees.toDouble()), px.toDouble(), py.toDouble())
    override fun clipRect(left: Float, top: Float, right: Float, bottom: Float) =
        g.clip(Rectangle2D.Float(left, top, right - left, bottom - top))
    override fun clipRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) =
        g.clip(RoundRectangle2D.Float(left, top, right - left, bottom - top, radius * 2, radius * 2))

    override fun fillRect(left: Float, top: Float, right: Float, bottom: Float, fill: Fill, alpha: Float) =
        fillShape(Rectangle2D.Float(left, top, right - left, bottom - top), fill, alpha)
    override fun fillRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, fill: Fill, alpha: Float) =
        fillShape(RoundRectangle2D.Float(left, top, right - left, bottom - top, radius * 2, radius * 2), fill, alpha)
    override fun strokeRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, width: Float,
                                 fill: Fill, alpha: Float) =
        strokeShape(RoundRectangle2D.Float(left, top, right - left, bottom - top, radius * 2, radius * 2), width, fill, alpha, true)
    override fun fillOval(left: Float, top: Float, right: Float, bottom: Float, fill: Fill, alpha: Float) =
        fillShape(Ellipse2D.Float(left, top, right - left, bottom - top), fill, alpha)
    override fun strokeOval(left: Float, top: Float, right: Float, bottom: Float, width: Float, fill: Fill, alpha: Float) =
        strokeShape(Ellipse2D.Float(left, top, right - left, bottom - top), width, fill, alpha, true)
    override fun strokeArc(left: Float, top: Float, right: Float, bottom: Float, startDegrees: Float, sweepDegrees: Float,
                           width: Float, fill: Fill, alpha: Float, roundCap: Boolean) =
        // Java2D measures angles counter-clockwise on screen; Android clockwise.
        strokeShape(Arc2D.Float(left, top, right - left, bottom - top, -startDegrees, -sweepDegrees, Arc2D.OPEN),
            width, fill, alpha, roundCap)
    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) =
        strokeShape(Line2D.Float(x0, y0, x1, y1), width, fill, alpha, roundCap)
    override fun fillPath(path: VectorPath, fill: Fill, alpha: Float) = fillShape(toShape(path), fill, alpha)
    override fun strokePath(path: VectorPath, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) =
        strokeShape(toShape(path), width, fill, alpha, roundCap)

    override fun drawText(text: String, x: Float, y: Float, style: TextStyle, fill: Fill, align: TextAlign, alpha: Float) {
        if (text.isEmpty() || alpha <= 0f) return
        val font = font(style)
        val width = measureText(text, style)
        val left = when (align) { TextAlign.LEFT -> x; TextAlign.CENTER -> x - width / 2f; TextAlign.RIGHT -> x - width }
        withPaint(fill, alpha) {
            g.font = font
            g.drawString(text, left, y)
        }
        textBoxes?.let { boxes ->
            val metrics = font.getLineMetrics(text, frc())
            val bounds = g.transform.createTransformedShape(Rectangle2D.Float(left, y - metrics.ascent, width,
                metrics.ascent + metrics.descent)).bounds2D
            boxes += TextBox(text, bounds.minX, bounds.minY, bounds.maxX, bounds.maxY)
        }
    }

    override fun measureText(text: String, style: TextStyle): Float =
        font(style).getStringBounds(text, frc()).width.toFloat()

    override fun capHeight(style: TextStyle): Float {
        val outline = font(style).createGlyphVector(frc(), "H").outline.bounds2D
        return (-outline.minY).toFloat()
    }

    private fun frc() = FontRenderContext(AffineTransform(), true, true)

    private fun font(style: TextStyle): Font {
        val key = style.copy()
        return fonts.getOrPut(key) {
            val base = baseFonts.getOrPut(ContinuumFonts.assetPath(style.family, style.weight)) {
                Font.createFont(Font.TRUETYPE_FONT, File(assetRoot, ContinuumFonts.assetPath(style.family, style.weight)))
            }
            val attributes = HashMap<TextAttribute, Any>()
            attributes[TextAttribute.SIZE] = style.size
            attributes[TextAttribute.KERNING] = TextAttribute.KERNING_ON
            if (style.letterSpacing != 0f) attributes[TextAttribute.TRACKING] = style.letterSpacing / style.size
            base.deriveFont(attributes)
        }
    }

    private fun toShape(path: VectorPath): Shape = Path2D.Float().apply {
        for (op in path.ops) when (op) {
            is VectorPath.Op.Move -> moveTo(op.x, op.y)
            is VectorPath.Op.Line -> lineTo(op.x, op.y)
            is VectorPath.Op.Quad -> quadTo(op.x1, op.y1, op.x, op.y)
            is VectorPath.Op.Cubic -> curveTo(op.x1, op.y1, op.x2, op.y2, op.x, op.y)
            VectorPath.Op.Close -> closePath()
        }
    }

    private fun fillShape(shape: Shape, fill: Fill, alpha: Float) = withPaint(fill, alpha) { g.fill(shape) }

    private fun strokeShape(shape: Shape, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) = withPaint(fill, alpha) {
        g.stroke = BasicStroke(width, if (roundCap) BasicStroke.CAP_ROUND else BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND)
        g.draw(shape)
    }

    private var additive = false
    override fun setAdditive(on: Boolean) { additive = on }

    private inline fun withPaint(fill: Fill, alpha: Float, draw: () -> Unit) {
        if (alpha <= 0f) return
        val oldComposite = g.composite
        g.paint = paint(fill)
        g.composite = if (additive) AdditiveComposite(alpha.coerceIn(0f, 1f))
        else AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha.coerceIn(0f, 1f))
        draw()
        g.composite = oldComposite
    }

    /**
     * Porter-Duff ADD like android.graphics.PorterDuff.Mode.ADD: destination + source colour x source alpha x [alpha],
     * saturated. Anti-aliasing stays correct: Java2D blends the composed tile into the destination by coverage.
     */
    private class AdditiveComposite(private val alpha: Float) : java.awt.Composite {
        override fun createContext(srcColorModel: ColorModel, dstColorModel: ColorModel, hints: RenderingHints?) =
            object : java.awt.CompositeContext {
                override fun dispose() {}
                override fun compose(src: Raster, dstIn: Raster, dstOut: java.awt.image.WritableRaster) {
                    val w = minOf(src.width, dstIn.width, dstOut.width); val h = minOf(src.height, dstIn.height, dstOut.height)
                    var s: Any? = null; var d: Any? = null
                    for (y in 0 until h) for (x in 0 until w) {
                        s = src.getDataElements(src.minX + x, src.minY + y, s)
                        d = dstIn.getDataElements(dstIn.minX + x, dstIn.minY + y, d)
                        val sc = srcColorModel.getRGB(s); val dc = dstColorModel.getRGB(d)
                        val a = (sc ushr 24) / 255f * alpha
                        fun add(shift: Int) = minOf(255, ((dc shr shift) and 255) + (((sc shr shift) and 255) * a + .5f).toInt())
                        val out = (minOf(255, (dc ushr 24) + (a * 255f + .5f).toInt()) shl 24) or (add(16) shl 16) or (add(8) shl 8) or add(0)
                        d = dstColorModel.getDataElements(out, d)
                        dstOut.setDataElements(dstOut.minX + x, dstOut.minY + y, d)
                    }
                }
            }
    }

    private fun paint(fill: Fill): Paint = when (fill) {
        is Fill.Solid -> Color(fill.argb, true)
        is Fill.Linear -> {
            val (fractions, colors) = stops(fill.colors, fill.stops)
            if (fill.x0 == fill.x1 && fill.y0 == fill.y1) colors.last()
            else java.awt.LinearGradientPaint(Point2D.Float(fill.x0, fill.y0), Point2D.Float(fill.x1, fill.y1), fractions, colors,
                MultipleGradientPaint.CycleMethod.NO_CYCLE)
        }
        is Fill.Radial -> {
            val (fractions, colors) = stops(fill.colors, fill.stops)
            java.awt.RadialGradientPaint(Point2D.Float(fill.cx, fill.cy), fill.radius, fractions, colors,
                MultipleGradientPaint.CycleMethod.NO_CYCLE)
        }
        is Fill.Sweep -> SweepPaint(fill)
    }

    /** Java2D needs strictly increasing fractions; Android accepts equal neighbours. */
    private fun stops(colors: IntArray, stops: FloatArray?): Pair<FloatArray, Array<Color>> {
        val fractions = FloatArray(colors.size) { i -> stops?.get(i) ?: (i.toFloat() / (colors.size - 1)) }
        for (i in 1 until fractions.size) if (fractions[i] <= fractions[i - 1]) fractions[i] = minOf(1f, fractions[i - 1] + 1e-4f)
        for (i in fractions.indices.reversed()) if (i > 0 && fractions[i] <= fractions[i - 1]) fractions[i - 1] = fractions[i] - 1e-4f
        return fractions to Array(colors.size) { Color(colors[it], true) }
    }

    /** Sweep gradient as in android.graphics.SweepGradient (clockwise from 3 o'clock in screen space). */
    private class SweepPaint(private val fill: Fill.Sweep) : Paint {
        override fun getTransparency() = java.awt.Transparency.TRANSLUCENT
        override fun createContext(cm: ColorModel?, deviceBounds: Rectangle?, userBounds: Rectangle2D?, xform: AffineTransform,
                                   hints: RenderingHints?): PaintContext {
            val inverse = xform.createInverse()
            val model = DirectColorModel(32, 0x00ff0000, 0x0000ff00, 0x000000ff, -0x1000000)
            val positions = FloatArray(fill.colors.size) { fill.stops?.get(it) ?: (it.toFloat() / (fill.colors.size - 1)) }
            return object : PaintContext {
                override fun dispose() {}
                override fun getColorModel() = model
                override fun getRaster(x: Int, y: Int, w: Int, h: Int): Raster {
                    val raster = model.createCompatibleWritableRaster(w, h)
                    val pixels = IntArray(w * h)
                    val point = Point2D.Float()
                    for (j in 0 until h) for (i in 0 until w) {
                        point.setLocation(x + i + .5f, y + j + .5f)
                        inverse.transform(point, point)
                        var angle = atan2(point.y - fill.cy, point.x - fill.cx).toDouble()
                        if (angle < 0) angle += 2 * PI
                        val t = (angle / (2 * PI)).toFloat()
                        var k = 0
                        while (k < positions.size - 2 && t > positions[k + 1]) k++
                        val span = (positions[k + 1] - positions[k]).coerceAtLeast(1e-6f)
                        pixels[j * w + i] = Argb.mix(fill.colors[k], fill.colors[k + 1], (t - positions[k]) / span)
                    }
                    raster.setDataElements(0, 0, w, h, pixels)
                    return raster
                }
            }
        }
    }

    private companion object {
        val baseFonts = HashMap<String, Font>()
        val fonts = HashMap<TextStyle, Font>()
    }
}

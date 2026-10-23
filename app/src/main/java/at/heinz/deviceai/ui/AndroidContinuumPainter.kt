package at.heinz.deviceai.ui

import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import java.util.IdentityHashMap

/**
 * [ContinuumPainter] on android.graphics.Canvas. Shaders are created once per [Fill] instance and reused; fonts are
 * loaded once from the bundled assets. Call [begin] with the frame's canvas before drawing.
 */
class AndroidContinuumPainter(private val assets: AssetManager) : ContinuumPainter {
    private lateinit var canvas: Canvas
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val rect = RectF()
    private val shaders = IdentityHashMap<Fill, Shader>()
    private val paths = IdentityHashMap<VectorPath, Pair<Int, Path>>()

    private val additiveMode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)

    fun begin(target: Canvas) {
        canvas = target
        paint.xfermode = null
        // gradient fills are allocated per layout; drop cached shaders of fills that are no longer used
        if (shaders.size > 512) shaders.clear()
        if (paths.size > 128) paths.clear()
    }

    override fun setAdditive(on: Boolean) { paint.xfermode = if (on) additiveMode else null }

    override fun save() { canvas.save() }
    override fun restore() { canvas.restore() }
    override fun translate(dx: Float, dy: Float) = canvas.translate(dx, dy)
    override fun scale(sx: Float, sy: Float, px: Float, py: Float) = canvas.scale(sx, sy, px, py)
    override fun rotate(degrees: Float, px: Float, py: Float) = canvas.rotate(degrees, px, py)
    override fun clipRect(left: Float, top: Float, right: Float, bottom: Float) { canvas.clipRect(left, top, right, bottom) }
    override fun clipRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
        val clip = Path(); clip.addRoundRect(RectF(left, top, right, bottom), radius, radius, Path.Direction.CW)
        canvas.clipPath(clip)
    }

    override fun fillRect(left: Float, top: Float, right: Float, bottom: Float, fill: Fill, alpha: Float) {
        if (prepare(fill, alpha, Paint.Style.FILL, 0f, false)) canvas.drawRect(left, top, right, bottom, paint)
    }
    override fun fillRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, fill: Fill, alpha: Float) {
        if (prepare(fill, alpha, Paint.Style.FILL, 0f, false)) canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
    }
    override fun strokeRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, width: Float, fill: Fill, alpha: Float) {
        if (prepare(fill, alpha, Paint.Style.STROKE, width, true)) canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
    }
    override fun fillOval(left: Float, top: Float, right: Float, bottom: Float, fill: Fill, alpha: Float) {
        if (prepare(fill, alpha, Paint.Style.FILL, 0f, false)) canvas.drawOval(left, top, right, bottom, paint)
    }
    override fun strokeOval(left: Float, top: Float, right: Float, bottom: Float, width: Float, fill: Fill, alpha: Float) {
        if (prepare(fill, alpha, Paint.Style.STROKE, width, true)) canvas.drawOval(left, top, right, bottom, paint)
    }
    override fun strokeArc(left: Float, top: Float, right: Float, bottom: Float, startDegrees: Float, sweepDegrees: Float,
                           width: Float, fill: Fill, alpha: Float, roundCap: Boolean) {
        if (!prepare(fill, alpha, Paint.Style.STROKE, width, roundCap)) return
        rect.set(left, top, right, bottom)
        canvas.drawArc(rect, startDegrees, sweepDegrees, false, paint)
    }
    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) {
        if (prepare(fill, alpha, Paint.Style.STROKE, width, roundCap)) canvas.drawLine(x0, y0, x1, y1, paint)
    }
    override fun fillPath(path: VectorPath, fill: Fill, alpha: Float) {
        if (prepare(fill, alpha, Paint.Style.FILL, 0f, false)) canvas.drawPath(android(path), paint)
    }
    override fun strokePath(path: VectorPath, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) {
        if (prepare(fill, alpha, Paint.Style.STROKE, width, roundCap)) canvas.drawPath(android(path), paint)
    }

    override fun drawText(text: String, x: Float, y: Float, style: TextStyle, fill: Fill, align: TextAlign, alpha: Float) {
        if (text.isEmpty() || !prepare(fill, alpha, Paint.Style.FILL, 0f, false)) return
        text(style)
        paint.textAlign = when (align) { TextAlign.LEFT -> Paint.Align.LEFT; TextAlign.CENTER -> Paint.Align.CENTER; TextAlign.RIGHT -> Paint.Align.RIGHT }
        canvas.drawText(text, x, y, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    override fun measureText(text: String, style: TextStyle): Float { text(style); return paint.measureText(text) }

    override fun capHeight(style: TextStyle): Float {
        text(style)
        val bounds = android.graphics.Rect()
        paint.getTextBounds("H", 0, 1, bounds)
        return (-bounds.top).toFloat()
    }

    private fun text(style: TextStyle) {
        paint.typeface = typeface(style)
        paint.textSize = style.size
        paint.letterSpacing = if (style.size > 0f) style.letterSpacing / style.size else 0f
    }

    private fun typeface(style: TextStyle): Typeface {
        val path = ContinuumFonts.assetPath(style.family, style.weight)
        return faces.getOrPut(path) { runCatching { Typeface.createFromAsset(assets, path) }.getOrDefault(Typeface.DEFAULT) }
    }

    private fun prepare(fill: Fill, alpha: Float, style: Paint.Style, width: Float, roundCap: Boolean): Boolean {
        if (alpha <= 0f) return false
        paint.style = style
        paint.strokeWidth = width
        paint.strokeCap = if (roundCap) Paint.Cap.ROUND else Paint.Cap.BUTT
        paint.strokeJoin = Paint.Join.ROUND
        when (fill) {
            is Fill.Solid -> { paint.shader = null; paint.color = fill.argb; paint.alpha = (Argb.alpha(fill.argb) * alpha.coerceIn(0f, 1f)).toInt() }
            else -> { paint.shader = shader(fill); paint.color = -1; paint.alpha = (255 * alpha.coerceIn(0f, 1f)).toInt() }
        }
        return true
    }

    private fun shader(fill: Fill): Shader = shaders.getOrPut(fill) {
        when (fill) {
            is Fill.Linear -> LinearGradient(fill.x0, fill.y0, fill.x1, fill.y1, fill.colors, fill.stops, Shader.TileMode.CLAMP)
            is Fill.Radial -> RadialGradient(fill.cx, fill.cy, fill.radius, fill.colors, fill.stops, Shader.TileMode.CLAMP)
            is Fill.Sweep -> SweepGradient(fill.cx, fill.cy, fill.colors, fill.stops)
            is Fill.Solid -> error("solid fills need no shader")
        }
    }

    private fun android(path: VectorPath): Path {
        val cached = paths[path]
        if (cached != null && cached.first == path.version) return cached.second
        val out = cached?.second?.apply { rewind() } ?: Path()
        for (op in path.ops) when (op) {
            is VectorPath.Op.Move -> out.moveTo(op.x, op.y)
            is VectorPath.Op.Line -> out.lineTo(op.x, op.y)
            is VectorPath.Op.Quad -> out.quadTo(op.x1, op.y1, op.x, op.y)
            is VectorPath.Op.Cubic -> out.cubicTo(op.x1, op.y1, op.x2, op.y2, op.x, op.y)
            VectorPath.Op.Close -> out.close()
        }
        paths[path] = path.version to out
        return out
    }

    private companion object { val faces = HashMap<String, Typeface>() }
}

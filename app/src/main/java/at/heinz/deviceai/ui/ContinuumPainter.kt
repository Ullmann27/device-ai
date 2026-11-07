package at.heinz.deviceai.ui

/**
 * Platform-free 2D drawing surface for the Continuum visuals.
 *
 * Every Continuum screen element (hologram, cards, charts, icons, navigation) draws only through this interface.
 * On the device [AndroidContinuumPainter] forwards to android.graphics.Canvas; the JVM preview and the unit tests use
 * a Java2D implementation. The same source therefore produces the device rendering and the reviewable preview images.
 *
 * Coordinates are in the caller's current transform; colours are ARGB integers; [alpha] multiplies the paint.
 */
interface ContinuumPainter {
    /** Packaged decorative RGBA asset only. False allows vector fallback in unsupported test painters. */
    fun drawRaster(assetPath: String, left: Float, top: Float, right: Float, bottom: Float, alpha: Float = 1f): Boolean = false
    /** Batched independent segments (x0,y0,x1,y1). Android submits one native draw call, without frame allocations. */
    fun strokeSegments(points: FloatArray, count: Int, width: Float, fill: Fill, alpha: Float) {
        for (i in 0 until count step 4) drawLine(points[i], points[i + 1], points[i + 2], points[i + 3], width, fill, alpha, roundCap = false)
    }
    fun save()
    /** save() with a group opacity; balanced by restore(). Portable fallback: plain save (no group fade). */
    fun saveAlpha(alpha: Float) = save()
    fun restore()
    fun translate(dx: Float, dy: Float)
    fun scale(sx: Float, sy: Float, px: Float = 0f, py: Float = 0f)
    fun rotate(degrees: Float, px: Float = 0f, py: Float = 0f)
    fun clipRect(left: Float, top: Float, right: Float, bottom: Float)
    fun clipRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float)

    fun fillRect(left: Float, top: Float, right: Float, bottom: Float, fill: Fill, alpha: Float = 1f)
    fun fillRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, fill: Fill, alpha: Float = 1f)
    fun strokeRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, width: Float, fill: Fill,
                        alpha: Float = 1f)
    fun fillOval(left: Float, top: Float, right: Float, bottom: Float, fill: Fill, alpha: Float = 1f)
    fun strokeOval(left: Float, top: Float, right: Float, bottom: Float, width: Float, fill: Fill, alpha: Float = 1f)
    /** Angles in degrees, 0° = 3 o'clock, positive = clockwise (Android convention). */
    fun strokeArc(left: Float, top: Float, right: Float, bottom: Float, startDegrees: Float, sweepDegrees: Float,
                  width: Float, fill: Fill, alpha: Float = 1f, roundCap: Boolean = true)
    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, fill: Fill, alpha: Float = 1f,
                 roundCap: Boolean = true)
    fun fillPath(path: VectorPath, fill: Fill, alpha: Float = 1f)
    fun strokePath(path: VectorPath, width: Float, fill: Fill, alpha: Float = 1f, roundCap: Boolean = true)

    /** Draws a single line of text with [y] as baseline. */
    fun drawText(text: String, x: Float, y: Float, style: TextStyle, fill: Fill, align: TextAlign = TextAlign.LEFT,
                 alpha: Float = 1f)
    fun measureText(text: String, style: TextStyle): Float
    /** Distance from baseline to the top of capital letters (positive), used for vertical centring. */
    fun capHeight(style: TextStyle): Float

    /**
     * Light layers: while on, fills and strokes add their light to what is below (Porter-Duff ADD) instead of
     * covering it, so crossing lines and dense regions glow brighter while dark areas stay dark. Off by default;
     * every caller that turns it on turns it off again.
     */
    fun setAdditive(on: Boolean) {}

    fun fillCircle(cx: Float, cy: Float, radius: Float, fill: Fill, alpha: Float = 1f) =
        fillOval(cx - radius, cy - radius, cx + radius, cy + radius, fill, alpha)
    fun strokeCircle(cx: Float, cy: Float, radius: Float, width: Float, fill: Fill, alpha: Float = 1f) =
        strokeOval(cx - radius, cy - radius, cx + radius, cy + radius, width, fill, alpha)
}

/** Paint source. Gradient instances are meant to be created once per layout and reused across frames. */
sealed class Fill {
    data class Solid(val argb: Int) : Fill()
    class Linear(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val colors: IntArray,
                 val stops: FloatArray? = null) : Fill() {
        init { require(colors.size >= 2 && (stops == null || stops.size == colors.size)) }
    }
    class Radial(val cx: Float, val cy: Float, val radius: Float, val colors: IntArray,
                 val stops: FloatArray? = null) : Fill() {
        init { require(radius > 0f && colors.size >= 2 && (stops == null || stops.size == colors.size)) }
    }
    /** Sweep gradient starting at 3 o'clock, clockwise, like android.graphics.SweepGradient. */
    class Sweep(val cx: Float, val cy: Float, val colors: IntArray, val stops: FloatArray? = null) : Fill() {
        init { require(colors.size >= 2 && (stops == null || stops.size == colors.size)) }
    }
}

enum class TextAlign { LEFT, CENTER, RIGHT }

enum class FontFamily { DISPLAY, TEXT }

/** [size] and [letterSpacing] are in the current drawing units; weight follows CSS (300 light … 700 bold). */
data class TextStyle(val family: FontFamily, val weight: Int, val size: Float, val letterSpacing: Float = 0f) {
    init { require(weight in 100..900 && size > 0f) }
}

/** Mutable, platform-free path. [version] lets platform painters cache their converted copy. */
class VectorPath {
    sealed class Op {
        data class Move(val x: Float, val y: Float) : Op()
        data class Line(val x: Float, val y: Float) : Op()
        data class Quad(val x1: Float, val y1: Float, val x: Float, val y: Float) : Op()
        data class Cubic(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float) : Op()
        object Close : Op()
    }

    private val list = ArrayList<Op>()
    val ops: List<Op> get() = list
    var version: Int = 0
        private set

    fun reset(): VectorPath { list.clear(); version++; return this }
    fun moveTo(x: Float, y: Float): VectorPath { list += Op.Move(x, y); version++; return this }
    fun lineTo(x: Float, y: Float): VectorPath { list += Op.Line(x, y); version++; return this }
    fun quadTo(x1: Float, y1: Float, x: Float, y: Float): VectorPath { list += Op.Quad(x1, y1, x, y); version++; return this }
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float): VectorPath {
        list += Op.Cubic(x1, y1, x2, y2, x, y); version++; return this
    }
    fun close(): VectorPath { list += Op.Close; version++; return this }
    val isEmpty: Boolean get() = list.isEmpty()
}

/** Colour helpers on ARGB integers (no Android dependency). */
object Argb {
    fun of(a: Int, r: Int, g: Int, b: Int): Int =
        (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    fun rgb(r: Int, g: Int, b: Int): Int = of(255, r, g, b)
    fun alpha(color: Int): Int = color ushr 24
    fun red(color: Int): Int = (color shr 16) and 255
    fun green(color: Int): Int = (color shr 8) and 255
    fun blue(color: Int): Int = color and 255
    /** Same colour with alpha replaced by [alpha] in 0..1. */
    fun withAlpha(color: Int, alpha: Float): Int = (((alpha.coerceIn(0f, 1f) * 255f) + .5f).toInt() shl 24) or (color and 0xFFFFFF)
    /** Linear interpolation in sRGB, t in 0..1. */
    fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(x: Int, y: Int) = (x + (y - x) * k + .5f).toInt()
        return of(ch(alpha(a), alpha(b)), ch(red(a), red(b)), ch(green(a), green(b)), ch(blue(a), blue(b)))
    }
}

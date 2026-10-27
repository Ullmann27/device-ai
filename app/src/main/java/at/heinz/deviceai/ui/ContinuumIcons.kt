package at.heinz.deviceai.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Line icons of the reference design, drawn as vectors (no bitmap assets). Every icon is laid out on a 24-unit grid
 * centred on (cx, cy) and scaled to [size]; [stroke] is the line width in final units.
 */
object ContinuumIcons {
    enum class Icon {
        GLOBE, BRAIN, BOOK, CODE, CHART_UP, ATOM, SEARCH, BELL, HOME, CHAT, DOTS_VERTICAL, DOTS_HORIZONTAL, DATABASE, GEAR,
        PULSE, CHEVRON_RIGHT, CHEVRON_DOWN, ARROW_UP, CPU, WIFI, DOCUMENT, BOLT, CLOCK, MODE, REFRESH, CHECK_CIRCLE, CUBE, USERS
    }

    private val path = VectorPath()

    fun draw(p: ContinuumPainter, icon: Icon, cx: Float, cy: Float, size: Float, fill: Fill, stroke: Float = size / 13f,
             alpha: Float = 1f) {
        val k = size / 24f
        fun x(v: Float) = cx + (v - 12f) * k
        fun y(v: Float) = cy + (v - 12f) * k
        fun line(x0: Float, y0: Float, x1: Float, y1: Float) = p.drawLine(x(x0), y(y0), x(x1), y(y1), stroke, fill, alpha)
        fun circle(ccx: Float, ccy: Float, r: Float) = p.strokeCircle(x(ccx), y(ccy), r * k, stroke, fill, alpha)
        fun dot(ccx: Float, ccy: Float, r: Float) = p.fillCircle(x(ccx), y(ccy), r * k, fill, alpha)
        fun oval(l: Float, t: Float, r: Float, b: Float) = p.strokeOval(x(l), y(t), x(r), y(b), stroke, fill, alpha)
        fun arc(l: Float, t: Float, r: Float, b: Float, start: Float, sweep: Float) =
            p.strokeArc(x(l), y(t), x(r), y(b), start, sweep, stroke, fill, alpha)
        fun poly(vararg pts: Float, closed: Boolean = false) {
            path.reset(); path.moveTo(x(pts[0]), y(pts[1]))
            var i = 2
            while (i < pts.size) { path.lineTo(x(pts[i]), y(pts[i + 1])); i += 2 }
            if (closed) path.close()
            p.strokePath(path, stroke, fill, alpha)
        }
        when (icon) {
            Icon.GLOBE -> {
                circle(12f, 12f, 9f)
                oval(8f, 3f, 16f, 21f)
                line(3.2f, 9f, 20.8f, 9f); line(3.2f, 15f, 20.8f, 15f); line(12f, 3f, 12f, 21f)
            }
            Icon.BRAIN -> {
                // two hemispheres with folds
                path.reset()
                path.moveTo(x(11.2f), y(4.2f)).cubicTo(x(8.6f), y(2.6f), x(5.6f), y(4f), x(5.6f), y(6.8f))
                    .cubicTo(x(3.2f), y(7.6f), x(2.8f), y(11f), x(4.6f), y(12.4f))
                    .cubicTo(x(3f), y(14.4f), x(4.2f), y(17.6f), x(6.8f), y(17.8f))
                    .cubicTo(x(7.4f), y(20.4f), x(10.2f), y(21f), x(11.2f), y(19.4f)).close()
                p.strokePath(path, stroke, fill, alpha)
                path.reset()
                path.moveTo(x(12.8f), y(4.2f)).cubicTo(x(15.4f), y(2.6f), x(18.4f), y(4f), x(18.4f), y(6.8f))
                    .cubicTo(x(20.8f), y(7.6f), x(21.2f), y(11f), x(19.4f), y(12.4f))
                    .cubicTo(x(21f), y(14.4f), x(19.8f), y(17.6f), x(17.2f), y(17.8f))
                    .cubicTo(x(16.6f), y(20.4f), x(13.8f), y(21f), x(12.8f), y(19.4f)).close()
                p.strokePath(path, stroke, fill, alpha)
                line(8f, 8.4f, 9.6f, 9.4f); line(7.2f, 12.6f, 9.4f, 12f); line(8.4f, 15.6f, 9.8f, 14.8f)
                line(16f, 8.4f, 14.4f, 9.4f); line(16.8f, 12.6f, 14.6f, 12f); line(15.6f, 15.6f, 14.2f, 14.8f)
            }
            Icon.BOOK -> {
                poly(12f, 6.5f, 12f, 20f)
                path.reset()
                path.moveTo(x(12f), y(6.5f)).cubicTo(x(9.6f), y(4.8f), x(6f), y(4.6f), x(2.8f), y(5.4f)).lineTo(x(2.8f), y(18.6f))
                    .cubicTo(x(6f), y(17.8f), x(9.6f), y(18f), x(12f), y(20f))
                    .cubicTo(x(14.4f), y(18f), x(18f), y(17.8f), x(21.2f), y(18.6f)).lineTo(x(21.2f), y(5.4f))
                    .cubicTo(x(18f), y(4.6f), x(14.4f), y(4.8f), x(12f), y(6.5f))
                p.strokePath(path, stroke, fill, alpha)
                line(5.2f, 9f, 9.6f, 9.6f); line(5.2f, 12.4f, 9.6f, 13f); line(14.4f, 9.6f, 18.8f, 9f); line(14.4f, 13f, 18.8f, 12.4f)
            }
            Icon.CODE -> { poly(8.5f, 6.5f, 3f, 12f, 8.5f, 17.5f); poly(15.5f, 6.5f, 21f, 12f, 15.5f, 17.5f); line(13.6f, 4.5f, 10.4f, 19.5f) }
            Icon.CHART_UP -> {
                p.fillRect(x(3.5f), y(14f), x(6.3f), y(20.5f), fill, alpha)
                p.fillRect(x(8.4f), y(11.2f), x(11.2f), y(20.5f), fill, alpha)
                p.fillRect(x(13.3f), y(12.8f), x(16.1f), y(20.5f), fill, alpha)
                p.fillRect(x(18.2f), y(8.6f), x(21f), y(20.5f), fill, alpha)
                poly(3.5f, 10.5f, 9.5f, 6.2f, 14f, 8.4f, 20.5f, 3.2f)
                poly(16.6f, 3.2f, 20.5f, 3.2f, 20.5f, 7f)
            }
            Icon.ATOM -> {
                dot(12f, 12f, 1.9f)
                p.save(); p.rotate(0f, x(12f), y(12f)); oval(2.5f, 8.2f, 21.5f, 15.8f); p.restore()
                p.save(); p.rotate(60f, x(12f), y(12f)); oval(2.5f, 8.2f, 21.5f, 15.8f); p.restore()
                p.save(); p.rotate(-60f, x(12f), y(12f)); oval(2.5f, 8.2f, 21.5f, 15.8f); p.restore()
            }
            Icon.SEARCH -> { circle(10.6f, 10.6f, 6.4f); line(15.4f, 15.4f, 20.5f, 20.5f) }
            Icon.BELL -> {
                path.reset()
                path.moveTo(x(5.2f), y(17f)).lineTo(x(18.8f), y(17f)).lineTo(x(17.4f), y(15f)).lineTo(x(17.4f), y(10.4f))
                    .cubicTo(x(17.4f), y(7f), x(15f), y(4.6f), x(12f), y(4.6f)).cubicTo(x(9f), y(4.6f), x(6.6f), y(7f), x(6.6f), y(10.4f))
                    .lineTo(x(6.6f), y(15f)).close()
                p.strokePath(path, stroke, fill, alpha)
                arc(9.8f, 17.6f, 14.2f, 21.6f, 0f, 180f)
                line(12f, 2.8f, 12f, 4.4f)
            }
            Icon.HOME -> {
                poly(3f, 11.2f, 12f, 3.6f, 21f, 11.2f)
                poly(5.4f, 9.6f, 5.4f, 20.4f, 18.6f, 20.4f, 18.6f, 9.6f)
                poly(10f, 20.4f, 10f, 14.4f, 14f, 14.4f, 14f, 20.4f)
            }
            Icon.CHAT -> {
                path.reset()
                path.moveTo(x(12f), y(4f)).cubicTo(x(17f), y(4f), x(21f), y(7.2f), x(21f), y(11.2f))
                    .cubicTo(x(21f), y(15.2f), x(17f), y(18.4f), x(12f), y(18.4f)).cubicTo(x(10.8f), y(18.4f), x(9.6f), y(18.2f), x(8.6f), y(17.8f))
                    .lineTo(x(4.2f), y(20.2f)).lineTo(x(5.2f), y(15.8f)).cubicTo(x(3.8f), y(14.6f), x(3f), y(13f), x(3f), y(11.2f))
                    .cubicTo(x(3f), y(7.2f), x(7f), y(4f), x(12f), y(4f)).close()
                p.strokePath(path, stroke, fill, alpha)
                dot(8.2f, 11.4f, 1.1f); dot(12f, 11.4f, 1.1f); dot(15.8f, 11.4f, 1.1f)
            }
            Icon.DOTS_VERTICAL -> { dot(12f, 5f, 1.8f); dot(12f, 12f, 1.8f); dot(12f, 19f, 1.8f) }
            Icon.DOTS_HORIZONTAL -> { dot(5f, 12f, 1.8f); dot(12f, 12f, 1.8f); dot(19f, 12f, 1.8f) }
            Icon.DATABASE -> {
                oval(4.5f, 3.5f, 19.5f, 8.5f)
                arc(4.5f, 9f, 19.5f, 14f, 0f, 180f)
                arc(4.5f, 14.5f, 19.5f, 19.5f, 0f, 180f)
                line(4.5f, 6f, 4.5f, 17f); line(19.5f, 6f, 19.5f, 17f)
            }
            Icon.GEAR -> {
                circle(12f, 12f, 3.2f)
                path.reset()
                for (i in 0 until 16) {
                    val a = i * (PI.toFloat() / 8f)
                    val rr = if (i % 2 == 0) 9f else 6.8f
                    val px = 12f + cos(a) * rr; val py = 12f + sin(a) * rr
                    if (i == 0) path.moveTo(x(px), y(py)) else path.lineTo(x(px), y(py))
                }
                path.close()
                p.strokePath(path, stroke, fill, alpha)
            }
            Icon.PULSE -> poly(2.5f, 12.5f, 7f, 12.5f, 9.2f, 6.5f, 13f, 18f, 15.4f, 10.5f, 16.8f, 12.5f, 21.5f, 12.5f)
            Icon.CHEVRON_RIGHT -> poly(9f, 5f, 16f, 12f, 9f, 19f)
            Icon.CHEVRON_DOWN -> poly(5.5f, 9f, 12f, 15.5f, 18.5f, 9f)
            Icon.ARROW_UP -> { line(12f, 20f, 12f, 4.5f); poly(6f, 10f, 12f, 4f, 18f, 10f) }
            Icon.CPU -> {
                p.strokeRoundRect(x(6f), y(6f), x(18f), y(18f), 2f * k, stroke, fill, alpha)
                p.strokeRoundRect(x(9.5f), y(9.5f), x(14.5f), y(14.5f), 1f * k, stroke, fill, alpha)
                for (v in floatArrayOf(9f, 12f, 15f)) { line(v, 3f, v, 6f); line(v, 18f, v, 21f); line(3f, v, 6f, v); line(18f, v, 21f, v) }
            }
            Icon.WIFI -> {
                arc(2f, 4f, 22f, 24f, 225f, 90f); arc(5.5f, 7.5f, 18.5f, 20.5f, 225f, 90f); arc(9f, 11f, 15f, 17f, 225f, 90f)
                dot(12f, 19f, 1.3f)
            }
            Icon.DOCUMENT -> {
                poly(6f, 3f, 14.5f, 3f, 19f, 7.5f, 19f, 21f, 6f, 21f, closed = true)
                poly(14.5f, 3f, 14.5f, 7.5f, 19f, 7.5f)
                line(9f, 12f, 16f, 12f); line(9f, 15.5f, 16f, 15.5f)
            }
            Icon.BOLT -> poly(13.5f, 2.5f, 5.5f, 13.5f, 11.5f, 13.5f, 10.5f, 21.5f, 18.5f, 10.5f, 12.5f, 10.5f, closed = true)
            Icon.CLOCK -> { circle(12f, 12f, 8.6f); poly(12f, 7f, 12f, 12f, 15.4f, 14f) }
            Icon.MODE -> {
                circle(12f, 12f, 8.6f); circle(12f, 12f, 3f)
                line(12f, 1.8f, 12f, 5f); line(12f, 19f, 12f, 22.2f); line(1.8f, 12f, 5f, 12f); line(19f, 12f, 22.2f, 12f)
            }
            Icon.REFRESH -> { arc(4f, 4f, 20f, 20f, 200f, 250f); poly(15.6f, 3.2f, 19.2f, 5.6f, 16.4f, 8.6f) }
            Icon.CHECK_CIRCLE -> { circle(12f, 12f, 9f); poly(7.6f, 12.4f, 10.6f, 15.2f, 16.6f, 8.8f) }
            Icon.CUBE -> {
                poly(12f, 2.8f, 20.5f, 7.4f, 20.5f, 16.6f, 12f, 21.2f, 3.5f, 16.6f, 3.5f, 7.4f, closed = true)
                poly(3.5f, 7.4f, 12f, 12f, 20.5f, 7.4f); line(12f, 12f, 12f, 21.2f)
            }
            Icon.USERS -> {
                circle(12f, 7.8f, 3.1f); circle(4.5f, 9.2f, 2.2f); circle(19.5f, 9.2f, 2.2f)
                arc(5.5f, 11.2f, 18.5f, 23f, 195f, 150f)
                arc(0f, 12.2f, 9f, 22f, 190f, 120f); arc(15f, 12.2f, 24f, 22f, 230f, 120f)
            }
        }
    }

    /** The Continuum infinity logo as a thick gradient ribbon with glow, fitted into a [width] x [height] box. */
    fun logo(p: ContinuumPainter, cx: Float, cy: Float, width: Float, height: Float, alpha: Float = 1f) {
        val a = width * .5f
        val b = height * .5f
        val thick = height * .2f
        path.reset()
        for (i in 0..96) {
            val t = i * (2f * PI.toFloat() / 96f)
            val px = cx + a * sin(t) * .86f
            val py = cy + b * sin(t) * cos(t) * 1.72f
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        val ribbon = Fill.Linear(cx - a, cy, cx + a, cy, ContinuumTheme.brandGradientColors)
        p.strokePath(path, thick * 2.2f, ribbon, .12f * alpha)
        p.strokePath(path, thick * 1.4f, ribbon, .28f * alpha)
        p.strokePath(path, thick, ribbon, alpha)
        p.strokePath(path, thick * .22f, Fill.Solid(Argb.rgb(230, 248, 255)), .55f * alpha)
    }
}
